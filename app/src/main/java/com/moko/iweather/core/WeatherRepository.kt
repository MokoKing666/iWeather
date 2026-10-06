package com.moko.iweather.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 网络 + 缓存的统一入口。UI 只跟它打交道。
 *
 * 缓存策略：本地已有快照且未超过有效期时直接返回（秒开）；
 * 强制刷新时先返回旧缓存由调用方展示，再在后台更新。
 */
class WeatherRepository(private val prefs: Prefs) {

    @Volatile
    private var clientAuth: CaiyunAuth = prefs.auth()

    @Volatile
    private var client: CaiyunClient = CaiyunClient(clientAuth)

    /** 设置里换了凭证之后重建客户端。 */
    fun reloadAuth() {
        val auth = prefs.auth()
        if (auth != clientAuth) {
            clientAuth = auth
            client = CaiyunClient(auth)
        }
    }

    private val refreshLock = Mutex()

    fun cached(cityId: String): WeatherSnapshot? = prefs.snapshots[cityId]

    fun isFresh(snapshot: WeatherSnapshot?, lifetimeMinutes: Int): Boolean =
        snapshot != null && System.currentTimeMillis() - snapshot.fetchedAt < lifetimeMinutes * 60_000L

    /**
     * 拉取并写入缓存。
     * @return 成功后的快照与彩云返回的真实时区（用于回填城市）。
     */
    suspend fun refresh(city: WeatherCity): Result<Refreshed> = withContext(Dispatchers.IO) {
        // 全局串行：彩云对 QPS 敏感，多城市同时刷新时排队而不是并发打出去。
        refreshLock.withLock {
            runCatching {
                val response = client.fetchWeather(city)
                val snapshot = SnapshotMapper.map(response, city)
                val merged = prefs.snapshots + (snapshot.cityId to snapshot)
                // 只保留仍在关注的城市，避免缓存无限膨胀。
                val valid = prefs.cities.map { it.id }.toSet()
                prefs.snapshots = merged.filterKeys { it in valid }
                Refreshed(snapshot, response.timezone)
            }
        }
    }

    /**
     * 查某个坐标的实况，**不写入缓存**。
     *
     * 给地图上"点任意位置查天气"用。走轻量的 `/realtime` 而不是综合接口：
     * 单次约 800 字节，而且这个查询会占掉一次配额，越省越好。
     *
     * 顺带一提，这个查询**会排队等彩云的时间片**（约 1.2 秒），
     * 所以界面上要显示"正在查询"，不要做成看起来是实时的。
     */
    suspend fun fetchPointWeather(
        latitude: Double,
        longitude: Double,
        name: String
    ): Result<MapPointWeather> = withContext(Dispatchers.IO) {
        runCatching {
            val realtime = client.fetchRealtime(latitude, longitude, throttle = true)
            val precipitation = realtime.precipitation
            MapPointWeather(
                name = name,
                latitude = latitude,
                longitude = longitude,
                temperature = realtime.temperature,
                skycon = Skycon.from(realtime.skycon),
                aqi = realtime.airQuality?.aqi?.chn,
                aqiCategory = realtime.airQuality?.description?.chn,
                windSpeedMs = realtime.wind?.speed,
                windDirectionDeg = realtime.wind?.direction,
                // 本地没有雷达回波时退回"最近降水点"的强度，避免到处都没值
                precipitationIntensity = precipitation?.local?.intensity
                    ?: precipitation?.nearest?.intensity
            )
        }
    }

    data class Refreshed(val snapshot: WeatherSnapshot, val resolvedTimeZone: String?)
}
