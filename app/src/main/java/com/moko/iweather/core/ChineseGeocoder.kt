package com.moko.iweather.core

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 高德逆地理编码（Web 服务）。
 *
 * 为什么必须用它：
 *  - Android 自带 `Geocoder` 在国内机型上 `subAdminArea` 常年为空，只能给出**地级市**；
 *  - 内置城市库是 2567 条**纯地级市**数据（实测：北京下面一条都没有），离线反查无用；
 *  - Nominatim 虽然免 key，但在国内基本连不上。
 *
 * 高德的逆地理编码能稳定返回**区/县**一级（`addressComponent.district`），
 * 而且走 HTTPS + JSON，不需要引入任何 SDK。
 *
 * 需要一个 Key：在 https://console.amap.com/dev/key/app 申请「Web服务」类型，
 * 然后填进 App 内「设置 → 定位 → 高德地图 Key」（或写入 local.properties 的
 * `caiyun.amapKey`）。
 */
@Serializable
private data class AMapRegeoResponse(
    val status: String? = null,
    val info: String? = null,
    @SerialName("regeocode") val regeocode: AMapRegeocode? = null
)

@Serializable
private data class AMapRegeocode(
    @SerialName("formatted_address") val formattedAddress: String? = null,
    @SerialName("addressComponent") val component: AMapAddressComponent? = null
)

@Serializable
private data class AMapAddressComponent(
    val country: String? = null,
    val province: String? = null,
    val city: String? = null,
    /** 区 / 县 —— 我们要的就是这一级 */
    val district: String? = null,
    val township: String? = null,
    val street: String? = null
)

data class PrecisePlace(
    /** 最细一级（优先 district，其次 township） */
    val name: String,
    val city: String?,
    val province: String?,
    val country: String?,
    val detail: String?
)

class ChineseGeocoder(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    /** 用户填的 Key。优先设置页里填的，其次编译期注入的。 */
    var apiKey: String
        get() = prefs.getString(KEY_AMAP, null)?.takeIf { it.isNotBlank() }
            ?: com.moko.iweather.BuildConfig.AMAP_KEY.takeIf { it.isNotBlank() }
            ?: ""
        set(value) = prefs.edit { putString(KEY_AMAP, value) }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * 逆地理编码。**高德返回空数组时（例如 key 没配额度）自动降级到 Nominatim**，
     * 再兜底系统 Geocoder，调用方不用关心用的是哪一家。
     */
    suspend fun reverse(latitude: Double, longitude: Double): PrecisePlace? =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext null
            runCatching {
                val url = "https://restapi.amap.com/v3/geocode/regeo" +
                    "?key=$apiKey&location=$longitude,$latitude&extensions=base"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "com.moko.iweather/27")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body?.string() ?: return@use null
                    val parsed = json.decodeFromString<AMapRegeoResponse>(body)
                    if (parsed.status != "1") return@use null
                    val c = parsed.regeocode?.component ?: return@use null
                    val district = c.district?.trim()?.takeIf { it.isNotEmpty() }
                    val township = c.township?.trim()?.takeIf { it.isNotEmpty() }
                    val city = c.city?.trim()?.takeIf { it.isNotEmpty() }

                    val name = district ?: township ?: city ?: c.province ?: return@use null
                    PrecisePlace(
                        name = name,
                        city = city?.takeIf { it != name },
                        province = c.province?.takeIf { it != name },
                        country = c.country,
                        detail = parsed.regeocode?.formattedAddress
                    )
                }
            }.getOrNull()
        }

    companion object {
        private const val PREFS = "iweather_amap"
        private const val KEY_AMAP = "amap_key"
    }
}
