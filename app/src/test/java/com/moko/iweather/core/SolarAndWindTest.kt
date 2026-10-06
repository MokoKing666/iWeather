package com.moko.iweather.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 日出日落与风向的回归测试。
 *
 * 这两处都是**编译不会报错、只会算错**的实现，所以必须用已知事实锁住。
 */
class SolarAndWindTest {

    private val beijingLat = 39.90
    private val beijingLon = 116.40

    /** 取某天在指定时区 12:00 的时刻。 */
    private fun noonOf(year: Int, month: Int, day: Int, zoneId: String): Long {
        val zone = TimeZone.getTimeZone(zoneId)
        return Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
    }

    /** 把时刻格式化成当地 HH:mm，方便和公开的日出日落表对照。 */
    private fun hhmm(millis: Long, zoneId: String): String =
        TimeZoneResolver.formatTime(millis, zoneId, "HH:mm")

    // ------------------------------------------------------------ 日出日落

    @Test
    fun `北京夏至日出日落约在 04 46 与 19 47`() {
        val noon = noonOf(2024, 6, 21, "Asia/Shanghai")
        val (sunrise, sunset) = SolarCalculator.eventsForDay(
            noon, beijingLat, beijingLon, "Asia/Shanghai"
        )
        assertNotNull(sunrise)
        assertNotNull(sunset)

        // 天文年历上北京夏至约为 04:46 / 19:47，简化公式允许 ±25 分钟偏差
        assertNear("夏至日出", hhmm(sunrise!!, "Asia/Shanghai"), 4, 46, 25)
        assertNear("夏至日落", hhmm(sunset!!, "Asia/Shanghai"), 19, 47, 25)
    }

    @Test
    fun `北京冬至日出日落约在 07 33 与 16 53`() {
        val noon = noonOf(2024, 12, 21, "Asia/Shanghai")
        val (sunrise, sunset) = SolarCalculator.eventsForDay(
            noon, beijingLat, beijingLon, "Asia/Shanghai"
        )
        assertNotNull(sunrise)
        assertNotNull(sunset)

        assertNear("冬至日出", hhmm(sunrise!!, "Asia/Shanghai"), 7, 33, 25)
        assertNear("冬至日落", hhmm(sunset!!, "Asia/Shanghai"), 16, 53, 25)
    }

    @Test
    fun `夏至白昼明显长于冬至`() {
        fun dayLength(month: Int, day: Int): Long {
            val noon = noonOf(2024, month, day, "Asia/Shanghai")
            val (rise, set) = SolarCalculator.eventsForDay(
                noon, beijingLat, beijingLon, "Asia/Shanghai"
            )
            return set!! - rise!!
        }

        val summer = dayLength(6, 21)
        val winter = dayLength(12, 21)
        // 北京夏至约 15 小时、冬至约 9.3 小时
        assertTrue("夏至白昼应超过 14 小时，实际 ${summer / 3_600_000f} 小时", summer > 14 * 3_600_000L)
        assertTrue("冬至白昼应不足 10 小时，实际 ${winter / 3_600_000f} 小时", winter < 10 * 3_600_000L)
        assertTrue("夏至必须长于冬至", summer > winter + 4 * 3_600_000L)
    }

    /**
     * 这条专门针对修掉的那个 bug。
     *
     * 原来的实现把经度项整个丢了（而且 `Math.toDegrees(longitude / 15.0)` 是双重错误），
     * 结果是**日出日落时刻与经度无关** —— 这个断言会失败。
     */
    @Test
    fun `日出时刻必须随经度变化`() {
        val noon = noonOf(2024, 6, 21, "UTC")

        // 同一纬度、经度相差 30°，在同一个 UTC 时区里比较 UTC 时刻。
        // 东经 30° 比 0° 早约 2 小时看到日出。
        val (zeroRise, _) = SolarCalculator.eventsForDay(noon, beijingLat, 0.0, "UTC")
        val (eastRise, _) = SolarCalculator.eventsForDay(noon, beijingLat, 30.0, "UTC")

        assertNotNull(zeroRise)
        assertNotNull(eastRise)

        val diffHours = (zeroRise!! - eastRise!!) / 3_600_000.0
        assertTrue(
            "经度每差 15° 应差约 1 小时，30° 应差约 2 小时；实际 $diffHours 小时" +
                "（若接近 0 说明经度项又丢了）",
            diffHours in 1.5..2.5
        )
    }

    /**
     * 南半球季节相反。原实现若只有符号错误，这里也会挂。
     */
    @Test
    fun `南半球六月是冬天`() {
        // 悉尼 33.87S, 151.21E
        val june = noonOf(2024, 6, 21, "Australia/Sydney")
        val december = noonOf(2024, 12, 21, "Australia/Sydney")

        fun dayLength(at: Long): Long {
            val (rise, set) = SolarCalculator.eventsForDay(
                at, -33.87, 151.21, "Australia/Sydney"
            )
            return set!! - rise!!
        }

        assertTrue(
            "南半球六月应比十二月白昼短",
            dayLength(june) < dayLength(december)
        )
    }

    // ------------------------------------------------------------ 风向

    @Test
    fun `三字风向不会被二字风向抢先命中`() {
        // 这是修掉的 bug："东北风" 包含子串 "北风"，
        // 原来的表把「北风」排在前面，于是 45° 被判成 0°。
        assertEquals(45.0, BeaufortScale.directionOf("东北风", "")!!, 1e-6)
        assertEquals(135.0, BeaufortScale.directionOf("东南风", "")!!, 1e-6)
        assertEquals(225.0, BeaufortScale.directionOf("西南风", "")!!, 1e-6)
        assertEquals(315.0, BeaufortScale.directionOf("西北风", "")!!, 1e-6)
    }

    @Test
    fun `二字风向仍然正确`() {
        assertEquals(0.0, BeaufortScale.directionOf("北风", "")!!, 1e-6)
        assertEquals(90.0, BeaufortScale.directionOf("东风", "")!!, 1e-6)
        assertEquals(180.0, BeaufortScale.directionOf("南风", "")!!, 1e-6)
        assertEquals(270.0, BeaufortScale.directionOf("西风", "")!!, 1e-6)
    }

    @Test
    fun `白天风向为空时退回夜间风向`() {
        assertEquals(225.0, BeaufortScale.directionOf("", "西南风")!!, 1e-6)
    }

    // ------------------------------------------------------------ 工具

    /** 断言 HH:mm 与预期的时:分相差不超过 tolerance 分钟（允许跨小时）。 */
    private fun assertNear(
        label: String,
        actual: String,
        expectedHour: Int,
        expectedMinute: Int,
        toleranceMinutes: Int
    ) {
        val parts = actual.split(":")
        assertEquals("$label 的时刻格式异常：$actual", 2, parts.size)

        val actualMinutes = parts[0].toInt() * 60 + parts[1].toInt()
        val expectedMinutes = expectedHour * 60 + expectedMinute
        val delta = kotlin.math.abs(actualMinutes - expectedMinutes)

        assertTrue(
            "$label 预期约 $expectedHour:${"%02d".format(expectedMinute)}，" +
                "实际 $actual，相差 $delta 分钟（容差 $toleranceMinutes）",
            delta <= toleranceMinutes
        )
    }
}
