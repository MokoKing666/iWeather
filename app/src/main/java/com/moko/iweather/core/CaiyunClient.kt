package com.moko.iweather.core

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** 彩云接口异常。 */
sealed class WeatherError(message: String) : Exception(message) {
    object NotConfigured : WeatherError("尚未配置彩云天气 API Token") {
        private fun readResolve(): Any = NotConfigured
    }
    object Unauthorized : WeatherError("彩云 Token 无效或已过期") {
        private fun readResolve(): Any = Unauthorized
    }
    object RateLimited : WeatherError("请求过于频繁，请稍后再试") {
        private fun readResolve(): Any = RateLimited
    }
    data class Network(val causeMessage: String) : WeatherError("网络请求失败：$causeMessage")
    data class Server(val code: Int, val apiMessage: String) : WeatherError("服务异常（$code）：$apiMessage")
    data class Parse(val detail: String) : WeatherError("数据解析失败：$detail")
    object LocationUnavailable : WeatherError("暂时无法获取位置") {
        private fun readResolve(): Any = LocationUnavailable
    }
    data class Geocode(val detail: String) : WeatherError("无法匹配到城市：$detail")
}

/** 鉴权配置：Token 或 App Key/Secret。 */
sealed class CaiyunAuth {
    data class Token(val token: String) : CaiyunAuth()
    data class AppKey(val key: String, val secret: String) : CaiyunAuth()

    val isConfigured: Boolean get() = when (this) {
        is Token -> token.isNotBlank()
        is AppKey -> key.isNotBlank() && secret.isNotBlank()
    }
}

/**
 * 彩云天气 v2.6 客户端。
 *
 * 只用综合接口 `/weather` 一次取回实况 + 空气质量 + 分钟级 + 逐时 + 逐日 + 预警，
 * 单城市只需一次请求，避免触发 QPS 限制。请求之间串行并强制最小间隔。
 */
