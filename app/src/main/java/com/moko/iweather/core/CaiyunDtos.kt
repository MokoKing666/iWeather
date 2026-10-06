package com.moko.iweather.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 彩云天气 v2.6 综合接口 `/weather` 的响应。
 *
 * 本文件里的字段名与量纲都是**照着真实响应逐个核对过的**，几个容易踩的点：
 *  - URL 路径是 `{经度},{纬度}`（lon 在前）；
 *  - 逐时数组的时间字段叫 `datetime`（不是 `date`），逐天数组叫 `date`；
 *  - 时间格式是 `2026-10-06T12:00+08:00`（分钟精度、带时区偏移、无秒）；
 *  - `daily.astro[].sunrise.time` 只有 `07:13` 这种"时间"，日期要自己拼；
 *  - `humidity`、`cloudrate` 是 0~1 小数；`pressure` 是 Pa；`windspeed` 是 m/s；
 *  - `hourly`/`daily` 是"按变量分数组"，每项自带时间，必须按时间对齐，不能按下标 zip；
 *  - 免费账号拿不到 `minutely` / `alert` / `hourly_summary`，这些字段可能整个不存在。
 */
@Serializable
data class CaiyunResponse(
    val status: String = "ok",
    val error: String? = null,
    val timezone: String? = null,
    @SerialName("tzshift") val tzShiftSeconds: Int? = null,
    val result: CaiyunResult? = null
)

/** 业务错误：彩云把它放在 HTTP 200 的响应体里。 */
@Serializable
data class CaiyunErrorResponse(
    val status: String = "ok",
    val error: String? = null
)

@Serializable
data class CaiyunResult(
    val realtime: CaiyunRealtime? = null,
    val hourly: CaiyunHourly? = null,
    val daily: CaiyunDaily? = null,
    /** 免费账号拿不到；企业套餐/开通预警后返回。 */
    val alert: CaiyunAlert? = null,
    @SerialName("forecast_keypoint") val forecastKeypoint: String? = null
)

// ------------------------------------------------------------------ 实况

@Serializable
data class CaiyunRealtime(
    val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    val skycon: String? = null,
    /** 0~1 */
    val humidity: Double? = null,
    /** 单位 Pa */
    val pressure: Double? = null,
    /** 单位 km */
    val visibility: Double? = null,
    /** 0~1 */
    val cloudrate: Double? = null,
    /** 下行短波辐射 W/m² */
    val dswrf: Double? = null,
    val wind: CaiyunWind? = null,
    val precipitation: CaiyunRealtimePrecipitation? = null,
    @SerialName("air_quality") val airQuality: CaiyunAirQuality? = null,
    @SerialName("life_index") val lifeIndex: CaiyunLifeIndex? = null
)

/** `speed` 单位 m/s，`direction` 是「来向」角度（0 = 正北，顺时针）。 */
@Serializable
data class CaiyunWind(
    val speed: Double? = null,
    val direction: Double? = null
)

@Serializable
data class CaiyunRealtimePrecipitation(
    val local: CaiyunPrecipitationPoint? = null,
    val nearest: CaiyunPrecipitationPoint? = null
)

/** `intensity` 0~1 相对强度。 */
@Serializable
data class CaiyunPrecipitationPoint(
    val status: String? = null,
    val datasource: String? = null,
    val intensity: Double? = null,
    val distance: Double? = null
)

@Serializable
data class CaiyunAirQuality(
    val pm25: Double? = null,
    val pm10: Double? = null,
    val o3: Double? = null,
    val so2: Double? = null,
    val no2: Double? = null,
    val co: Double? = null,
    /** 国标 AQI 取 `chn` */
    val aqi: CaiyunAqi? = null,
    val description: CaiyunAqiDescription? = null
)

@Serializable
data class CaiyunAqi(val chn: Double? = null, val usa: Double? = null)

@Serializable
data class CaiyunAqiDescription(val chn: String? = null, val usa: String? = null)

