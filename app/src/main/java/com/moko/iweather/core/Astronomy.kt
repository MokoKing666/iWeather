package com.moko.iweather.core

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/**
 * 露点、月相、月出月落、农历 —— 全部本地计算，不依赖任何网络接口。
 *
 * 这几项之前标的是"未实现"，但查下来发现**根本不需要数据源**：
 * 露点由温度与湿度直接算出，月亮与农历是天文/历法计算。
 */

// ------------------------------------------------------------------ 露点

object DewPoint {

    /**
     * Magnus 公式：由气温与相对湿度求露点。
     *
     * @param temperatureC 摄氏度
     * @param humidityPercent 相对湿度，**百分数**（0~100）
     */
    fun celsius(temperatureC: Double, humidityPercent: Double): Double? {
        if (humidityPercent <= 0.0) return null
        val rh = humidityPercent.coerceIn(1.0, 100.0)
        val a = 17.625
        val b = 243.04
        val gamma = kotlin.math.ln(rh / 100.0) + (a * temperatureC) / (b + temperatureC)
        return b * gamma / (a - gamma)
    }

    /** 彩云的湿度是 0~1 的小数，这里转成百分数再算。 */
    fun celsiusFromRatio(temperatureC: Double, humidityRatio: Double?): Double? {
        val ratio = humidityRatio ?: return null
        return celsius(temperatureC, ratio * 100.0)
    }
}

// ------------------------------------------------------------------ 月球

/** 月相。 */
enum class MoonPhase(val text: String, val symbol: String) {
    NEW_MOON("新月", "🌑"),
    WAXING_CRESCENT("娥眉月", "🌒"),
    FIRST_QUARTER("上弦月", "🌓"),
    WAXING_GIBBOUS("盈凸月", "🌔"),
    FULL_MOON("满月", "🌕"),
    WANING_GIBBOUS("亏凸月", "🌖"),
    LAST_QUARTER("下弦月", "🌗"),
    WANING_CRESCENT("残月", "🌘");

    companion object {
        /** 按日月角距（0~360°）判相位。 */
        fun of(elongationDegrees: Double): MoonPhase {
            val angle = elongationDegrees.mod(360.0)
            return when {
                angle < 22.5 || angle >= 337.5 -> NEW_MOON
                angle < 67.5 -> WAXING_CRESCENT
                angle < 112.5 -> FIRST_QUARTER
                angle < 157.5 -> WAXING_GIBBOUS
                angle < 202.5 -> FULL_MOON
                angle < 247.5 -> WANING_GIBBOUS
                angle < 292.5 -> LAST_QUARTER
                else -> WANING_CRESCENT
            }
        }
    }
}

/** 某一天的月亮信息。 */
data class MoonInfo(
    val phase: MoonPhase,
    /** 照明比例 0~1 */
    val illumination: Double,
    /** 月龄，0~29.53 天 */
    val ageDays: Double,
    val moonrise: Long?,
    val moonset: Long?,
    /** 下一次满月 */
    val nextFullMoon: Long,
    /** 下一次新月 */
    val nextNewMoon: Long
)

/**
 * 月亮位置用低精度星历（Jean Meeus《Astronomical Algorithms》简化式），
 * 精度约 0.3°，对"什么时候月出、什么相位"完全够用。
 */
object MoonCalculator {

    private const val DEG = Math.PI / 180.0

    /** 月亮的地心黄经与黄纬（度）。daysSinceJ2000 为自 J2000 起的天数。 */
    private fun eclipticLongitudeLatitude(daysSinceJ2000: Double): Pair<Double, Double> {
        val meanLongitude = 218.316 + 13.176396 * daysSinceJ2000
        val meanAnomaly = 134.963 + 13.064993 * daysSinceJ2000
        val meanDistance = 93.272 + 13.229350 * daysSinceJ2000

        val longitude = meanLongitude + 6.289 * sin(meanAnomaly * DEG)
        val latitude = 5.128 * sin(meanDistance * DEG)
        return longitude.mod(360.0) to latitude
    }

    /** 太阳的地心黄经（度）。 */
    private fun sunEclipticLongitude(daysSinceJ2000: Double): Double {
        val meanAnomaly = 357.529 + 0.98560028 * daysSinceJ2000
        val meanLongitude = 280.459 + 0.98564736 * daysSinceJ2000
        val center = 1.915 * sin(meanAnomaly * DEG) + 0.020 * sin(2 * meanAnomaly * DEG)
        return (meanLongitude + center).mod(360.0)
    }

    /** 日月角距（度）：0 = 新月，180 = 满月。 */
    private fun elongation(daysSinceJ2000: Double): Double {
        val (moonLongitude, _) = eclipticLongitudeLatitude(daysSinceJ2000)
        val sunLongitude = sunEclipticLongitude(daysSinceJ2000)
        return (moonLongitude - sunLongitude).mod(360.0)
    }

