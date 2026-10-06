package com.moko.iweather.core

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** 地理编码结果的统一包装。 */
data class ResolvedPlace(
    val name: String,
    val adminArea: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double
) {
    fun toCity(isCurrentLocation: Boolean): WeatherCity = WeatherCity(
        id = WeatherCity.makeId(latitude, longitude),
        name = name,
        latitude = latitude,
        longitude = longitude,
        adminArea = adminArea,
        country = country,
        countryCode = countryCode,
        timeZoneId = WeatherCity.provisionalTimeZone(longitude),
        isCurrentLocation = isCurrentLocation
    )
}

/**
 * 逆地理编码 + 全球城市搜索。
 *
 * **主路径用 Nominatim（OpenStreetMap，无需 Key）**，因为 Android 自带 `Geocoder`
 * 在国内很多机型上只会返回地级市、`subAdminArea` 常为空，定位永远到不了区县。
 * 系统 `Geocoder` 降级为兜底（离线时仍能给出结果）。
 *
 * 搜索也优先走 Nominatim 的 `search` 接口，失败再退回系统 Geocoder。
 */
class PlaceResolver(private val context: Context) {

    private val appContext = context.applicationContext
    private val geocoder: Geocoder? = runCatching { Geocoder(appContext, Locale.CHINA) }.getOrNull()
    private val nominatim = NominatimClient()
    private val amap = ChineseGeocoder(appContext)

    /** 是否配置了高德 Key（配置后定位才能稳定精确到区/县）。 */
    val hasAmapKey: Boolean get() = amap.isConfigured

    fun setAmapKey(key: String) { amap.apiKey = key }

    // ------------------------------------------------------------ 逆地理编码

    /** 坐标 → 城市（尽量细到区/县）。 */
    suspend fun reverse(latitude: Double, longitude: Double): Result<ResolvedPlace> =
        withContext(Dispatchers.IO) {
            runCatching {
                // 1) 高德：国内直连，能返回 district（区/县）
                amap.reverse(latitude, longitude)?.let { hit ->
                    return@runCatching ResolvedPlace(
                        name = hit.name,
                        adminArea = listOfNotNull(hit.city, hit.province)
                            .filter { it.isNotBlank() && it != hit.name }
                            .distinct().joinToString(" · ").takeIf { it.isNotBlank() },
                        country = hit.country ?: "中国",
                        countryCode = "CN",
                        latitude = latitude,
                        longitude = longitude
                    )
                }

                // 2) Nominatim：免 key，境外网络可用
                val fromNominatim = nominatim.reverse(latitude, longitude)
                if (fromNominatim != null) {
                    return@runCatching ResolvedPlace(
                        name = fromNominatim.name,
                        adminArea = listOfNotNull(
                            fromNominatim.city?.takeIf { it != fromNominatim.name },
                            fromNominatim.state?.takeIf { it != fromNominatim.name }
                        ).distinct().joinToString(" · ").takeIf { it.isNotBlank() },
                        country = fromNominatim.country,
                        countryCode = fromNominatim.countryCode,
                        latitude = latitude,
                        longitude = longitude
                    )
                }

                // 兜底：系统 Geocoder
                val address = geocodeOne(latitude, longitude)
                    ?: return@runCatching fallbackPlace(latitude, longitude)
                val name = bestName(address)
                ResolvedPlace(
                    name = name,
                    adminArea = listOfNotNull(
                        address.locality?.takeIf { it != name },
                        address.adminArea?.takeIf { it != name }
                    ).distinct().joinToString(" · ").takeIf { it.isNotBlank() },
                    country = address.countryName,
                    countryCode = address.countryCode,
                    latitude = latitude,
                    longitude = longitude
                )
            }
        }

    /**
     * 取"最能代表这个点"的名字。
     *
     * 直辖市（北京/上海）常常 `locality == adminArea == 北京市`，
     * 真正有区分度的是 `subAdminArea`（如"朝阳区"），所以它排第一。
     */
    private fun bestName(address: Address): String {
        val city = address.locality?.takeIf { it.isNotBlank() }
        val district = address.subAdminArea?.takeIf { it.isNotBlank() }
        val province = address.adminArea?.takeIf { it.isNotBlank() }
        val feature = address.featureName?.takeIf { it.isNotBlank() }

        return when {
            // 区县有效且与省市不重复 → 用区县（最细）
            district != null && district != city && district != province -> district
            // 区县就是"某某县/某某区"，但和城市同名（少数直辖市情况）→ 也接受
            district != null && (city == null || province == null) -> district
            city != null -> city
            feature != null -> feature
            province != null -> province
            else -> "我的位置"
        }
    }

    /** Android 13+ 用异步 API，更稳；低版本退回同步并包在 IO 线程里。 */
    @Suppress("DEPRECATION")
    private suspend fun geocodeOne(latitude: Double, longitude: Double): Address? {
        val geocoder = this.geocoder ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(
                    latitude,
                    longitude,
                    1,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(null)
                        }
                    }
                )
            }
        } else {
            runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
        }
    }

    // ------------------------------------------------------------ 正向搜索

    /** 关键词 → 城市列表（全球）。结果同样优先区/县命名。 */
    suspend fun search(keyword: String, maxResults: Int = 40): Result<List<ResolvedPlace>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val addresses = geocodeByName(keyword, maxResults)
                addresses.mapNotNull { address ->
                    if (!address.hasLatitude() || !address.hasLongitude()) return@mapNotNull null
                    val name = bestName(address)
                    ResolvedPlace(
                        name = name,
                        adminArea = listOfNotNull(
                            address.locality?.takeIf { it != name },
                            address.adminArea?.takeIf { it != name }
                        ).distinct().joinToString(" · ").takeIf { it.isNotBlank() },
                        country = address.countryName,
                        countryCode = address.countryCode,
                        latitude = address.latitude,
                        longitude = address.longitude
                    )
                }.distinctBy { "${it.name}|${"%.2f".format(it.latitude)}|${"%.2f".format(it.longitude)}" }
            }
        }

    @Suppress("DEPRECATION")
    private suspend fun geocodeByName(keyword: String, maxResults: Int): List<Address> {
        val geocoder = this.geocoder ?: return emptyList()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocationName(
                    keyword,
                    maxResults,
                    object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            if (continuation.isActive) continuation.resume(addresses)
                        }

                        override fun onError(errorMessage: String?) {
                            if (continuation.isActive) continuation.resume(emptyList())
                        }
                    }
                )
            }
        } else {
            runCatching { geocoder.getFromLocationName(keyword, maxResults).orEmpty() }.getOrDefault(emptyList())
        }
    }

    /** 设备上没有地理编码服务时的兜底。 */
    private fun fallbackPlace(latitude: Double, longitude: Double) = ResolvedPlace(
        name = "我的位置",
        adminArea = null,
        country = null,
        countryCode = null,
        latitude = latitude,
        longitude = longitude
    )
}
