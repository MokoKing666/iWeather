package com.moko.iweather.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * 天气通知。
 *
 * 这是 README 里唯一还标着 ❌ 的**功能**（不是库），也是这次引入
 * WorkManager 的唯一理由 —— 普通的协程在 App 被杀掉后就没了，
 * 只有 WorkManager 能保证「隔一段时间检查一次」这件事真的发生。
 *
 * ## 会推什么
 * 1. **灾害预警**：彩云 `alert` 里有新预警时推，按标题去重；
 * 2. **降水开始 / 停止**：读彩云 `minutely`（未来两小时的逐分钟强度），
 *    干着但一小时内要下、或者正在下但一小时内要停，都推一条。
 *
 * ## 为什么通知状态单独存一个 SharedPreferences
 * 不去动 `AppSettings` 和 `Prefs`：那两个类是小组件和主界面共用的，
 * 为了三个开关去改它们的序列化结构，风险不划算（尤其小组件是**同步**读的）。
 *
 * ## 配额
 * 一次检查最多查 3 个城市，而彩云限流是 1.2 秒一个请求，
 * 所以一轮最多占 3 次配额。一小时跑一次，一天 72 次 —— 免费额度（1000/天）够用。
 */
object WeatherNotifications {

    private const val PREFS = "iweather_notifications"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_RAIN = "rain"
    private const val KEY_SEVERE = "severe"
    private const val KEY_LAST = "last_signature"

    private const val CHANNEL_RAIN = "weather_rain"
    private const val CHANNEL_SEVERE = "weather_severe"

    const val WORK_NAME = "weather_background_check"

