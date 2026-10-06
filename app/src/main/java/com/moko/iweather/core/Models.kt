package com.moko.iweather.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------------------------------------------------------------- 天气现象

/** 彩云 skycon 的 20 种取值。 */
enum class Skycon(val raw: String, val text: String, val family: SkyconFamily) {
    CLEAR_DAY("CLEAR_DAY", "晴", SkyconFamily.CLEAR),
    CLEAR_NIGHT("CLEAR_NIGHT", "晴", SkyconFamily.CLEAR),
    PARTLY_CLOUDY_DAY("PARTLY_CLOUDY_DAY", "多云", SkyconFamily.PARTLY_CLOUDY),
    PARTLY_CLOUDY_NIGHT("PARTLY_CLOUDY_NIGHT", "多云", SkyconFamily.PARTLY_CLOUDY),
    CLOUDY("CLOUDY", "阴", SkyconFamily.CLOUDY),
    LIGHT_HAZE("LIGHT_HAZE", "轻度污染", SkyconFamily.HAZE),
    MODERATE_HAZE("MODERATE_HAZE", "中度污染", SkyconFamily.HAZE),
    HEAVY_HAZE("HEAVY_HAZE", "重度污染", SkyconFamily.HAZE),
    LIGHT_RAIN("LIGHT_RAIN", "小雨", SkyconFamily.RAIN),
    MODERATE_RAIN("MODERATE_RAIN", "中雨", SkyconFamily.RAIN),
    HEAVY_RAIN("HEAVY_RAIN", "大雨", SkyconFamily.RAIN),
    LIGHT_SNOW("LIGHT_SNOW", "小雪", SkyconFamily.SNOW),
    MODERATE_SNOW("MODERATE_SNOW", "中雪", SkyconFamily.SNOW),
    HEAVY_SNOW("HEAVY_SNOW", "大雪", SkyconFamily.SNOW),
    DUST("DUST", "浮尘", SkyconFamily.DUST),
    WIND("WIND", "大风", SkyconFamily.WIND),
    FOG("FOG", "雾", SkyconFamily.FOG),
    LIGHT_HAIL("LIGHT_HAIL", "小冰雹", SkyconFamily.SNOW),
    HEAVY_HAIL("HEAVY_HAIL", "强冰雹", SkyconFamily.SNOW),
    UNKNOWN("UNKNOWN", "未知", SkyconFamily.UNKNOWN);

    companion object {
        private val byRaw = entries.associateBy { it.raw }
        fun from(raw: String?): Skycon = byRaw[raw] ?: UNKNOWN
    }
}

/** 归类后的天气族，决定背景配色与动画。 */
enum class SkyconFamily { CLEAR, PARTLY_CLOUDY, CLOUDY, RAIN, SNOW, FOG, HAZE, DUST, WIND, UNKNOWN }

enum class AlertLevel(val text: String) {
    BLUE("蓝色"), YELLOW("黄色"), ORANGE("橙色"), RED("红色"), UNKNOWN("未知");

    companion object {
        /** v2.6 返回中文等级，旧文档写的是英文，两种都认。 */
        fun from(raw: String?): AlertLevel = when (raw?.trim()?.uppercase()) {
            "BLUE", "蓝色" -> BLUE
            "YELLOW", "黄色" -> YELLOW
            "ORANGE", "橙色" -> ORANGE
            "RED", "红色" -> RED
            else -> UNKNOWN
        }
    }
}

enum class TemperatureUnit(val symbol: String, val displayName: String) {
    CELSIUS("°C", "摄氏度"), FAHRENHEIT("°F", "华氏度");

    fun convert(celsius: Double): Double =
        if (this == CELSIUS) celsius else celsius * 9.0 / 5.0 + 32.0

    fun format(value: Double?): String = value?.let { "${convert(it).roundToInt()}" } ?: "--"

    fun formatWithUnit(value: Double?): String =
        value?.let { "${convert(it).roundToInt()}$symbol" } ?: "--"

    fun formatProbability(value: Double?): String =
        value?.let { "${(it * 100).roundToInt()}%" } ?: "--"
}

// ---------------------------------------------------------------- 城市

@Serializable
data class WeatherCity(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val adminArea: String? = null,
    val country: String? = null,
    val countryCode: String? = null,
    val timeZoneId: String = provisionalTimeZone(longitude),
    val isCurrentLocation: Boolean = false
) {
    val subtitle: String
        get() = listOfNotNull(adminArea, country)
            .filter { it.isNotBlank() && it != name }
            .joinToString(" · ")

    companion object {
        fun makeId(latitude: Double, longitude: Double): String = "%.4f,%.4f".format(latitude, longitude)

        /** 拿到彩云响应的真实时区前，用经度推算一个固定偏移。 */
        fun provisionalTimeZone(longitude: Double): String {
            val hours = (longitude / 15.0).roundToInt().coerceIn(-12, 14)
            return "GMT%s%02d:00".format(if (hours < 0) "-" else "+", abs(hours))
        }

        val Beijing = WeatherCity(
            id = makeId(39.9040, 116.3910),
            name = "北京",
            latitude = 39.9040,
            longitude = 116.3910,
            adminArea = "北京",
            country = "中国",
            countryCode = "CN",
            timeZoneId = "Asia/Shanghai"
        )
    }
}

// ---------------------------------------------------------------- 天气快照