@Serializable
data class CaiyunLifeIndex(
    val ultraviolet: CaiyunLifeValue? = null,
    val comfort: CaiyunLifeValue? = null
)

@Serializable
data class CaiyunLifeValue(
    val index: Double? = null,
    val desc: String? = null
)

// ------------------------------------------------------------------ 逐时

@Serializable
data class CaiyunSeriesItem<T>(
    val datetime: String = "",
    val value: T
)

@Serializable
data class CaiyunPrecipitationItem(
    val datetime: String = "",
    /** 降水强度 0~1 */
    val value: Double? = null,
    /** 降水概率；彩云在不同版本里用过 0~1 与 0~100 两种，映射层会归一化 */
    val probability: Double? = null
)

@Serializable
data class CaiyunWindItem(
    val datetime: String = "",
    val speed: Double? = null,
    val direction: Double? = null
)

@Serializable
data class CaiyunHourly(
    val status: String? = null,
    /** 自然语言摘要，等价于 forecast_keypoint */
    val description: String? = null,
    val temperature: List<CaiyunSeriesItem<Double>> = emptyList(),
    val skycon: List<CaiyunSeriesItem<String>> = emptyList(),
    val precipitation: List<CaiyunPrecipitationItem> = emptyList(),
    val wind: List<CaiyunWindItem> = emptyList()
)

// ------------------------------------------------------------------ 逐天

@Serializable
data class CaiyunTemperatureItem(
    val date: String = "",
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null
)

/** 逐天 `skycon[].value` 是一个字符串（白天现象）。 */
@Serializable
data class CaiyunSkyconItem(
    val date: String = "",
    val value: String? = null
)

@Serializable
data class CaiyunDailyPrecipitationItem(
    val date: String = "",
    val max: Double? = null,
    val min: Double? = null,
    val avg: Double? = null,
    val probability: Double? = null
)

/** 日出日落只有 `HH:mm`，日期要由 `date` 字段提供。 */
@Serializable
data class CaiyunAstro(
    val date: String = "",
    val sunrise: CaiyunAstroTime? = null,
    val sunset: CaiyunAstroTime? = null
)

@Serializable
data class CaiyunAstroTime(val time: String? = null)

/** 逐天生活指数的每一项（按天展开）。 */
@Serializable
data class CaiyunLifeIndexItem(
    val date: String = "",
    val index: String? = null,
    val desc: String? = null
)

@Serializable
data class CaiyunDailyLifeIndex(
    val ultraviolet: List<CaiyunLifeIndexItem> = emptyList(),
    val dressing: List<CaiyunLifeIndexItem> = emptyList(),
    val comfort: List<CaiyunLifeIndexItem> = emptyList(),
    @SerialName("carWashing") val carWashing: List<CaiyunLifeIndexItem> = emptyList()
)

@Serializable
data class CaiyunDaily(
    val status: String? = null,
    val temperature: List<CaiyunTemperatureItem> = emptyList(),
    val skycon: List<CaiyunSkyconItem> = emptyList(),
    val precipitation: List<CaiyunDailyPrecipitationItem> = emptyList(),
    val astro: List<CaiyunAstro> = emptyList(),
    @SerialName("life_index") val lifeIndex: CaiyunDailyLifeIndex? = null
)

// ------------------------------------------------------------------ 预警

/** 免费账号拿不到；保留结构，企业套餐/后续开通时直接生效。 */
@Serializable
data class CaiyunAlert(
    val status: String? = null,
    val content: List<CaiyunAlertContent> = emptyList()
)

@Serializable
data class CaiyunAlertContent(
    val title: String? = null,
    val type: String? = null,
    /** v2.6 返回中文等级（蓝色/黄色/…），旧文档写的是英文 */
    val level: String? = null,
    val detail: String? = null,
    val effective: String? = null,
    val expire: String? = null,
    val sender: String? = null
)
