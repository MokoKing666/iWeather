package com.moko.iweather.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 气象局全国站点图的解析回归测试。
 *
 * 响应里的 `city` 是**没有字段名的混合类型数组**，只能按下标读，
 * 而下标含义是拿 `api/now/54511`（北京）逐列比对出来的。
 * 一旦有人改了 [CmaMapClient] 里的下标，编译不会报错、只会画错图，
 * 所以这里用真实响应样例把语义锁住。
 */
class CmaMapParsingTest {

    /** 直接从接口拷回来的真实片段（北京、万盛、大安），外加一行残缺数据。 */
    private val sample = """
        {
          "msg": "success",
          "code": 0,
          "data": {
            "lastUpdate": "2026/10/06 12:00",
            "date": "2026/10/06",
            "city": [
              ["54511","北京","中国",0,39.81,116.47,24.0,"晴",0,"北风","微风",11.0,"晴",0,"西南风","微风","ABJ","110115"],
              ["57509","万盛","中国",3,28.99,106.92,"23.0","多云",1,"西北风","微风",15.0,"多云",1,"东风","微风","ACQ","500000"],
              ["50945","大安","中国",3,45.51,124.24,18.0,"小雨",0,"西北风","3-4级",4.0,"中雨",0,"北风","4-5级","AJL","220882"],
              ["00000","坏数据","中国",0,39.0,116.0]
            ]
          }
        }
    """.trimIndent()

    private val delta = 1e-6

    @Test
    fun `列下标解析正确`() {
        val stations = CmaMapClient().parseStations(sample)

        // 最后一行只有 5 列，应该被丢掉而不是崩掉
        assertEquals(3, stations.size)

        val beijing = stations.first { it.name == "北京" }
        assertEquals("54511", beijing.id)
        // 纬度在前、经度在后 —— 和彩云 URL 的 lon,lat 顺序相反，特别容易写反
        assertEquals(39.81, beijing.latitude, delta)
        assertEquals(116.47, beijing.longitude, delta)
        assertEquals(24.0, beijing.temperature!!, delta)
        assertEquals("晴", beijing.dayText)
        assertEquals("北风", beijing.dayWindDirection)
        assertEquals("微风", beijing.dayWindScale)
        assertEquals("西南风", beijing.nightWindDirection)
    }

    @Test
    fun `温度是字符串时也能解析`() {
        val wanSheng = CmaMapClient().parseStations(sample).first { it.name == "万盛" }
        // 样例里 temperature 故意写成字符串 "23.0"，接口两种形式都出现过
        assertEquals(23.0, wanSheng.temperature!!, delta)
    }

    @Test
    fun `风力文字转风速`() {
        assertEquals(2.45, BeaufortScale.speedOf("微风", "微风")!!, delta)
        // 「3-4级」代表 3 级 (4.4) 与 4 级 (6.7) 的中点 = 5.55，
        // 不能四舍五入到 4 级拿 6.7
        assertEquals(5.55, BeaufortScale.speedOf("3-4级", "微风")!!, delta)
        // 白天夜间取较大的那个：4-5 级 → 6.7 与 9.35 的中点
        assertEquals(8.025, BeaufortScale.speedOf("3-4级", "4-5级")!!, delta)
    }

    @Test
    fun `风向文字转角度`() {
        // 「东北风」必须命中 45°，不能含「北风」就被匹配成 0°
        assertEquals(0.0, BeaufortScale.directionOf("北风", "")!!, delta)
        assertEquals(180.0, BeaufortScale.directionOf("", "南风")!!, delta)
    }

    @Test
    fun `天气现象转降水强度`() {
        assertEquals(0.0, PrecipitationScale.intensityOf("晴", "多云"), delta)
        assertEquals(0.80, PrecipitationScale.intensityOf("大雨", "小雨"), delta)
        assertEquals(0.50, PrecipitationScale.intensityOf("中雨", "阴"), delta)
        // 「多云转小雨」这类组合要能命中关键词
        assertEquals(0.22, PrecipitationScale.intensityOf("多云转小雨", "晴"), delta)
        assertTrue(PrecipitationScale.intensityOf("暴雨", "大雨") > 0.9)
    }

    @Test
    fun `站点实现 WeatherMetrics 后各图层都能取值`() {
        val station = CmaMapClient().parseStations(sample).first { it.name == "大安" }

        assertEquals(18.0, WeatherMapLayer.TEMPERATURE.valueOf(station)!!, delta)
        assertTrue(WeatherMapLayer.PRECIPITATION.valueOf(station)!! > 0.0)
        assertTrue(WeatherMapLayer.WIND.valueOf(station)!! > 0.0)
        // 全国站点图不含空气质量：必须返回 null 而不是 0，
        // 否则 AQI 会被算成 0 并画成"优"，那是错的
        assertNull(WeatherMapLayer.AIR_QUALITY.valueOf(station))
    }
}
