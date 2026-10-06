package com.moko.iweather.core

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.edit
import kotlin.math.roundToInt

/**
 * 天气地图的图层。
 *
 * 数据来源分两条路，界面上会如实标注，避免看起来像坏了：
 *
 *  - **气温 / 降水 / 风**：来自中国气象局的全国站点气象图（见 [CmaStation]），
 *    一次请求 2440 个观测站，出的是真实空间分布，而且不占彩云配额；
 *  - **空气质量**：国家级的免费全量数据源没找到，所以这一层是
 *    「已保存城市标注 + 点地图任意位置用彩云逐点查询」。
 *
 * 至于为什么不用现成的天气栅格瓦片服务：
 *  - RainViewer 的雷达拼图来自 NOAA / OPERA，**中国没有覆盖**，国内看降水是全透明的（实测）；
 *  - OpenWeatherMap / Windy 的栅格图层都要求额外的 Key。
 */
enum class WeatherMapLayer(
    val label: String,
    val icon: ImageVector,
    /** 图例上显示的数值单位。 */
    val unitLabel: String
) {
    PRECIPITATION("降水", Icons.Filled.WaterDrop, "强度"),
    TEMPERATURE("气温", Icons.Filled.Thermostat, "°C"),
    AIR_QUALITY("空气质量", Icons.Filled.BubbleChart, "AQI"),
    WIND("风", Icons.Filled.Air, "m/s");

    companion object {
        private const val PREFS = "iweather_map_layer"
        private const val KEY = "layer"

        fun current(context: Context): WeatherMapLayer {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return TEMPERATURE
            return entries.firstOrNull { it.name == name } ?: TEMPERATURE
        }

        fun set(context: Context, layer: WeatherMapLayer) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY, layer.name)
            }
        }
    }
}

/** 四项指标的统一读法，网格采样点和城市标注都实现它。 */
interface WeatherMetrics {
    /** 摄氏度 */
    val temperature: Double?
    /** 0~1 相对强度 */
    val precipitationIntensity: Double?
    /** 国标 AQI */
    val aqi: Double?
    /** m/s */
    val windSpeedMs: Double?
}

fun WeatherMapLayer.valueOf(metrics: WeatherMetrics): Double? = when (this) {
    WeatherMapLayer.PRECIPITATION -> metrics.precipitationIntensity
    WeatherMapLayer.TEMPERATURE -> metrics.temperature
    WeatherMapLayer.AIR_QUALITY -> metrics.aqi
    WeatherMapLayer.WIND -> metrics.windSpeedMs
}

/**
 * 数值 → 颜色。
 *
 * 气温用冷→暖、AQI 用国标六段色、风用风速色阶、降水用雷达强度色阶。
 * 统一遵守"越红越极端"的直觉。
 */
fun WeatherMapLayer.colorFor(rawValue: Double?): Color {
    val value = rawValue ?: return Color(0xFF8A8F98)
    return when (this) {
        WeatherMapLayer.PRECIPITATION -> when {
            value <= 0.02 -> Color(0xFF4FC3F7)
            value < 0.20 -> Color(0xFF29B6F6)
            value < 0.45 -> Color(0xFF1E88E5)
            value < 0.75 -> Color(0xFF7E57C2)
            else -> Color(0xFFE53935)
        }

        WeatherMapLayer.TEMPERATURE -> when {
            value < -10 -> Color(0xFF4A5BC4)
            value < 0 -> Color(0xFF3F8FE0)
            value < 10 -> Color(0xFF29B6F6)
            value < 18 -> Color(0xFF43A047)
            value < 26 -> Color(0xFFF5C542)
            value < 32 -> Color(0xFFF57C00)
            else -> Color(0xFFD32F2F)
        }

        WeatherMapLayer.AIR_QUALITY -> when {
            value <= 50 -> Color(0xFF35C759)
            value <= 100 -> Color(0xFF9BD44F)
            value <= 150 -> Color(0xFFF5C542)
            value <= 200 -> Color(0xFFF59E42)
            value <= 300 -> Color(0xFFEB5757)
            else -> Color(0xFFB04A6E)
        }

        WeatherMapLayer.WIND -> when {
            value < 3 -> Color(0xFF4DD0E1)
            value < 6 -> Color(0xFF4FC3F7)
            value < 10 -> Color(0xFF1E88E5)
            value < 17 -> Color(0xFF7E57C2)
            else -> Color(0xFFE53935)
        }
    }
}

/** 顶部图例的渐变色带，和图层的色阶保持一致。 */
fun WeatherMapLayer.legendStops(): List<Color> = when (this) {
    WeatherMapLayer.PRECIPITATION -> listOf(
        Color(0xFF4FC3F7), Color(0xFF1E88E5), Color(0xFF7E57C2), Color(0xFFE53935)
    )
    WeatherMapLayer.TEMPERATURE -> listOf(
        Color(0xFF4A5BC4), Color(0xFF29B6F6), Color(0xFF43A047),
        Color(0xFFF5C542), Color(0xFFD32F2F)
    )
    WeatherMapLayer.AIR_QUALITY -> listOf(
        Color(0xFF35C759), Color(0xFF9BD44F), Color(0xFFF5C542),
        Color(0xFFF59E42), Color(0xFFEB5757), Color(0xFFB04A6E)
    )
    WeatherMapLayer.WIND -> listOf(
        Color(0xFF4DD0E1), Color(0xFF1E88E5), Color(0xFF7E57C2), Color(0xFFE53935)
    )
}

/** 图例两端的文字。 */
fun WeatherMapLayer.legendLabels(unit: TemperatureUnit): Pair<String, String> = when (this) {
    WeatherMapLayer.PRECIPITATION -> "无" to "强"
    WeatherMapLayer.TEMPERATURE -> unit.formatWithUnit(-15.0) to unit.formatWithUnit(38.0)
    WeatherMapLayer.AIR_QUALITY -> "0" to "300+"
    WeatherMapLayer.WIND -> "0" to "17+"
}

/** 单个数值的展示文案（信息卡、标注里用）。 */
fun WeatherMapLayer.formatValue(rawValue: Double?, unit: TemperatureUnit): String {
    val value = rawValue ?: return "--"
    return when (this) {
        WeatherMapLayer.PRECIPITATION ->
            if (value <= 0.02) "无降水" else "强度 ${(value * 100).roundToInt()}"
        WeatherMapLayer.TEMPERATURE -> unit.formatWithUnit(value)
        WeatherMapLayer.AIR_QUALITY -> "${value.roundToInt()}"
        WeatherMapLayer.WIND -> "%.1f m/s".format(value)
    }
}

/** 标注圆圈里显示的紧凑文案（要短）。 */
fun WeatherMapLayer.compactLabel(rawValue: Double?, unit: TemperatureUnit): String {
    val value = rawValue ?: return "–"
    return when (this) {
        WeatherMapLayer.PRECIPITATION ->
            if (value <= 0.02) "–" else "${(value * 100).roundToInt()}"
        WeatherMapLayer.TEMPERATURE -> unit.format(value)
        WeatherMapLayer.AIR_QUALITY -> "${value.roundToInt()}"
        WeatherMapLayer.WIND -> "${value.roundToInt()}"
    }
}

/** 某个点位的天气摘要，用于地图标注与底部信息卡。 */
data class MapPointWeather(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    override val temperature: Double?,
    val skycon: Skycon,
    override val aqi: Double?,
    val aqiCategory: String?,
    override val windSpeedMs: Double?,
    val windDirectionDeg: Double?,
    override val precipitationIntensity: Double?,
    val isSavedCity: Boolean = false
) : WeatherMetrics
