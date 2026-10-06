package com.moko.iweather.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 用**真实保存下来的彩云响应**回归验证映射层。
 *
 * 之前就是靠它发现了一批字段名/量纲错误（humidity 是 0~1、pressure 是 Pa、
 * 逐时时间字段叫 datetime、astro 只给 HH:mm 等），所以固化成测试。
 *
 * 把响应放到 `app/src/test/resources/fixture_weather.json` 即可自动跑；
 * 文件不存在时跳过，不影响别人的构建。
 */
class MappingSelfCheckTest {

    private fun loadSnapshot(): Pair<WeatherSnapshot, WeatherCity>? {
        val file = File("src/test/resources/fixture_weather.json")
        assumeTrue("未提供 fixture 文件，跳过", file.exists())
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            isLenient = true
        }
        val response = json.decodeFromString<CaiyunResponse>(file.readText())
        val city = WeatherCity(
            id = WeatherCity.makeId(39.2072, 101.6656),
            name = "测试城市",
            latitude = 39.2072,
            longitude = 101.6656
        )
        return SnapshotMapper.map(response, city) to city
    }

    @Test
    fun `实况字段与量纲正确`() {
        val (snapshot, _) = loadSnapshot() ?: return

        assertNotNull("温度", snapshot.realtime.temperature)
        assertNotNull("天气现象", snapshot.realtime.skycon.raw)

        // 湿度是 0~1，不是百分数
        snapshot.realtime.humidity?.let {
            assertTrue("湿度应在 0~1，实际 $it", it in 0.0..1.0)
        }
        snapshot.realtime.cloudRate?.let {
            assertTrue("云量应在 0~1，实际 $it", it in 0.0..1.0)
        }
        // 气压彩云给 Pa，映射层已换算成 hPa
        snapshot.realtime.pressureHpa?.let {
            assertTrue("气压应在 600~1100 hPa，实际 $it", it in 600.0..1100.0)
        }
        snapshot.realtime.windDirectionDeg?.let {
            assertTrue("风向应在 0~360，实际 $it", it in 0.0..360.0)
        }
    }

    @Test
    fun `逐时按时间对齐且非空`() {
        val (snapshot, _) = loadSnapshot() ?: return
        assertTrue("逐时不应为空", snapshot.hourly.isNotEmpty())

        val times = snapshot.hourly.map { it.time }
        assertEquals("逐时应按时间升序", times.sorted(), times)
        assertEquals("逐时不应有重复时间", times.distinct().size, times.size)
        assertTrue("逐时温度不应全为空", snapshot.hourly.any { it.temperature != null })
    }

    @Test
    fun `逐天带日出日落与生活指数`() {
        val (snapshot, _) = loadSnapshot() ?: return
        assertTrue("逐天不应为空", snapshot.daily.isNotEmpty())

        snapshot.daily.forEach { day ->
            assertNotNull("缺最高温", day.temperatureMax)
            assertNotNull("缺最低温", day.temperatureMin)
            assertNotNull("缺日出", day.sunrise)
            assertNotNull("缺日落", day.sunset)
            assertNotNull("缺天气现象", day.skyconDay.raw)
        }
    }

    @Test
    fun `日出日落落在当天且时间合理`() {
        val (snapshot, _) = loadSnapshot() ?: return
        val zone = TimeZoneResolver.zone(snapshot.timeZoneId)
        snapshot.daily.forEach { day ->
            val calendar = java.util.Calendar.getInstance(zone)
            val rise = calendar.apply { timeInMillis = day.sunrise!! }
            val set = java.util.Calendar.getInstance(zone).apply { timeInMillis = day.sunset!! }
            assertEquals("日出与日落应同一天", rise.get(java.util.Calendar.DAY_OF_YEAR), set.get(java.util.Calendar.DAY_OF_YEAR))
            assertTrue(
                "日出应在 02:00~10:00，实际 ${rise.get(java.util.Calendar.HOUR_OF_DAY)}",
                rise.get(java.util.Calendar.HOUR_OF_DAY) in 2..10
            )
            assertTrue(
                "日落应在 14:00~23:00，实际 ${set.get(java.util.Calendar.HOUR_OF_DAY)}",
                set.get(java.util.Calendar.HOUR_OF_DAY) in 14..23
            )
        }
    }

    @Test
    fun `空气质量取国标值与中文等级`() {
        val (snapshot, _) = loadSnapshot() ?: return
        val air = snapshot.airQuality
        assertNotNull("AQI 不应为空", air.aqi)
        assertNotNull("AQI 等级文案不应为空", air.category)
        assertNotNull("应能算出 AQI 等级", air.level)
    }

    @Test
    fun `降水概率归一化到 0 到 1`() {
        listOf(0.0, 0.35, 1.0, 35.0, 100.0).forEach { raw ->
            val normalized = SnapshotMapper.normalizeProbability(raw)!!
            assertTrue("原始 $raw 归一化后应在 0~1，实际 $normalized", normalized in 0.0..1.0)
        }
        assertEquals(null, SnapshotMapper.normalizeProbability(null))
    }

    @Test
    fun `时区优先用彩云返回的 IANA 名`() {
        val (snapshot, _) = loadSnapshot() ?: return
        assertTrue(
            "应解析出可用的时区，实际 ${snapshot.timeZoneId}",
            TimeZoneResolver.isValidId(snapshot.timeZoneId)
        )
    }
}