    /** 月亮在某一时刻的地平高度（度）。 */
    private fun altitudeDegrees(daysSinceJ2000: Double, latitude: Double, longitude: Double): Double {
        val (moonLongitude, moonLatitude) = eclipticLongitudeLatitude(daysSinceJ2000)
        val obliquity = 23.4397

        // 黄道 → 赤道
        val rightAscension = atan2(
            sin(moonLongitude * DEG) * cos(obliquity * DEG) - tan(moonLatitude * DEG) * sin(obliquity * DEG),
            cos(moonLongitude * DEG)
        ) / DEG
        val declination = asin(
            sin(moonLatitude * DEG) * cos(obliquity * DEG) +
                cos(moonLatitude * DEG) * sin(obliquity * DEG) * sin(moonLongitude * DEG)
        ) / DEG

        // 地方恒星时
        val siderealTime = (280.16 + 360.9856235 * daysSinceJ2000 + longitude).mod(360.0)
        val hourAngle = (siderealTime - rightAscension).mod(360.0)

        val altitude = asin(
            sin(latitude * DEG) * sin(declination * DEG) +
                cos(latitude * DEG) * cos(declination * DEG) * cos(hourAngle * DEG)
        ) / DEG
        return altitude
    }

    private fun daysSinceJ2000(epochMillis: Long): Double =
        (epochMillis / 86_400_000.0) + 2440587.5 - 2451545.0

    /** 月亮的地平高度阈值：月面视半径 + 折射 − 地平视差，约 +0.125°。 */
    private const val RISE_THRESHOLD = 0.125

    /**
     * 求某一天的月出月落。
     *
     * 用**扫描高度角**而不是解析解：每 2 分钟算一次月亮高度，
     * 找穿过 [RISE_THRESHOLD] 的时刻。一天 720 次三角运算，
     * 在手机上不到 1 毫秒，却免去了解析法里一堆容易写错的修正项。
     */
    fun riseAndSet(
        dateMillis: Long,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone
    ): Pair<Long?, Long?> {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = dateMillis }
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dayStart = calendar.timeInMillis

        val stepMillis = 2 * 60_000L
        var rise: Long? = null
        var set: Long? = null

        var previous = altitudeDegrees(daysSinceJ2000(dayStart), latitude, longitude)
        var offset = stepMillis
        while (offset <= 24 * 60 * 60 * 1000L) {
            val current = altitudeDegrees(daysSinceJ2000(dayStart + offset), latitude, longitude)
            if (previous < RISE_THRESHOLD && current >= RISE_THRESHOLD && rise == null) {
                rise = dayStart + offset
            }
            if (previous >= RISE_THRESHOLD && current < RISE_THRESHOLD && set == null) {
                set = dayStart + offset
            }
            previous = current
            offset += stepMillis
        }
        return rise to set
    }

    /**
     * 从某时刻开始，下一次角距等于 [target] 的时刻。
     *
     * 角距每天增加约 12.2°，6 小时一步足够；差值从大跳回小就说明跨过了目标，
     * 再在两点之间线性插值即可（满月/新月精确到小时级完全够）。
     */
    private fun nextElongation(fromMillis: Long, target: Double): Long {
        val stepMillis = 6 * 3_600_000L
        val limit = fromMillis + 40L * 86_400_000L

        var previousTime = fromMillis
        var previous = (elongation(daysSinceJ2000(previousTime)) - target).mod(360.0)
        var time = fromMillis + stepMillis

        while (time < limit) {
            val current = (elongation(daysSinceJ2000(time)) - target).mod(360.0)
            if (current < previous) {
                // 跨过目标：previous → 360 再回到 current，总跨度是 previous + (360 - current)
                val span = previous + (360.0 - current)
                val fraction = if (span <= 0.0) 0.5 else previous / span
                return previousTime + ((time - previousTime) * fraction).toLong()
            }
            previous = current
            previousTime = time
            time += stepMillis
        }
        return fromMillis
    }

    private const val SYNODIC_MONTH = 29.530588853

    /** 综合信息。 */
    fun info(
        dateMillis: Long,
        latitude: Double,
        longitude: Double,
        timeZone: TimeZone
    ): MoonInfo {
        val days = daysSinceJ2000(dateMillis)
        val angle = elongation(days)
        val illumination = (1 - cos(angle * DEG)) / 2.0
        val (rise, set) = riseAndSet(dateMillis, latitude, longitude, timeZone)

        return MoonInfo(
            phase = MoonPhase.of(angle),
            illumination = illumination,
            ageDays = angle / 360.0 * SYNODIC_MONTH,
            moonrise = rise,
            moonset = set,
            nextFullMoon = nextElongation(dateMillis, 180.0),
            nextNewMoon = nextElongation(dateMillis, 0.0)
        )
    }
}

// ------------------------------------------------------------------ 农历

/**
 * 公历 → 农历。
 *
 * 农历没有公式可算，只能查表：每年用一个整数编码
 * "12 个月各是大月(30)还是小月(29) + 闰月是第几月 + 闰月大小"。
 * 这张表是通行的公开数据（1900–2100）。
 */
object LunarCalendar {

    private val LUNAR_INFO = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x055c0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    private const val BASE_YEAR = 1900

