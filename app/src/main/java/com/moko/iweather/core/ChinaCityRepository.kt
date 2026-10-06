package com.moko.iweather.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 内置中国城市离线库（assets/china_cities.json，2500+ 条）。
 *
 * 彩云按经纬度出报、不认和风的 CityID，因此这里只用到名称、省份与坐标。
 * 有了它，"中国"城市列表完全离线可用，搜索也是瞬时的。
 */
@Serializable
private data class ChinaCityRecord(
    @SerialName("n") val name: String,
    @SerialName("p") val province: String,
    @SerialName("a") val latitude: Double,
    @SerialName("o") val longitude: Double
)

@Serializable
data class ProvinceGroup(val name: String, val cities: List<WeatherCity>)

class ChinaCityRepository(context: Context) {

    private val appContext = context.applicationContext

    private val allCities: List<WeatherCity> by lazy { load() }

    private val grouped: List<ProvinceGroup> by lazy {
        allCities.groupBy { it.adminArea.orEmpty() }
            .map { (province, cities) ->
                ProvinceGroup(province, cities.sortedBy { it.name })
            }
            .sortedBy { it.name }
    }

    val provinces: List<ProvinceGroup> get() = grouped

    val count: Int get() = allCities.size

    fun search(keyword: String): List<WeatherCity> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return emptyList()
        return allCities.filter {
            it.name.contains(trimmed, ignoreCase = true) ||
                it.adminArea.orEmpty().contains(trimmed, ignoreCase = true)
        }.take(60)
    }

    fun citiesIn(province: String): List<WeatherCity> =
        grouped.firstOrNull { it.name == province }?.cities.orEmpty()

    suspend fun warmUp() = withContext(Dispatchers.IO) { allCities.size }

    private fun load(): List<WeatherCity> = runCatching {
        val json = Json { ignoreUnknownKeys = true }
        appContext.assets.open("china_cities.json").bufferedReader().use { reader ->
            json.decodeFromString<List<ChinaCityRecord>>(reader.readText())
        }
    }.getOrElse { emptyList() }
        .map { record ->
            WeatherCity(
                id = WeatherCity.makeId(record.latitude, record.longitude),
                name = record.name,
                latitude = record.latitude,
                longitude = record.longitude,
                adminArea = record.province,
                country = "中国",
                countryCode = "CN",
                timeZoneId = "Asia/Shanghai"
            )
        }
}
