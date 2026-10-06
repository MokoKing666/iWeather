package com.moko.iweather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.moko.iweather.core.AqiLevel
import com.moko.iweather.core.AlertLevel

private val DarkColors = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    background = Color(0xFF07090C),
    onBackground = Color.White,
    surface = Color(0xFF12161B),
    onSurface = Color.White
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A6CFF),
    onPrimary = Color.White,
    background = Color(0xFFF2F4F7),
    onBackground = Color(0xFF11151B),
    surface = Color.White,
    onSurface = Color(0xFF11151B)
)

/**
 * 主题只影响设置页/城市页这类普通界面；
 * 天气主页永远是白字压深色渐变，不走 Material 的明暗。
 */
@Composable
fun IWeatherTheme(
    followSystemDarkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = if (followSystemDarkTheme) isSystemInDarkTheme() else false

    // 西文与数字走 SF Pro、汉字走苹方（已在 build 阶段合并进同一个字体文件）
    val typography = remember { Typography().withAppFontFamily() }

    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = typography,
        content = content
    )
}

/** 预警级别配色（对齐 Apple 的蓝/黄/橙/红）。 */
fun alertColor(level: AlertLevel): Color = when (level) {
    AlertLevel.BLUE -> Color(0xFF3B82F6)
    AlertLevel.YELLOW -> Color(0xFFF5C542)
    AlertLevel.ORANGE -> Color(0xFFF59E42)
    AlertLevel.RED -> Color(0xFFEB5757)
    AlertLevel.UNKNOWN -> Color(0xFF9AA5B1)
}

/** AQI 等级配色（国标六段）。 */
fun aqiColor(level: AqiLevel?): Color = when (level) {
    AqiLevel.EXCELLENT -> Color(0xFF35C759)
    AqiLevel.GOOD -> Color(0xFF9BD44F)
    AqiLevel.LIGHT -> Color(0xFFF5C542)
    AqiLevel.MODERATE -> Color(0xFFF59E42)
    AqiLevel.HEAVY -> Color(0xFFEB5757)
    AqiLevel.SEVERE -> Color(0xFFB04A6E)
    null -> Color(0xFF9AA5B1)
}

val AqiGradientColors = listOf(
    Color(0xFF35C759), Color(0xFF9BD44F), Color(0xFFF5C542),
    Color(0xFFF59E42), Color(0xFFEB5757), Color(0xFFB04A6E)
)