class CaiyunClient(
    private val auth: CaiyunAuth,
    private val language: String = "zh_CN",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 限流。
     *
     * **实测结论**：彩云免费额度大约每秒只放行 1 个请求，
     * 间隔 300ms 时会出现大量 `HTTP 429 TOO MANY REQUESTS`（实测 25 个点只成功 1 个）；
     * 间隔放宽到 1200ms 后 6/6 全部成功。所以这里取 1200ms。
     *
     * 这个值不只关系到地图，也直接决定多城市刷新会不会失败，
     * 不要再为了"快一点"把它调小。
     */
    private val requestLock = Mutex()
    /** 下一个可以发请求的时刻。用"预约时间片"而不是"记录上次时刻"，多协程排队才不会挤在一起。 */
    private var nextSlotAt = 0L
    private val minimumIntervalMs = 1_200L

    /** 撞 429 后最多再试几次（每次退避时间递增）。 */
    private val MAX_RETRY_ON_429 = 2

    /** 拉取一个城市的完整天气。 */
    suspend fun fetchWeather(city: WeatherCity, dailySteps: Int = 10, hourlySteps: Int = 24): CaiyunResponse {
        if (!auth.isConfigured) throw WeatherError.NotConfigured

        // 彩云 URL 中经度在前
        val coordinate = "%.4f,%.4f".format(city.longitude, city.latitude)

        val path = when (auth) {
            is CaiyunAuth.Token -> "/v2.6/${auth.token}/$coordinate/weather"
            is CaiyunAuth.AppKey -> "/v2.6/${auth.key}/$coordinate/weather"
        }

        val query = mapOf(
            "alert" to "true",
            "dailysteps" to dailySteps.toString(),
            "hourlysteps" to hourlySteps.toString(),
            "lang" to language,
            "unit" to "metric"
        )

        return execute(path, query)
    }

    /**
     * 轻量实况接口 `/realtime`：只返回实况，不含逐时/逐日。
     *
     * 响应约 800 字节、200ms 左右，**一次就同时给出气温、降水强度、风速风向和空气质量**，
     * 所以很适合拿来给地图做网格采样 —— 25 个点约 2 秒就能填满一屏。
     *
     * `throttle = false`：网格请求靠调用方的并发闸门限速，
     * 不走单城市刷新那套"最小间隔串行"，否则 25 个点要排队 8 秒以上。
     */
    suspend fun fetchRealtime(
        latitude: Double,
        longitude: Double,
        throttle: Boolean = false
    ): CaiyunRealtime {
        if (!auth.isConfigured) throw WeatherError.NotConfigured

        val coordinate = "%.4f,%.4f".format(longitude, latitude)
        val path = when (auth) {
            is CaiyunAuth.Token -> "/v2.6/${auth.token}/$coordinate/realtime"
            is CaiyunAuth.AppKey -> "/v2.6/${auth.key}/$coordinate/realtime"
        }

        val response = execute(path, mapOf("lang" to language), throttle = throttle)
        return response.result?.realtime ?: throw WeatherError.Parse("realtime 字段为空")
    }

    private suspend fun execute(
        path: String,
        query: Map<String, String>,
        throttle: Boolean = true
    ): CaiyunResponse {
        val urlBuilder = ("https://api.caiyunapp.com$path").toHttpUrl().newBuilder()
        query.toSortedMap().forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val url = urlBuilder.build()

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .get()

        if (auth is CaiyunAuth.AppKey) {
            val signature = sign(
                method = "GET",
                path = path,
                query = url.encodedQuery.orEmpty(),
                appKey = auth.key,
                secret = auth.secret
            )
            requestBuilder.header("x-cy-nonce", signature.first)
                .header("x-cy-timestamp", signature.second)
                .header("x-cy-signature", signature.third)
        }

        val body = withContext(Dispatchers.IO) {
            var attempt = 0
            while (true) {
                if (throttle) throttle()
                try {
                    client.newCall(requestBuilder.build()).execute().use { response ->
                        val text = response.body?.string().orEmpty()
                        if (response.isSuccessful) return@withContext text

                        // 429 说明撞上限流了：退避后重试，而不是直接把错误抛给用户
                        if (response.code == 429 && attempt < MAX_RETRY_ON_429) {
                            backOff(attempt)
                            attempt++
                            return@use
                        }
                        if (response.code == 429) throw WeatherError.RateLimited
                        throw WeatherError.Server(response.code, text.take(160))
                    }
                } catch (e: WeatherError) {
                    throw e
                } catch (e: IOException) {
                    throw WeatherError.Network(e.message ?: "连接失败")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            ""
        }

        // 彩云把业务错误放在 HTTP 200 里
        val failure = runCatching { json.decodeFromString<CaiyunErrorResponse>(body) }.getOrNull()
        if (failure != null && failure.status == "failed") {
            throw mapApiError(failure.error.orEmpty())
        }

        return try {
            json.decodeFromString<CaiyunResponse>(body)
        } catch (e: Exception) {
            throw WeatherError.Parse(e.message ?: "未知字段")
        }
    }

    private fun mapApiError(message: String): WeatherError {
        val lower = message.lowercase(Locale.US)
        return when {
            "rate" in lower || "limit" in lower || "too many" in lower -> WeatherError.RateLimited
            "token" in lower || "auth" in lower || "unauthorized" in lower -> WeatherError.Unauthorized
            else -> WeatherError.Server(200, message)
        }
    }

    /** 排队取一个时间片。多协程同时调用时会依次排开，不会挤在同一时刻打出去。 */
    private suspend fun throttle() = requestLock.withLock {
        val now = System.currentTimeMillis()
        val wait = nextSlotAt - now
        if (wait > 0) delay(wait)
        nextSlotAt = System.currentTimeMillis() + minimumIntervalMs
    }

    /** 撞限流后的退避：等得越久说明越挤，逐步拉长。 */
    private suspend fun backOff(attempt: Int) {
        val wait = minimumIntervalMs * (attempt + 1) * 2L
        delay(wait)
    }

    /** 返回 (nonce, timestamp, signature)。 */
    private fun sign(method: String, path: String, query: String, appKey: String, secret: String): Triple<String, String, String> {
        val nonce = UUID.randomUUID().toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val original = "$method:$path:$query:$appKey:$nonce:$timestamp"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val urlSafe = Base64.encodeToString(mac.doFinal(original.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
            .replace("+", "-").replace("/", "_").replace("=", "")

        return Triple(nonce, timestamp, urlSafe)
    }

    /** 供调试：签名原文，方便和官方文档比对。 */
    /** 解析彩云的 ISO8601 时间串。 */
    companion object {
        private val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        /**
         * 彩云时间形如 `2026-10-06T12:00+08:00`：分钟精度、自带时区偏移、无秒。
         * 串里的偏移优先于传入的 timeZone；解析不出来返回 null（不抛异常）。
         */
        fun parseIso(text: String?, defaultZone: TimeZone = TimeZone.getTimeZone("UTC")): Long? {
            val value = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            for (pattern in patterns) {
                val parsed = runCatching {
                    SimpleDateFormat(pattern, Locale.US).apply {
                        isLenient = false
                        timeZone = defaultZone
                    }.parse(value)
                }.getOrNull()
                if (parsed != null) return parsed.time
            }
            return null
        }
    }
}