    private val MONTH_NAMES = arrayOf(
        "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    )
    private val DAY_NAMES = arrayOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    private fun leapMonth(year: Int): Int = (LUNAR_INFO[year - BASE_YEAR] and 0xF).toInt()

    private fun leapMonthDays(year: Int): Int =
        if (leapMonth(year) == 0) 0
        else if (LUNAR_INFO[year - BASE_YEAR] and 0x10000L != 0L) 30 else 29

    private fun monthDays(year: Int, month: Int): Int =
        if (LUNAR_INFO[year - BASE_YEAR] and (0x10000L shr month) != 0L) 30 else 29

    private fun yearDays(year: Int): Int {
        var sum = 348
        var mask = 0x8000L
        while (mask > 0x8) {
            if (LUNAR_INFO[year - BASE_YEAR] and mask != 0L) sum++
            mask = mask shr 1
        }
        return sum + leapMonthDays(year)
    }

    /** 农历日期。 */
    data class LunarDate(val year: Int, val month: Int, val day: Int, val isLeapMonth: Boolean) {
        /** 例如「八月十六」。初一显示月份名。 */
        // 注意必须写 ${monthName}：汉字是合法的 Kotlin 标识符字符，
        // 写成 "$monthName月" 会被解析成变量名 monthName月，编译不过。
        val dayText: String get() =
            if (day == 1) "${monthName}月" else DAY_NAMES[day - 1]

        val monthName: String get() =
            (if (isLeapMonth) "闰" else "") + MONTH_NAMES[month - 1]

        val fullText: String get() = "${monthName}月${DAY_NAMES[day - 1]}"

        /** 传统节日（只做常见的几个）。 */
        val festival: String?
            get() = when {
                month == 1 && day == 1 && !isLeapMonth -> "春节"
                month == 1 && day == 15 && !isLeapMonth -> "元宵节"
                month == 5 && day == 5 && !isLeapMonth -> "端午节"
                month == 7 && day == 7 && !isLeapMonth -> "七夕"
                month == 8 && day == 15 && !isLeapMonth -> "中秋节"
                month == 9 && day == 9 && !isLeapMonth -> "重阳节"
                month == 12 && day == 8 && !isLeapMonth -> "腊八"
                month == 12 && day == 29 && !isLeapMonth -> "除夕"
                else -> null
            }
    }

    /** 公历转农历。超出 1900–2100 返回 null。 */
    fun fromGregorian(year: Int, month: Int, day: Int): LunarDate? {
        if (year < BASE_YEAR || year - BASE_YEAR >= LUNAR_INFO.size) return null

        // 以 1900-01-31（农历 1900 年正月初一）为基准累加天数
        var offset = daysBetween(1900, 1, 31, year, month, day)
        if (offset < 0) return null

        var lunarYear = BASE_YEAR
        while (lunarYear - BASE_YEAR < LUNAR_INFO.size) {
            val days = yearDays(lunarYear)
            if (offset < days) break
            offset -= days
            lunarYear++
        }

        // 逐月扣天数。三个容易写错的点，都是被单元测试逮出来的：
        //  1. `month++` 必须在扣完天数之后（之前放前面，整体多了 1 个月）；
        //  2. 出循环时 offset ≤ 0，落到的是**上一个月**，要把天数和月份都退回去；
        //  3. 日期是 `offset + 1`，不是 `offset + days`。
        val leap = leapMonth(lunarYear)
        var index = 1
        var dayCount = 0
        var isLeap = false
        var remaining = offset

        while (index < 13 && remaining > 0) {
            if (leap > 0 && index == leap + 1 && !isLeap) {
                // 走到闰月：月份号回退一格，改扣闰月天数
                index--
                isLeap = true
                dayCount = leapMonthDays(lunarYear)
            } else {
                dayCount = monthDays(lunarYear, index)
            }
            if (isLeap && index == leap + 1) isLeap = false

            remaining -= dayCount
            index++
        }

        if (remaining < 0) {
            remaining += dayCount
            index--
        }

        return LunarDate(
            year = lunarYear,
            month = index.coerceIn(1, 12),
            day = (remaining + 1).toInt().coerceIn(1, 30),
            isLeapMonth = isLeap
        )
    }

    /** 用儒略日算两天的间隔，避开 Calendar 的时区/夏令时坑。 */
    private fun daysBetween(y1: Int, m1: Int, d1: Int, y2: Int, m2: Int, d2: Int): Int =
        (julianDay(y2, m2, d2) - julianDay(y1, m1, d1)).toInt()

    private fun julianDay(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) {
            y--
            m += 12
        }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    /** 从 Calendar 取公历日期再转农历。 */
    fun fromCalendar(calendar: Calendar): LunarDate? =
        fromGregorian(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
}

/** 把一整天的天文信息打包，供界面一次取用。 */
data class SkyInfo(
    val moon: MoonInfo,
    val lunar: LunarCalendar.LunarDate?,
    val sunrise: Long?,
    val sunset: Long?
)

private fun Double.mod(m: Double): Double {
    val r = this % m
    return if (r < 0) r + m else r
}