@Serializable
data class Realtime(
    val temperature: Double? = null,
    val apparentTemperature: Double? = null,
    val skycon: Skycon = Skycon.UNKNOWN,
    /** 0~1 */
    val humidity: Double? = null,
    /** hPa（彩云返回 Pa，这里已换算） */
    val pressureHpa: Double? = null,
    /** km */
    val visibilityKm: Double? = null,
    /** m/s */
    val windSpeedMs: Double? = null,
    /** 来向角度，0 = 正北 */
    val windDirectionDeg: Double? = null,
    /** 0~1 */
    val cloudRate: Double? = null,
    /** 0~1 相对强度 */
    val precipitationIntensity: Double? = null,
    /** 下行短波辐射 W/m² */
    val solarRadiation: Double? = null,
    val uvIndex: Double? = null,
    val uvText: String? = null,
    val comfortText: String? = null
) {
    companion object { val EMPTY = Realtime() }
}

@Serializable
data class HourlyPoint(
    val time: Long,
    val temperature: Double? = null,
    val skycon: Skycon = Skycon.UNKNOWN,
    /** 降水概率 0~1 */
    val precipProbability: Double? = null,
    /** 降水强度 0~1 */
    val precipitationIntensity: Double? = null,
    /** m/s */
    val windSpeedMs: Double? = null
) { val id: Long get() = time }

@Serializable
data class DailyPoint(
    val date: Long,
    val skyconDay: Skycon = Skycon.UNKNOWN,
    val temperatureMax: Double? = null,
    val temperatureMin: Double? = null,
    val temperatureAvg: Double? = null,
    /** 降水概率 0~1 */
    val precipProbability: Double? = null,
    /** 降水强度 0~1 */
    val precipMax: Double? = null,
    val sunrise: Long? = null,
    val sunset: Long? = null,
    /** 生活指数文案 */
    val uvText: String? = null,
    val dressingText: String? = null,
    val comfortText: String? = null,
    val carWashText: String? = null
) { val id: Long get() = date }

@Serializable
data class MinutelyPoint(
    val time: Long,
    /** 强度 0...1 */
    val intensity: Double = 0.0
) { val id: Long get() = time }

@Serializable
data class AirQuality(
    val aqi: Double? = null,
    val category: String? = null,
    val primary: String? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val no2: Double? = null,
    val so2: Double? = null,
    val co: Double? = null
) {
    /** 按国标 GB 3095-2016 给等级。 */
    val level: AqiLevel? get() = aqi?.let { AqiLevel.of(it) }

    companion object { val EMPTY = AirQuality() }
}

enum class AqiLevel(val text: String) {
    EXCELLENT("优"), GOOD("良"), LIGHT("轻度污染"), MODERATE("中度污染"),
    HEAVY("重度污染"), SEVERE("严重污染");

    companion object {
        fun of(aqi: Double): AqiLevel = when {
            aqi <= 50 -> EXCELLENT
            aqi <= 100 -> GOOD
            aqi <= 150 -> LIGHT
            aqi <= 200 -> MODERATE
            aqi <= 300 -> HEAVY
            else -> SEVERE
        }
    }
}

@Serializable
data class WeatherAlert(
    val id: String = "",
    val title: String = "",
    val type: String = "",
    val level: AlertLevel = AlertLevel.UNKNOWN,
    val body: String = "",
    val publishedAt: Long? = null,
    val source: String? = null
)

@Serializable
data class WeatherSnapshot(
    val cityId: String,
    val timeZoneId: String,
    /** 抓取时刻（epoch millis）。 */
    val fetchedAt: Long,
    val realtime: Realtime = Realtime.EMPTY,
    val hourly: List<HourlyPoint> = emptyList(),
    val daily: List<DailyPoint> = emptyList(),
    /** 彩云免费账号不返回分钟级降水，留空即隐藏该卡片。 */
    val minutely: List<MinutelyPoint> = emptyList(),
    val airQuality: AirQuality = AirQuality.EMPTY,
    /** 免费账号不返回预警，留空即隐藏该卡片。 */
    val alerts: List<WeatherAlert> = emptyList(),
    /** 彩云给出的自然语言摘要。 */
    val forecastKeypoint: String? = null,
    val hourlySummary: String? = null
) {
    fun today(timeZoneId: String, now: Long): DailyPoint? {
        val zone = TimeZoneResolver.zone(timeZoneId)
        val a = java.util.Calendar.getInstance(zone).apply { timeInMillis = now }
        return daily.firstOrNull { day ->
            val c = java.util.Calendar.getInstance(zone).apply { timeInMillis = day.date }
            c.get(java.util.Calendar.YEAR) == a.get(java.util.Calendar.YEAR) &&
                c.get(java.util.Calendar.DAY_OF_YEAR) == a.get(java.util.Calendar.DAY_OF_YEAR)
        }
    }
}

@Serializable
data class AppSettings(
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    /** 跟随系统深色模式；关闭时强制浅色（天气卡片本身即深色风格）。 */
    val followSystemDarkTheme: Boolean = true,
    val reduceMotion: Boolean = false,
    val refreshOnLaunch: Boolean = true,
    val cacheLifetimeMinutes: Int = 30
)

@Serializable
data class WidgetPreference(
    /** 优先跟随定位城市。 */
    val followCurrentLocation: Boolean = true,
    val smallCityId: String? = null,
    val mediumCityId: String? = null,
    val largeCityId: String? = null
) {
    enum class Layout { SMALL, MEDIUM, LARGE }

    fun cityIdFor(layout: Layout): String? = when (layout) {
        Layout.SMALL -> smallCityId
        Layout.MEDIUM -> mediumCityId
        Layout.LARGE -> largeCityId
    }

    fun withCityId(layout: Layout, cityId: String?): WidgetPreference = when (layout) {
        Layout.SMALL -> copy(smallCityId = cityId)
        Layout.MEDIUM -> copy(mediumCityId = cityId)
        Layout.LARGE -> copy(largeCityId = cityId)
    }
}
