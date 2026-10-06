package com.moko.iweather.core

import android.content.Context
import android.content.SharedPreferences
import com.moko.iweather.BuildConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * 本地持久化。
 *
 * 用 SharedPreferences 而不是 DataStore：数据量很小（几个城市 + 各自一份快照），
 * 读取同步、逻辑简单，也方便小组件在同进程内直接复用。
 * 同时充当"本地缓存"，断网时仍能渲染上次的天气。
 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---------------------------------------------------------- 城市列表

    var cities: List<WeatherCity>
        get() = decode<List<WeatherCity>>(KEY_CITIES) ?: listOf(WeatherCity.Beijing)
        set(value) = encode(KEY_CITIES, value)

    var currentPage: Int
        get() = sp.getInt(KEY_PAGE, 0)
        set(value) = sp.edit().putInt(KEY_PAGE, value).apply()

    var locatedCityId: String?
        get() = sp.getString(KEY_LOCATED_CITY, null)
        set(value) = sp.edit().apply {
            if (value == null) remove(KEY_LOCATED_CITY) else putString(KEY_LOCATED_CITY, value)
        }.apply()

    // ---------------------------------------------------------- 快照（缓存）

    var snapshots: Map<String, WeatherSnapshot>
        get() = decode<Map<String, WeatherSnapshot>>(KEY_SNAPSHOTS) ?: emptyMap()
        set(value) = encode(KEY_SNAPSHOTS, value)

    // ---------------------------------------------------------- 设置

    var settings: AppSettings
        get() = decode<AppSettings>(KEY_SETTINGS) ?: AppSettings()
        set(value) = encode(KEY_SETTINGS, value)

    var widgetPreference: WidgetPreference
        get() = decode<WidgetPreference>(KEY_WIDGET) ?: WidgetPreference()
        set(value) = encode(KEY_WIDGET, value)

    // ---------------------------------------------------------- 引导 / 其他

    var onboardingCompleted: Boolean
        get() = sp.getBoolean(KEY_ONBOARDING, false)
        set(value) = sp.edit().putBoolean(KEY_ONBOARDING, value).apply()

    var lastRegion: String
        get() = sp.getString(KEY_REGION, "china") ?: "china"
        set(value) = sp.edit().putString(KEY_REGION, value).apply()

    /** 彩云凭证：优先 App Key/Secret，其次 Token。 */
    fun auth(): CaiyunAuth {
        val key = sp.getString(KEY_APP_KEY, null)?.takeIf { it.isNotBlank() }
        val secret = sp.getString(KEY_APP_SECRET, null)?.takeIf { it.isNotBlank() }
        if (key != null && secret != null) return CaiyunAuth.AppKey(key, secret)

        val token = sp.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.CAIYUN_TOKEN.takeIf { it.isNotBlank() }
        return CaiyunAuth.Token(token.orEmpty())
    }

    fun isAuthConfigured(): Boolean = auth().isConfigured

    fun setAuth(auth: CaiyunAuth) {
        sp.edit().apply {
            when (auth) {
                is CaiyunAuth.Token -> {
                    putString(KEY_TOKEN, auth.token)
                    remove(KEY_APP_KEY)
                    remove(KEY_APP_SECRET)
                }
                is CaiyunAuth.AppKey -> {
                    putString(KEY_APP_KEY, auth.key)
                    putString(KEY_APP_SECRET, auth.secret)
                    remove(KEY_TOKEN)
                }
            }
        }.apply()
    }

    /** 小组件用：读出当前城市与快照。 */
    fun widgetData(): WidgetData = WidgetData(
        cities = cities,
        snapshots = snapshots,
        settings = settings,
        widgetPreference = widgetPreference
    )

    private inline fun <reified T> decode(key: String): T? =
        sp.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private inline fun <reified T> encode(key: String, value: T) {
        val text = try {
            json.encodeToString(value)
        } catch (e: Exception) {
            // 之前这里是静默 return：序列化失败等于数据凭空消失，
            // 用户看到的是"设置改了但重启就没了"，而且没有任何线索。
            Timber.w(e, "持久化失败，key=%s", key)
            return
        }
        sp.edit().putString(key, text).apply()
    }

    private companion object {
        const val FILE = "iweather_prefs"
        const val KEY_CITIES = "cities"
        const val KEY_SNAPSHOTS = "snapshots"
        const val KEY_SETTINGS = "settings"
        const val KEY_WIDGET = "widget_preference"
        const val KEY_PAGE = "current_page"
        const val KEY_LOCATED_CITY = "located_city_id"
        const val KEY_ONBOARDING = "onboarding_completed"
        const val KEY_REGION = "search_region"
        const val KEY_TOKEN = "caiyun_token"
        const val KEY_APP_KEY = "caiyun_app_key"
        const val KEY_APP_SECRET = "caiyun_app_secret"
    }
}

/** 小组件读取用的打包数据。 */
data class WidgetData(
    val cities: List<WeatherCity>,
    val snapshots: Map<String, WeatherSnapshot>,
    val settings: AppSettings,
    val widgetPreference: WidgetPreference
) {
    /**
     * 决定某个布局要显示哪个城市：
     * 1. 开启"优先跟随定位"且有定位城市 → 定位城市；
     * 2. 否则用该布局指定的固定城市；
     * 3. 再兜底到任意有数据的城市。
     */
    fun resolve(layout: WidgetPreference.Layout): Pair<WeatherCity, WeatherSnapshot?>? {
        val located = cities.firstOrNull { it.isCurrentLocation }
        val byPreference = widgetPreference.cityIdFor(layout)?.let { id -> cities.firstOrNull { it.id == id } }

        val city = when {
            widgetPreference.followCurrentLocation && located != null -> located
            byPreference != null -> byPreference
            else -> cities.firstOrNull { snapshots.containsKey(it.id) } ?: cities.firstOrNull()
        } ?: return null

        return city to snapshots[city.id]
    }
}
