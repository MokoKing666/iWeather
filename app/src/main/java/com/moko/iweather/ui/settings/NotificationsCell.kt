package com.moko.iweather.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.moko.iweather.core.WeatherNotifications
import com.moko.iweather.ui.components.GlassCell
import com.moko.iweather.ui.components.GlassHairLine

/**
 * 设置页里的通知开关。
 *
 * 和 [LicensesCell] 一样是**自带状态的自包含组件**，插进设置页只要一行 ——
 * 这样不必去改设置页的状态结构。
 *
 * ## 为什么点击时要跑一遍权限申请
 * Android 13 起 `POST_NOTIFICATIONS` 是**运行时权限**。
 * 只把开关打开、不申请权限，通知会被系统直接丢掉，
 * 用户看到的是"开了但从来没通知过"，比报错还难查。
 * 所以先申请、拿到授权才真正开启。
 */
@Composable
fun NotificationsCell() {
    val context = LocalContext.current

    var enabled by remember { mutableStateOf(WeatherNotifications.isEnabled(context)) }
    var rain by remember { mutableStateOf(WeatherNotifications.isRainAlertsEnabled(context)) }
    var severe by remember { mutableStateOf(WeatherNotifications.isSevereAlertsEnabled(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            WeatherNotifications.setEnabled(context, true)
            enabled = true
        }
    }

    fun turnOn() {
        val needsRuntimePermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        if (!needsRuntimePermission || WeatherNotifications.hasPermission(context)) {
            WeatherNotifications.setEnabled(context, true)
            enabled = true
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    GlassCell(
        title = "天气通知",
        subtitle = if (enabled) "后台每小时检查一次" else "关闭时不消耗任何请求配额",
        value = if (enabled) "已开启" else "已关闭",
        icon = Icons.Filled.Notifications,
        onTap = {
            if (enabled) {
                WeatherNotifications.setEnabled(context, false)
                enabled = false
            } else {
                turnOn()
            }
        }
    )

    if (enabled) {
        GlassHairLine()
        GlassCell(
            title = "降水提醒",
            subtitle = "雨雪即将开始或停止时提醒",
            value = if (rain) "开" else "关",
            onTap = {
                rain = !rain
                WeatherNotifications.setRainAlertsEnabled(context, rain)
            }
        )
        GlassHairLine()
        GlassCell(
            title = "灾害预警",
            subtitle = "台风、暴雨、高温等预警信号",
            value = if (severe) "开" else "关",
            onTap = {
                severe = !severe
                WeatherNotifications.setSevereAlertsEnabled(context, severe)
            }
        )
    }
}
