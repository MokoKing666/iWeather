package com.moko.iweather.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 中国气象局全国站点气象图。
 *
 * 接口：`https://weather.cma.cn/api/map/weather/1`
 *
 * 这是目前能找到的**最适合画图的国内数据源**：
 *  - 一次请求返回 **2440 个国家级观测站**的实时数据（约 310 KB，1 个请求）；
 *  - 坐标、气温、天气现象、风向风力都有，且**不需要 Key**；
 *  - 实测裸 User-Agent 即可访问，不需要 Referer 或 Cookie。
 *
 * 对比之前的方案：
 *  - RainViewer 雷达瓦片：数据源是 NOAA / OPERA，**中国没有覆盖**，国内看是全透明的；
 *  - 彩云网格采样：免费额度约 20 次/分钟，25 个点会直接 429（实测）；
 *  - 本接口：1 个请求拿到 2440 个点，温度场有真实空间分布（实测 4℃~33℃）。
 *
 * 返回的 `city` 是**混合类型数组**，没有字段名，只能按下标读。各列含义已用
 * `api/now/54511`（北京）逐列比对确认：
 *
 * ```
 * [0] 站号   [1] 城市名  [2] 国家   [3] 保留
 * [4] 纬度   [5] 经度    [6] 气温(℃)
 * [7] 白天天气现象  [8] 白天现象代码  [9] 白天风向  [10] 白天风力  [11] 保留
 * [12] 夜间现象  [13] 夜间代码  [14] 夜间风向  [15] 夜间风力
 * [16] 城市拼音缩写  [17] 行政区划码
 * ```
 */
@Serializable
private data class CmaMapResponse(
    val code: Int = -1,
    val msg: String = "",
    val data: CmaMapData? = null
)

@Serializable
private data class CmaMapData(
    @SerialName("lastUpdate") val lastUpdate: String = "",
    val date: String = "",
    /** 每行是一个混合类型数组，用 JsonArray 接住再按下标读 */
    val city: List<JsonArray> = emptyList()
)

/** 一个观测站。气温/降水/风三张图共用它。 */
data class CmaStation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    override val temperature: Double?,
    val dayText: String,
    val nightText: String,
    val dayWindDirection: String,
    val dayWindScale: String,
    val nightWindDirection: String,
    val nightWindScale: String
) : WeatherMetrics {

    /** 由天气现象文字反推降水强度 0~1（气象局全国图不含定量降水）。 */
    override val precipitationIntensity: Double?
        get() = PrecipitationScale.intensityOf(dayText, nightText)

    /** 全国站点图不含空气质量，AQI 图层走彩云逐点查询。 */
    override val aqi: Double? get() = null

    /** 风力等级换算成风速（m/s）。 */
    override val windSpeedMs: Double?
        get() = BeaufortScale.speedOf(dayWindScale, nightWindScale)

    /** 风向文字换算成角度。 */
    val windDirectionDeg: Double?
        get() = BeaufortScale.directionOf(dayWindDirection, nightWindDirection)
}

/** 风力等级 ⇄ 风速。 */
object BeaufortScale {

    /** 蒲福风级中值风速（m/s），下标即风级。 */
    private val midSpeed = listOf(
        0.1, 0.9, 2.45, 4.4, 6.7, 9.35, 12.3, 15.5, 18.95, 22.6, 26.45, 30.55, 35.0
    )

    /**
     * 解析气象局的风力文字。
     * 实测取值形如：`微风`、`3-4级`、`4-5级`、`5-6级`、`6-7级`、`12级以上`。
     */
    fun levelOf(dayScale: String, nightScale: String): Double {
        val day = parseLevel(dayScale)
        val night = parseLevel(nightScale)
        return maxOf(day, night)
    }

    private fun parseLevel(text: String): Double {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Double.NaN
        if (trimmed.contains("微风")) return 2.0
        // 「3-4级」取中间值 3.5
        val numbers = Regex("""\d+""").findAll(trimmed).map { it.value.toDouble() }.toList()
        return when (numbers.size) {
            0 -> Double.NaN
            1 -> numbers[0]
            else -> (numbers[0] + numbers[1]) / 2.0
        }
    }

    fun speedOf(dayScale: String, nightScale: String): Double? {
        val level = levelOf(dayScale, nightScale)
        if (level.isNaN() || level < 0) return null
        return speedAtLevel(level)
    }

    /**
     * 按风级线性插值取风速。
     *
     * 不能四舍五入到整数级：「3-4级」表示 3 到 4 级，代表值是两级风速的中点（约 5.5 m/s），
     * 直接 round 成 4 级会得到 6.7 m/s，偏大一档。
     */
    private fun speedAtLevel(level: Double): Double {
        val clamped = level.coerceIn(0.0, midSpeed.lastIndex.toDouble())
        val low = kotlin.math.floor(clamped).toInt()
        val high = kotlin.math.ceil(clamped).toInt()
        if (low == high) return midSpeed[low]
        return midSpeed[low] + (clamped - low) * (midSpeed[high] - midSpeed[low])
    }

