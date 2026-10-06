package com.moko.iweather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moko.iweather.core.ChinaCityRepository
import com.moko.iweather.core.LocationProvider
import com.moko.iweather.core.Prefs
import com.moko.iweather.core.PlaceResolver
import com.moko.iweather.core.ResolvedPlace
import com.moko.iweather.core.Skycon
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.core.WeatherError
import com.moko.iweather.core.WeatherRepository
import com.moko.iweather.core.WeatherSnapshot
import com.moko.iweather.core.WidgetPreference
import com.moko.iweather.core.DayNightResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 搜索区域。 */
enum class SearchRegion(val label: String) { CHINA("中国"), GLOBAL("全球") }

data class AppUiState(
    val onboardingCompleted: Boolean = false,
    val cities: List<WeatherCity> = emptyList(),
    val currentPage: Int = 0,
    val snapshots: Map<String, WeatherSnapshot> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val errors: Map<String, String> = emptyMap(),
    val settings: com.moko.iweather.core.AppSettings = com.moko.iweather.core.AppSettings(),
    val widgetPreference: WidgetPreference = WidgetPreference(),
    val locateMessage: String? = null,
    val locateInProgress: Boolean = false,
    val authConfigured: Boolean = false,
    val locatedCityId: String? = null,
    /** 地图上被点选查询的点位（临时，不写入城市列表） */
    val mapQuery: com.moko.iweather.core.MapPointWeather? = null,
    val mapQueryInProgress: Boolean = false,
    val mapQueryError: String? = null,
    /**
     * 中国气象局全国站点快照；气温/降水/风三个图层共用它。
     * 一次请求 2440 个站，不占彩云配额。
     */
    val cmaSnapshot: com.moko.iweather.core.CmaStationSnapshot? = null,
    val cmaLoading: Boolean = false,
    val cmaError: String? = null,
    val searchRegion: SearchRegion = SearchRegion.CHINA,
    val searchResults: List<ResolvedPlace> = emptyList(),
    val searchInProgress: Boolean = false,
    val searchError: String? = null
) {
    val unit: TemperatureUnit get() = settings.temperatureUnit

    fun snapshot(cityId: String): WeatherSnapshot? = snapshots[cityId]

    fun isLoading(cityId: String): Boolean = cityId in loading

    fun cityById(cityId: String): WeatherCity? = cities.firstOrNull { it.id == cityId }
}

