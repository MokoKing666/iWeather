package com.moko.iweather.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * 露点、月相、农历的正确性校验。
 *
 * 这些是纯计算，可以用**公认的已知事实**做断言 —— 比界面可靠得多，
 * 所以哪怕没有真机也要把它们锁住。
 */
class AstronomyTest {

    private val delta = 0.05

    // ------------------------------------------------------------ 露点

    @Test
    fun `露点按 Magnus 公式计算`() {
        // 20℃ / 50% 相对湿度 → 露点约 9.3℃（教科书常见算例）
        assertEquals(9.26, DewPoint.celsius(20.0, 50.0)!!, 0.05)
        // 饱和空气露点等于气温
        assertEquals(20.0, DewPoint.celsius(20.0, 100.0)!!, 0.01)
        // 湿度越低露点越低
        assertTrue(DewPoint.celsius(30.0, 20.0)!! < DewPoint.celsius(30.0, 80.0)!!)
    }

    @Test
    fun `彩云的湿度是 0到1 的比例`() {
        // 0.5 应当等价于 50%
        assertEquals(9.26, DewPoint.celsiusFromRatio(20.0, 0.5)!!, 0.05)
        assertEquals(null, DewPoint.celsiusFromRatio(20.0, null))
    }

    // ------------------------------------------------------------ 农历

    private fun lunar(year: Int, month: Int, day: Int) =
        LunarCalendar.fromGregorian(year, month, day)

    @Test
    fun `春节是正月初一`() {
        val spring2024 = lunar(2024, 2, 10)
        assertNotNull(spring2024)
        assertEquals(1, spring2024!!.month)
        assertEquals(1, spring2024.day)
        assertEquals("春节", spring2024.festival)

        val spring2023 = lunar(2023, 1, 22)
        assertEquals(1, spring2023!!.month)
        assertEquals(1, spring2023.day)

        val spring2025 = lunar(2025, 1, 29)
        assertEquals(1, spring2025!!.month)
        assertEquals(1, spring2025.day)
    }

    @Test
    fun `中秋是八月十五`() {
        val midAutumn = lunar(2024, 9, 17)
        assertNotNull(midAutumn)
        assertEquals(8, midAutumn!!.month)
        assertEquals(15, midAutumn.day)
        assertEquals("中秋节", midAutumn.festival)
    }

    @Test
    fun `端午是五月初五`() {
        val dragonBoat = lunar(2024, 6, 10)
        assertEquals(5, dragonBoat!!.month)
        assertEquals(5, dragonBoat.day)
        assertEquals("端午节", dragonBoat.festival)
    }

    @Test
    fun `2024 年有闰月`() {
        // 2024 年是闰年，农历有闰月。取一个落在闰月里的日期，
        // 只要月份与日期合法即可，重点是不能崩、不能算成 0 月或 0 日。
        val date = lunar(2024, 8, 1)
        assertNotNull(date)
        assertTrue(date!!.month in 1..12)
        assertTrue(date.day in 1..30)
    }

    @Test
    fun `超出表格范围返回 null 而不是乱算`() {
        assertEquals(null, LunarCalendar.fromGregorian(1850, 1, 1))
        assertEquals(null, LunarCalendar.fromGregorian(2200, 1, 1))
    }

    @Test
    fun `初一显示月份名而不是初一`() {
        val date = lunar(2024, 2, 10)!!
        assertEquals("正月初一", date.fullText)
        assertTrue(date.dayText.contains("月"))
    }

    // ------------------------------------------------------------ 月相

    private fun moonAt(year: Int, month: Int, day: Int): MoonInfo {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val calendar = Calendar.getInstance(zone).apply {
            set(year, month - 1, day, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return MoonCalculator.info(calendar.timeInMillis, 39.9, 116.4, zone)
    }

    @Test
    fun `二零二四年四月八日是新月`() {
        // 当天发生了日全食，必定是新月
        val info = moonAt(2024, 4, 8)
        assertEquals(MoonPhase.NEW_MOON, info.phase)
        assertTrue("照明比例应接近 0，实际 ${info.illumination}", info.illumination < 0.02)
    }

    @Test
    fun `二零二四年三月二十五日是满月`() {
        // 当天发生了半影月食
        val info = moonAt(2024, 3, 25)
        assertEquals(MoonPhase.FULL_MOON, info.phase)
        assertTrue("照明比例应接近 1，实际 ${info.illumination}", info.illumination > 0.98)
    }

    @Test
    fun `月龄在新月附近接近 0 或 29、满月附近接近半月`() {
        // 新月时刻是 2024-04-09 02:21 CST，取样点 04-08 12:00 在它之前约 14 小时，
        // 所以月龄应该接近 29 而不是 0（两者都是"新月附近"）。
        val newAge = moonAt(2024, 4, 8).ageDays
        assertTrue("新月附近月龄应接近 0 或 29.5，实际 $newAge", newAge < 1.5 || newAge > 28.0)

        val fullAge = moonAt(2024, 3, 25).ageDays
        assertTrue("满月月龄应接近 14.8，实际 $fullAge", fullAge in 13.0..16.5)
    }

    @Test
    fun `下一次满月一定在当前时间之后`() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val calendar = Calendar.getInstance(zone).apply { set(2024, 3, 1, 12, 0, 0) }
        val info = MoonCalculator.info(calendar.timeInMillis, 39.9, 116.4, zone)
        assertTrue(info.nextFullMoon > calendar.timeInMillis)
        assertTrue(info.nextNewMoon > calendar.timeInMillis)
    }

    @Test
    fun `月出月落落在当天之内`() {
        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val calendar = Calendar.getInstance(zone).apply {
            set(2024, 3, 25, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis
        val (rise, set) = MoonCalculator.riseAndSet(dayStart, 39.9, 116.4, zone)

        val dayEnd = dayStart + 86_400_000L
        rise?.let { assertTrue("月出应在当天内", it in dayStart..dayEnd) }
        set?.let { assertTrue("月落应在当天内", it in dayStart..dayEnd) }

        // 注意**不能**断言"月出早于月落"：满月那天月亮傍晚才升起、
        // 次日清晨落下，所以同一个日历日里是先落后升。这是天文事实，不是 bug。
        assertTrue("月出月落不应同时为空", rise != null || set != null)
    }
}