    /** 一轮最多查几个城市（直接决定占用多少彩云配额）。 */
    private const val MAX_CITIES_PER_RUN = 3

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // 用普通函数而不是 Context 的扩展属性：扩展属性定义在 object 内部时，
    // 外部必须 with(WeatherNotifications) 才能用，很容易踩坑。
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** 开关通知。开的时候顺带排期，关的时候取消任务。 */
    fun setEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
        if (value) schedule(context) else cancel(context)
    }

    fun isRainAlertsEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_RAIN, true)

    fun setRainAlertsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_RAIN, value).apply()
    }

    fun isSevereAlertsEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEVERE, true)

    fun setSevereAlertsEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEVERE, value).apply()
    }

    /** 建通知渠道。在 Application.onCreate 里调一次，重复调用无害。 */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SEVERE,
                "灾害预警",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "台风、暴雨、高温等预警信号" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RAIN,
                "降水提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "雨雪即将开始或停止" }
        )
    }

    /** 是否已拿到通知权限（Android 13+ 是运行时权限）。 */
    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    // ------------------------------------------------------------ 调度

    /**
     * 排一个周期任务。
     *
     * 用 `KEEP` 而不是 `UPDATE`：已经排过就不要重建，
     * 否则每次冷启动都会把下一次执行时间往后推，等于永远跑不到。
     */
    fun schedule(context: Context) {
        if (!isEnabled(context)) return
        val request = PeriodicWorkRequestBuilder<WeatherCheckWorker>(1, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
        Timber.i("天气通知已排期（每小时一次）")
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        Timber.i("天气通知已取消")
    }

    // ------------------------------------------------------------ 通知

    private fun post(
        context: Context,
        channel: String,
        id: Int,
        title: String,
        text: String
    ) {
        if (!hasPermission(context)) {
            Timber.w("没有通知权限，跳过推送：%s", title)
            return
        }

        // 点通知直接打开 App
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pending = intent?.let {
            PendingIntent.getActivity(
                context, id, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(com.moko.iweather.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .apply { pending?.let { setContentIntent(it) } }
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(id, notification)
        }.onFailure { Timber.w(it, "推送失败") }
    }

    /**
     * 检查一遍并推送。
     *
     * 去重靠 [KEY_LAST] 里存的"签名"：只有内容真的变了才推，
     * 否则每小时推一条一模一样的"未来一小时有雨"就是骚扰。
     */
    suspend fun checkAndNotify(context: Context) {
        val app = context.applicationContext
        if (!isEnabled(app)) return

        val prefsStore = Prefs(app)
        val auth = prefsStore.auth()
        if (!auth.isConfigured) {
            Timber.i("未配置彩云 Token，跳过通知检查")
            return
        }

        val cities = prefsStore.cities.take(MAX_CITIES_PER_RUN)
        if (cities.isEmpty()) return

        val client = CaiyunClient(auth)
        val signatures = StringBuilder()

        cities.forEach { city ->
            val result = runCatching { client.fetchWeather(city, dailySteps = 1, hourlySteps = 1) }
            val response = result.getOrElse {
                Timber.w(it, "通知检查失败：%s", city.name)
                return@forEach
            }
            val snapshot = SnapshotMapper.map(response, city)

            // ① 灾害预警
            if (isSevereAlertsEnabled(app)) {
                snapshot.alerts.forEach { alert ->
                    val signature = "alert:${city.id}:${alert.title}"
                    signatures.append(signature).append('|')
                    if (signature != lastSignature(app)) {
                        post(
                            context = app,
                            channel = CHANNEL_SEVERE,
                            id = signature.hashCode(),
                            title = "${city.name} · ${alert.level.text}预警",
                            text = alert.title + alert.body.take(120).let {
                                if (it.isBlank()) "" else "\n$it"
                            }
                        )
                    }
                }
            }

            // ② 降水开始 / 停止
            if (isRainAlertsEnabled(app)) {
                rainMessage(snapshot)?.let { message ->
                    val signature = "rain:${city.id}:$message"
                    signatures.append(signature).append('|')
                    if (signature != lastSignature(app)) {
                        post(
                            context = app,
                            channel = CHANNEL_RAIN,
                            id = signature.hashCode(),
                            title = city.name,
                            text = message
                        )
                    }
                }
            }
        }

        // 记录本轮所有签名，下一轮比对。用整串而不是最后一条，
        // 否则多个城市之间会互相覆盖，导致重复推送。
        prefs(app).edit().putString(KEY_LAST, signatures.toString()).apply()
    }

    private fun lastSignature(context: Context): String = prefs(context).getString(KEY_LAST, "") ?: ""

    /**
     * 从分钟级降水推出提醒文案。
     *
     * 彩云 `minutely` 是未来两小时的逐分钟强度（0~1 的相对值）。
     * 这里只看**未来 60 分钟**：
     *  - 现在没下、60 分钟内强度过阈值 → 即将开始
     *  - 现在在下、60 分钟内归零 → 即将停止
     */
    private fun rainMessage(snapshot: WeatherSnapshot): String? {
        val minutely = snapshot.minutely
        if (minutely.size < 10) return null

        val now = System.currentTimeMillis()
        val nextHour = minutely.filter { it.time in now..(now + 60 * 60_000L) }
        if (nextHour.isEmpty()) return null

        val threshold = 0.05
        val rainingNow = minutely.firstOrNull { it.time >= now }?.intensity ?: 0.0
        val currentlyWet = rainingNow > threshold

        if (!currentlyWet) {
            val start = nextHour.firstOrNull { it.intensity > threshold } ?: return null
            val minutes = ((start.time - now) / 60_000L).toInt().coerceAtLeast(1)
            val adjective = if (snapshot.realtime.temperature != null &&
                snapshot.realtime.temperature < 0.0
            ) "雪" else "雨"
            return "预计约 $minutes 分钟后开始下$adjective"
        }

        val stop = nextHour.firstOrNull { it.intensity <= threshold } ?: return null
        val minutes = ((stop.time - now) / 60_000L).toInt().coerceAtLeast(1)
        return "降水预计约 $minutes 分钟后停止"
    }
}

/**
 * 周期检查任务。
 *
 * 失败时返回 [Result.success] 而不是 [Result.retry]：
 * 这是个每小时跑一次的周期任务，这一轮失败下一轮自然会重试；
 * 用 retry 会在断网时疯狂退避重试，白白耗电。
 */
class WeatherCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.i("开始后台天气检查")
        return runCatching {
            WeatherNotifications.checkAndNotify(applicationContext)
            Result.success()
        }.getOrElse {
            Timber.w(it, "后台天气检查异常")
            Result.success()
        }
    }
}
