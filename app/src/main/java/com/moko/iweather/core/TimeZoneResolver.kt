package com.moko.iweather.core

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/** 时区解析 + 昼夜判定。 */
object TimeZoneResolver {

    private val cache = HashMap<String, TimeZone>()

    fun zone(id: String): TimeZone =
        synchronized(cache) {
            cache.getOrPut(id) {
                TimeZone.getTimeZone(id) ?: TimeZone.getTimeZone("GMT+00:00")
            }
        }

    fun isValidId(id: String): Boolean = TimeZone.getTimeZone(id).id == id

/** 没有 IANA 名但知道 UTC 偏移时的兜底（彩云的 tzshift，单位秒）。 */
    fun zoneByOffsetSeconds(seconds: Int): TimeZone? {
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        val id = "GMT%s%02d:%02d".format(sign, abs / 3600, (abs % 3600) / 60)
        val zone = runCatching { TimeZone.getTimeZone(id) }.getOrNull()
        return if (zone != null && zone.id == id) zone else null
    }

    /** 按城市时区格式化成 HH:mm。 */
    fun formatTime(millis: Long, timeZoneId: String, pattern: String = "HH:mm"): String {
        val fmt = java.text.SimpleDateFormat(pattern, Locale.CHINA)
        fmt.timeZone = zone(timeZoneId)
        return fmt.format(java.util.Date(millis))
    }

    fun formatDate(millis: Long, timeZoneId: String, pattern: String): String {
        val fmt = java.text.SimpleDateFormat(pattern, Locale.CHINA)
        fmt.timeZone = zone(timeZoneId)
        return fmt.format(java.util.Date(millis))
    }

    fun isSameDay(a: Long, b: Long, timeZoneId: String): Boolean {
        val ca = Calendar.getInstance(zone(timeZoneId)).apply { timeInMillis = a }
        val cb = Calendar.getInstance(zone(timeZoneId)).apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun hourOfDay(millis: Long, timeZoneId: String): Int =
        Calendar.getInstance(zone(timeZoneId)).apply { timeInMillis = millis }.get(Calendar.HOUR_OF_DAY)
}

/** 昼夜判定结果。 */
data class DayNight(
    val isDaytime: Boolean,
    val sunrise: Long?,
    val sunset: Long?,
    /** 下一次日出或日落的绝对时刻。 */
    val nextTransition: Long?,
    val transitionIsSunrise: Boolean
)

object DayNightResolver {

    /**
     * 优先用彩云返回的日出日落；缺失时（例如极昼极夜）用天文公式兜底。
     */
    fun resolve(snapshot: WeatherSnapshot?, timeZoneId: String, city: WeatherCity?, now: Long = System.currentTimeMillis()): DayNight {
        val today = snapshot?.today(timeZoneId, now)

        var sunrise = today?.sunrise
        var sunset = today?.sunset

        if ((sunrise == null || sunset == null) && city != null) {
            val fallback = SolarCalculator.eventsForDay(
                now = now,
                latitude = city.latitude,
                longitude = city.longitude,
                timeZoneId = timeZoneId
            )
            if (sunrise == null) sunrise = fallback.first
            if (sunset == null) sunset = fallback.second
        }

        if (sunrise == null || sunset == null) {
            val hour = TimeZoneResolver.hourOfDay(now, timeZoneId)
            val day = hour in 6..17
            return DayNight(day, null, null, null, true)
        }

        val day = now in sunrise..sunset
        val next = if (day) sunset else sunrise
        return DayNight(
            isDaytime = day,
            sunrise = sunrise,
            sunset = sunset,
            nextTransition = next,
            transitionIsSunrise = !day
        )
    }
}

/**
 * 日出日落天文计算（NOAA 简化公式）。
 * 用于彩云没给 sunrise/sunset 的场景（高纬度、极昼极夜）。
 */
object SolarCalculator {

    private const val JULIAN_UNIX_EPOCH = 2440587.5

    /** 返回当天 [日出, 日落] 的 epoch millis；极昼/极夜返回 null。 */
    fun eventsForDay(
        now: Long,
        latitude: Double,
        longitude: Double,
        timeZoneId: String
    ): Pair<Long?, Long?> {
        val zone = TimeZoneResolver.zone(timeZoneId)
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }

        // 取「当地日期那一天的 0h UT」的儒略日。
        // NOAA 公式以 UT 日为基准，若用当地零点会随经度差出小半天，
        // 高纬度地区会因此算到前一天或后一天。
        val utcMidnight = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                local.get(Calendar.YEAR),
                local.get(Calendar.MONTH),
                local.get(Calendar.DAY_OF_MONTH),
                0, 0, 0
            )
        }.timeInMillis
        val julianDate = utcMidnight / 86_400_000.0 + 2440587.5

        // 自 J2000 起的天数
        val dayNumber = kotlin.math.ceil(julianDate - 2451545.0 + 0.0008)

        // 平均太阳正午。**东经为正，所以是减** —— 东经 120° 的正午在 04:00 UT。
        //
        // 原来这两处都是错的：
        //   `val n = approx - Math.toDegrees(longitude / 15.0).roundToInt()`
        //   longitude/15 得到的是「小时数」，再套 toDegrees（弧度→角度）是双重错误；
        //   而且 solarTransit 里**整个丢了经度项**，
        //   结果是日出日落时刻与所在经度无关 —— 只对 0° 经线附近才对。
        val meanSolarNoon = dayNumber - longitude / 360.0

        val meanAnomaly = (357.5291 + 0.98560028 * meanSolarNoon).mod(360.0)
        val center = 1.9148 * sinDeg(meanAnomaly) +
            0.0200 * sinDeg(2 * meanAnomaly) +
            0.0003 * sinDeg(3 * meanAnomaly)
        val eclipticLongitude = (meanAnomaly + center + 180.0 + 102.9372).mod(360.0)

        val solarTransit = 2451545.0 + meanSolarNoon +
            0.0053 * sinDeg(meanAnomaly) -
            0.0069 * sinDeg(2 * eclipticLongitude)

        val declination = asin(sinDeg(eclipticLongitude) * sinDeg(23.44))
        val cosHourAngle =
            (sinDeg(-0.833) - sin(declination) * sinDeg(latitude)) /
                (cos(declination) * cosDeg(latitude))

        // |cosH| > 1 表示极昼或极夜，当天没有日出日落。
        if (abs(cosHourAngle) > 1.0) return null to null

        // 时角是角度，一天 360°，所以直接 /360 就是「天的比例」
        val hourAngle = Math.toDegrees(acos(cosHourAngle))
        val sunriseJulian = solarTransit - hourAngle / 360.0
        val sunsetJulian = solarTransit + hourAngle / 360.0

        return millisFromJulian(sunriseJulian) to millisFromJulian(sunsetJulian)
    }

    // 标准定义：JD = unixMillis / 86400000 + 2440587.5（1970-01-01 00:00 UT 的儒略日）。
    // 原来两边各带了 -0.5 / +0.5，等于把基准点挪到 1969-12-31 12:00 UT，整体差半天。
    private fun millisFromJulian(julian: Double): Long =
        ((julian - JULIAN_UNIX_EPOCH) * 86_400_000.0).toLong()

    private fun sinDeg(value: Double): Double = sin(Math.toRadians(value))

    private fun cosDeg(value: Double): Double = cos(Math.toRadians(value))

    private fun Double.mod(m: Double): Double {
        val r = this % m
        return if (r < 0) r + m else r
    }
}