    /** `北风` → 0°，`东北风` → 45°，依此类推。 */
    fun directionOf(dayDirection: String, nightDirection: String): Double? {
        val text = dayDirection.trim().ifEmpty { nightDirection.trim() }
        if (text.isEmpty()) return null
        // 顺序必须是「先长后短」：用 contains 匹配时，
        // "东北风" 是包含子串 "北风" 的，三字方位必须排在二字之前，
        // 否则 45° 会被判成 0°。原来的表把「北风」写在最前面，
        // 注释却写着"避免被抢先命中" —— 代码和注释是反的，已改。
        val table = listOf(
            "东北风" to 45.0, "东南风" to 135.0, "西南风" to 225.0, "西北风" to 315.0,
            "北风" to 0.0, "东风" to 90.0, "南风" to 180.0, "西风" to 270.0
        )
        return table.firstOrNull { text.contains(it.first) }?.second
    }
}

/** 天气现象文字 → 降水强度 0~1。 */
object PrecipitationScale {

    /**
     * 关键词 → 强度，取第一个命中的。
     *
     * **顺序很重要**：必须"具体词在泛化词前面"。
     * 之前把 `雨`(0.30) 排在 `小雨`(0.22) 前面，
     * 结果「多云转小雨」先命中 `雨`，小雨被算成了中雨量级 —— 这是被测试逮出来的。
     */
    private val table = listOf(
        "特大暴雨" to 1.0, "大暴雨" to 1.0, "暴雨" to 1.0, "暴雪" to 0.92,
        "冰雹" to 0.85, "大雨" to 0.80, "大雪" to 0.75,
        "中雨" to 0.50, "中雪" to 0.45, "雷阵雨" to 0.45, "阵雨" to 0.40,
        "雨夹雪" to 0.35, "冻雨" to 0.35,
        "毛毛雨" to 0.18, "小雨" to 0.22, "小雪" to 0.20,
        // 泛化词放最后兜底
        "雨" to 0.30, "雪" to 0.30
    )

    fun intensityOf(dayText: String, nightText: String): Double {
        val day = intensity(dayText)
        val night = intensity(nightText)
        return maxOf(day, night)
    }

    private fun intensity(text: String): Double {
        if (text.isBlank()) return 0.0
        // 「多云转小雨」这类要按关键词命中
        return table.firstOrNull { text.contains(it.first) }?.second ?: 0.0
    }
}

class CmaMapClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * 拉取全国站点实时气象。
     * @return 站点列表与数据更新时间；失败返回 null。
     */
    suspend fun fetchStations(): CmaStationSnapshot? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://weather.cma.cn/api/map/weather/1")
                .header("User-Agent", "com.moko.iweather/27")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                val parsed = json.decodeFromString<CmaMapResponse>(body)
                if (parsed.code != 0) return@use null

                val stations = parsed.data?.city.orEmpty().mapNotNull { row -> row.toStation() }
                if (stations.isEmpty()) return@use null

                CmaStationSnapshot(
                    stations = stations,
                    lastUpdate = parsed.data?.lastUpdate.orEmpty()
                )
            }
        }.getOrNull()
    }

    /**
     * 只做解析、不发请求。抽出来是为了能用真实响应样例做回归测试 ——
     * 那些列的含义是拿北京站逐列比对出来的，没有字段名可依赖，改错一位不会报错只会画错图。
     */
    internal fun parseStations(body: String): List<CmaStation> {
        val parsed = json.decodeFromString<CmaMapResponse>(body)
        return parsed.data?.city.orEmpty().mapNotNull { row -> row.toStation() }
    }

    private fun JsonArray.toStation(): CmaStation? {
        // 至少要有到风力那一列
        if (size < 16) return null
        val latitude = getOrNull(4)?.jsonPrimitive?.doubleOrNull ?: return null
        val longitude = getOrNull(5)?.jsonPrimitive?.doubleOrNull ?: return null
        val name = getOrNull(1)?.jsonPrimitive?.contentOrEmpty() ?: return null
        if (name.isBlank()) return null

        return CmaStation(
            id = getOrNull(0)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            name = name,
            latitude = latitude,
            longitude = longitude,
            temperature = getOrNull(6)?.jsonPrimitive?.doubleOrNull,
            dayText = getOrNull(7)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            nightText = getOrNull(12)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            dayWindDirection = getOrNull(9)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            dayWindScale = getOrNull(10)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            nightWindDirection = getOrNull(14)?.jsonPrimitive?.contentOrEmpty().orEmpty(),
            nightWindScale = getOrNull(15)?.jsonPrimitive?.contentOrEmpty().orEmpty()
        )
    }

    private fun kotlinx.serialization.json.JsonPrimitive.contentOrEmpty(): String =
        runCatching { content }.getOrDefault("")
}

/** 一次全国站点快照。 */
data class CmaStationSnapshot(
    val stations: List<CmaStation>,
    /** 气象局给出的数据时间，形如 `2026/10/06 12:00`。 */
    val lastUpdate: String
)
