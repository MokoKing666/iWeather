package com.moko.iweather

import android.app.Application
import com.moko.iweather.core.WeatherNotifications
import com.moko.iweather.ui.theme.AppFonts
import org.osmdroid.config.Configuration
import timber.log.Timber
import java.io.File

class WeatherApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // 日志：debug 装 Tree，release 不装就等于全部静默。
        // 之前几乎没有任何日志，出问题只能靠猜。
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 通知渠道要先建好，否则 Android 8.0+ 上推通知会被直接丢掉。
        WeatherNotifications.ensureChannels(this)

        // 用户开过通知就重新排一次周期任务；KEEP 策略不会把已有排期往后推。
        if (WeatherNotifications.isEnabled(this)) {
            WeatherNotifications.schedule(this)
        }

        // 先确认字体能被系统解析；解析不了就退回系统字体，不让界面排版时闪退。
        AppFonts.verify(this)

        // osmdroid 必须先配置缓存目录与 User-Agent，否则瓦片不加载甚至崩溃。
        val base = File(cacheDir, "osmdroid")
        Configuration.getInstance().apply {
            load(this@WeatherApplication, getSharedPreferences("osmdroid", MODE_PRIVATE))
            userAgentValue = "$packageName/${BuildConfig.VERSION_NAME}"
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
    }
}