/** App 单一状态源：视图只读它、调用它的方法。 */
class WeatherViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)
    private val repository = WeatherRepository(prefs)
    private val locations = LocationProvider(app)
    private val places = PlaceResolver(app)
    private val cmaClient = com.moko.iweather.core.CmaMapClient()

    private var cmaJob: Job? = null
    val chinaCities = ChinaCityRepository(app)

    private val _state = MutableStateFlow(
        AppUiState(
            onboardingCompleted = prefs.onboardingCompleted,
            cities = prefs.cities,
            currentPage = prefs.currentPage.coerceIn(0, (prefs.cities.size - 1).coerceAtLeast(0)),
            snapshots = prefs.snapshots,
            settings = prefs.settings,
            widgetPreference = prefs.widgetPreference,
            authConfigured = prefs.isAuthConfigured(),
            locatedCityId = prefs.locatedCityId,
            searchRegion = runCatching { SearchRegion.valueOf(prefs.lastRegion) }.getOrDefault(SearchRegion.CHINA)
        )
    )
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var locateJob: Job? = null

    // ------------------------------------------------------------ 引导

    fun completeOnboarding() {
        prefs.onboardingCompleted = true
        _state.update { it.copy(onboardingCompleted = true) }
        bootstrap()
    }

    // ------------------------------------------------------------ 启动

    fun bootstrap() = viewModelScope.launch {
        // 1) 先把本地缓存渲染出来（秒开、离线可用）
        _state.update { it.copy(cities = prefs.cities, snapshots = prefs.snapshots) }
        if (!_state.value.authConfigured) return@launch

        // 2) 后台刷新，串行以遵守彩云 QPS
        refreshAll(force = false)
    }

    // ------------------------------------------------------------ 刷新

    fun refresh(cityId: String, force: Boolean = false) = viewModelScope.launch {
        val city = _state.value.cityById(cityId) ?: return@launch
        if (!_state.value.authConfigured) {
            _state.update { it.copy(errors = it.errors + (cityId to "尚未配置彩云天气 API Token")) }
            return@launch
        }
        val existing = _state.value.snapshots[cityId]
        if (!force && repository.isFresh(existing, _state.value.settings.cacheLifetimeMinutes)) return@launch

        _state.update { it.copy(loading = it.loading + cityId, errors = it.errors - cityId) }

        repository.refresh(city).fold(
            onSuccess = { refreshed ->
                val snapshots = _state.value.snapshots + (refreshed.snapshot.cityId to refreshed.snapshot)
                prefs.snapshots = snapshots

                // 彩云返回的真实时区比"经度推算"准，回填到城市上
                val resolved = refreshed.resolvedTimeZone
                val cities = if (resolved != null && TimeZoneResolver.isValidId(resolved)) {
                    val updated = _state.value.cities.map {
                        if (it.id == cityId && it.timeZoneId != resolved) it.copy(timeZoneId = resolved) else it
                    }
                    prefs.cities = updated
                    updated
                } else {
                    _state.value.cities
                }

                _state.update { it.copy(snapshots = snapshots, cities = cities, loading = it.loading - cityId) }
            },
            onFailure = { error ->
                val message = (error as? WeatherError)?.message ?: error.message ?: "刷新失败"
                _state.update { it.copy(errors = it.errors + (cityId to message), loading = it.loading - cityId) }
            }
        )
    }

    fun refreshAll(force: Boolean = true) = viewModelScope.launch {
        _state.value.cities.forEach { city -> refresh(city.id, force) }
    }

    fun setCurrentPage(page: Int) {
        val clamped = page.coerceIn(0, (_state.value.cities.size - 1).coerceAtLeast(0))
        prefs.currentPage = clamped
        _state.update { it.copy(currentPage = clamped) }
    }

    // ------------------------------------------------------------ 城市管理

    fun addCity(city: WeatherCity): Boolean {
        val current = _state.value.cities
        if (current.any { it.id == city.id }) return false

        // 同一时刻只保留一个"我的位置"
        val normalized = if (city.isCurrentLocation) {
            current.map { if (it.isCurrentLocation) it.copy(isCurrentLocation = false) else it }
        } else {
            current
        }

        val updated = normalized + city.copy(
            timeZoneId = current.firstOrNull { it.id == city.id }?.timeZoneId ?: city.timeZoneId
        )
        prefs.cities = updated
        prefs.locatedCityId = city.takeIf { it.isCurrentLocation }?.id ?: prefs.locatedCityId
        _state.update {
            it.copy(
                cities = updated,
                currentPage = updated.lastIndex,
                locatedCityId = city.takeIf { c -> c.isCurrentLocation }?.id ?: it.locatedCityId
            )
        }
        refresh(city.id, force = true)
        return true
    }

    fun removeCity(cityId: String) {
        val remaining = _state.value.cities.filterNot { it.id == cityId }.ifEmpty { listOf(WeatherCity.Beijing) }
        prefs.cities = remaining
        prefs.snapshots = _state.value.snapshots - cityId
        prefs.locatedCityId = prefs.locatedCityId?.takeIf { it != cityId }
        _state.update {
            it.copy(
                cities = remaining,
                snapshots = it.snapshots - cityId,
                currentPage = it.currentPage.coerceIn(0, (remaining.size - 1).coerceAtLeast(0))
            )
        }
    }

    /** 拖拽排序：把 from 位置的元素移动到 to。 */
    fun moveCity(from: Int, to: Int) {
        val list = _state.value.cities.toMutableList()
        if (from !in list.indices || to !in 0..list.size) return
        list.add(to, list.removeAt(from))
        prefs.cities = list
        _state.update { it.copy(cities = list) }
    }

    // ------------------------------------------------------------ 定位

    fun locateCurrentCity() {
        if (locateJob?.isActive == true) return
        locateJob = viewModelScope.launch {
            _state.update { it.copy(locateInProgress = true, locateMessage = null) }

            if (!locations.hasPermission()) {
                _state.update {
                    it.copy(
                        locateInProgress = false,
                        locateMessage = "尚未授予定位权限，请在系统弹窗中允许"
                    )
                }
                return@launch
            }
            if (!locations.isLocationEnabled()) {
                _state.update {
                    it.copy(locateInProgress = false, locateMessage = "系统定位服务未开启，请到设置中打开")
                }
                return@launch
            }

            val location = locations.currentLocation().getOrElse { error ->
                _state.update {
                    it.copy(
                        locateInProgress = false,
                        locateMessage = (error as? WeatherError)?.message ?: "定位失败"
                    )
                }
                return@launch
            }

            val place = places.reverse(location.latitude, location.longitude).getOrElse {
                _state.update { it.copy(locateInProgress = false, locateMessage = "无法把当前位置匹配到城市") }
                return@launch
            }

            val existing = _state.value.cityById(place.toCity(false).id)
            val city = place.toCity(isCurrentLocation = existing == null)
            addCity(city)

            _state.update {
                it.copy(
                    locateInProgress = false,
                    locateMessage = "已定位到 ${city.name}",
                    currentPage = _state.value.cities.indexOfFirst { c -> c.id == city.id }.coerceAtLeast(0)
                )
            }
        }
    }

    fun clearLocateMessage() = _state.update { it.copy(locateMessage = null) }

    // ------------------------------------------------------------ 地图

    /**
     * 地图上点选任意位置 → 逆地理编码取名字 + 拉该点天气。
     *
     * 这是"看其他地区的降水/气温/空气质量/风"的实现方式：
     * 气温、空气质量、风没有免费免 Key 的栅格图层，所以走点位实时查询。
     */
    fun queryMapPoint(latitude: Double, longitude: Double) {
        if (_state.value.mapQueryInProgress) return
        viewModelScope.launch {
            _state.update { it.copy(mapQueryInProgress = true, mapQueryError = null) }

            val placeName = places.reverse(latitude, longitude)
                .getOrNull()?.name ?: "选定位置"

            repository.fetchPointWeather(latitude, longitude, placeName).fold(
                onSuccess = { point ->
                    _state.update { it.copy(mapQueryInProgress = false, mapQuery = point) }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            mapQueryInProgress = false,
                            mapQueryError = (error as? WeatherError)?.message ?: "查询失败"
                        )
                    }
                }
            )
        }
    }

    fun clearMapQuery() = _state.update { it.copy(mapQuery = null, mapQueryError = null) }

    /** 已保存城市的点位（用缓存里的快照，不额外发请求）。 */
    fun mapPoints(): List<com.moko.iweather.core.MapPointWeather> =
        _state.value.cities.mapNotNull { city ->
            val snapshot = _state.value.snapshots[city.id] ?: return@mapNotNull null
            com.moko.iweather.core.MapPointWeather(
                name = city.name,
                latitude = city.latitude,
                longitude = city.longitude,
                temperature = snapshot.realtime.temperature,
                skycon = snapshot.realtime.skycon,
                aqi = snapshot.airQuality.aqi,
                aqiCategory = snapshot.airQuality.category,
                windSpeedMs = snapshot.realtime.windSpeedMs,
                windDirectionDeg = snapshot.realtime.windDirectionDeg,
                precipitationIntensity = snapshot.realtime.precipitationIntensity,
                isSavedCity = true
            )
        }

    /**
     * 拉取中国气象局的全国站点气象图。
     *
     * **一次请求**就拿到 2440 个观测站的气温/天气现象/风向风力，
     * 气温、降水、风三个图层都从这一份数据出图，切图层不再发请求。
     * 走的是气象局而不是彩云，所以不占彩云的配额，也不再受 429 影响。
     */
    fun loadCmaStations(force: Boolean = false) {
        if (cmaJob?.isActive == true) return
        if (!force && _state.value.cmaSnapshot != null) return

        cmaJob = viewModelScope.launch {
            _state.update { it.copy(cmaLoading = true, cmaError = null) }
            val snapshot = cmaClient.fetchStations()
            _state.update {
                it.copy(
                    cmaSnapshot = snapshot ?: it.cmaSnapshot,
                    cmaLoading = false,
                    cmaError = if (snapshot == null) "气象局图层数据获取失败，请检查网络后重试" else null
                )
            }
        }
    }

    fun focusCurrentLocation() {
        val locatedId = _state.value.cities.firstOrNull { it.isCurrentLocation }?.id ?: return
        val index = _state.value.cities.indexOfFirst { it.id == locatedId }
        if (index >= 0) setCurrentPage(index)
    }

    // ------------------------------------------------------------ 设置

    fun setTemperatureUnit(unit: TemperatureUnit) {
        val settings = _state.value.settings.copy(temperatureUnit = unit)
        prefs.settings = settings
        _state.update { it.copy(settings = settings) }
    }

    fun toggleTemperatureUnit() = setTemperatureUnit(
        if (_state.value.settings.temperatureUnit == TemperatureUnit.CELSIUS)
            TemperatureUnit.FAHRENHEIT else TemperatureUnit.CELSIUS
    )

    fun updateSettings(transform: (com.moko.iweather.core.AppSettings) -> com.moko.iweather.core.AppSettings) {
        val settings = transform(_state.value.settings)
        prefs.settings = settings
        _state.update { it.copy(settings = settings) }
    }

    fun updateWidgetPreference(transform: (WidgetPreference) -> WidgetPreference) {
        val preference = transform(_state.value.widgetPreference)
        prefs.widgetPreference = preference
        _state.update { it.copy(widgetPreference = preference) }
    }

    // ------------------------------------------------------------ 搜索

    fun setSearchRegion(region: SearchRegion) {
        prefs.lastRegion = region.name
        _state.update { it.copy(searchRegion = region, searchResults = emptyList(), searchError = null) }
    }

    /** 输入防抖：连续输入时只发最后一次请求。 */
    fun search(keyword: String) {
        searchJob?.cancel()
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(searchResults = emptyList(), searchInProgress = false) }
            return
        }

        val region = _state.value.searchRegion
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _state.update { it.copy(searchInProgress = true, searchError = null) }

            val result: Result<List<ResolvedPlace>> = if (region == SearchRegion.CHINA) {
                Result.success(
                    chinaCities.search(trimmed).map { city ->
                        ResolvedPlace(
                            name = city.name,
                            adminArea = city.adminArea,
                            country = city.country,
                            countryCode = city.countryCode,
                            latitude = city.latitude,
                            longitude = city.longitude
                        )
                    }
                )
            } else {
                places.search(trimmed)
            }

            result.fold(
                onSuccess = { list -> _state.update { it.copy(searchResults = list, searchInProgress = false) } },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            searchResults = emptyList(),
                            searchInProgress = false,
                            searchError = (error as? WeatherError)?.message ?: "搜索失败"
                        )
                    }
                }
            )
        }
    }

    fun provinces() = chinaCities.provinces

    fun citiesIn(province: String) = chinaCities.citiesIn(province)

    // ------------------------------------------------------------ 派生

    fun dayNight(cityId: String, now: Long = System.currentTimeMillis()) = DayNightResolver.resolve(
        snapshot = _state.value.snapshots[cityId],
        timeZoneId = _state.value.cityById(cityId)?.timeZoneId
            ?: _state.value.snapshots[cityId]?.timeZoneId
            ?: "GMT+00:00",
        city = _state.value.cityById(cityId),
        now = now
    )

    fun skycon(cityId: String): Skycon = _state.value.snapshots[cityId]?.realtime?.skycon ?: Skycon.UNKNOWN
}
