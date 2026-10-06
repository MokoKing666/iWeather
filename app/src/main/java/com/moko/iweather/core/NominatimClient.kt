package com.moko.iweather.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Nominatim（OpenStreetMap）逆地理编码 —— **不需要 API Key**。
 *
 * 为什么不用 Android 自带的 `Geocoder`：
 *  - 国内不少机型（无 GMS、或 ROM 自带 geocoder 数据源）返回的 `locality` 往往就是地级市，
 *    `subAdminArea` 常年为空，导致"定位只到市、到不了区县"；
 *  - Nominatim 的地址层级里 `city_district` / `county` / `suburb` 在中国通常能给出
 *    "海淀区""朝阳区""昆山市"这一级结果。
 *
 * 使用规范（Nominatim 使用条款）：
 *  - 必须带可识别的 User-Agent；
 *  - 限速 1 请求/秒 —— 这里在 [PlaceResolver] 里串行调用。
 */
@Serializable
private data class NominatimResponse(
    val address: NominatimAddress? = null
)

@Serializable
private data class NominatimAddress(
    val country: String? = null,
    @SerialName("country_code") val countryCode: String? = null,
    val state: String? = null,
    val city: String? = null,
    val county: String? = null,
    @SerialName("city_district") val cityDistrict: String? = null,
    val suburb: String? = null,
    val town: String? = null,
    val village: String? = null,
    val neighbourhood: String? = null
)

data class NominatimPlace(
    /** 最细一级（区/县），已按优先级挑好 */
    val name: String,
    val city: String?,
    val state: String?,
    val country: String?,
    val countryCode: String?
)

class NominatimClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 坐标 → 地址。失败返回 null，由调用方降级到系统 Geocoder。 */
    suspend fun reverse(latitude: Double, longitude: Double, language: String = "zh-CN"): NominatimPlace? =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "https://nominatim.openstreetmap.org/reverse" +
                    "?format=jsonv2&zoom=12&addressdetails=1" +
                    "&lat=$latitude&lon=$longitude&accept-language=$language"
                val request = Request.Builder()
                    .url(url)
                    // Nominatim 使用条款要求带可联系的应用标识
                    .header("User-Agent", "com.moko.iweather/27 (Android weather app)")
                    .header("Accept-Language", language)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val parsed = json.decodeFromString<NominatimResponse>(body)
                    parsed.address?.toPlace()
                }
            }.getOrNull()
        }

    /** 按"市 → 区/县 → 省"挑一个最细的名字。 */
    private fun NominatimAddress.toPlace(): NominatimPlace? {
        val cleanCity = city?.takeIf { it.isNotBlank() }
        val cleanState = state?.takeIf { it.isNotBlank() }

        val district = sequenceOf(cityDistrict, county, suburb, town, village)
            .mapNotNull { it?.trim() }
            .firstOrNull { candidate ->
                candidate.isNotEmpty() &&
                    candidate != cleanCity &&
                    candidate != cleanState &&
                    candidate != country
            }

        val name = district ?: cleanCity ?: cleanState ?: country ?: return null
        return NominatimPlace(
            name = name,
            city = cleanCity,
            state = cleanState,
            country = country,
            countryCode = countryCode?.uppercase()
        )
    }
}
