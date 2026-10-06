package com.moko.iweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.moko.iweather.ui.city.CityListScreen
import com.moko.iweather.ui.city.CitySearchScreen
import com.moko.iweather.ui.components.rememberLocationPermissionController
import com.moko.iweather.ui.home.HomeScreen
import com.moko.iweather.ui.map.WeatherMapScreen
import com.moko.iweather.ui.onboarding.OnboardingScreen
import com.moko.iweather.ui.settings.SettingsScreen
import com.moko.iweather.ui.theme.IWeatherTheme
import com.moko.iweather.ui.weather.AppleWeatherTheme
import com.moko.iweather.core.SkyconFamily

/** 极简页面栈，避免为此再引入 navigation 依赖。设置页以 sheet 呈现，不进栈。 */
private sealed interface Screen {
    data object Home : Screen
    data object CityList : Screen
    data object CitySearch : Screen
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val deepLinkCityId = intent?.data
            ?.takeIf { it.scheme == "iweather" }
            ?.lastPathSegment

        setContent {
            val viewModel: WeatherViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            IWeatherTheme(followSystemDarkTheme = state.settings.followSystemDarkTheme) {
                if (!state.onboardingCompleted) {
                    OnboardingScreen(
                        state = state,
                        viewModel = viewModel,
                        onFinished = { /* 状态切到已完成，这里会自动进入主页 */ },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
                    )
                    return@IWeatherTheme
                }

                val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
                val current = backStack.last()

                fun push(screen: Screen) { backStack.add(screen) }
                fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

                // 从小组件点击进来时切到对应城市
                LaunchedEffect(deepLinkCityId, state.cities.size) {
                    val target = deepLinkCityId ?: return@LaunchedEffect
                    val index = state.cities.indexOfFirst { it.id == target }
                    if (index >= 0) viewModel.setCurrentPage(index)
                }

                BackHandler(enabled = backStack.size > 1) { pop() }

                val inset = Modifier.windowInsetsPadding(WindowInsets.systemBars)

                // 权限控制器：所有"要定位"的入口都走它，保证一定会弹系统授权框
                val permission = rememberLocationPermissionController(
                    onGranted = { viewModel.locateCurrentCity() }
                )
                val requestLocation = remember(permission) {
                    { permission.requestOrRun { viewModel.locateCurrentCity() } }
                }
                var settingsOpen by remember { mutableStateOf(false) }
                var mapOpen by remember { mutableStateOf(false) }

                // 当前城市的天气渐变：设置页/城市页都用它做背景，
                // 这样从天气页点进来是连续的视觉，而不是突然换成灰底。
                val backdropColors = remember(state.cities, state.currentPage, state.snapshots) {
                    val city = state.cities.getOrNull(state.currentPage)
                    val snapshot = city?.let { state.snapshot(it.id) }
                    val isDay = city?.let { viewModel.dayNight(it.id).isDaytime } ?: true
                    AppleWeatherTheme.of(
                        family = snapshot?.realtime?.skycon?.family ?: SkyconFamily.UNKNOWN,
                        isDaytime = isDay
                    ).colors
                }

                when (current) {
                    Screen.Home -> HomeScreen(
                        state = state,
                        viewModel = viewModel,
                        onOpenCityList = { push(Screen.CityList) },
                        onOpenSearch = { push(Screen.CitySearch) },
                        onOpenSettings = { settingsOpen = true },
                        onRequestLocation = requestLocation,
                        onOpenMap = { mapOpen = true },
                        modifier = Modifier
                    )

                    Screen.CityList -> CityListScreen(
                        state = state,
                        viewModel = viewModel,
                        onBack = { pop() },
                        onAddCity = { push(Screen.CitySearch) },
                        onOpenSettings = { settingsOpen = true },
                        onRequestLocation = requestLocation,
                        backdropColors = backdropColors,
                        modifier = Modifier
                    )

                    Screen.CitySearch -> CitySearchScreen(
                        state = state,
                        viewModel = viewModel,
                        onBack = { pop() },
                        backdropColors = backdropColors,
                        modifier = Modifier
                    )
                }

                // 设置以 sheet 形式浮在天气页之上（和 Apple 各 App 的设置页一致）
                if (settingsOpen) {
                    SettingsScreen(
                        state = state,
                        viewModel = viewModel,
                        permission = permission,
                        backdropColors = backdropColors,
                        onDismiss = { settingsOpen = false },
                        onRequestLocation = requestLocation
                    )
                }

                // 天气地图（左下角小圆圈）
                if (mapOpen) {
                    WeatherMapScreen(
                        state = state,
                        viewModel = viewModel,
                        currentCity = state.cities.getOrNull(state.currentPage),
                        onClose = { mapOpen = false }
                    )
                }
            }
        }
    }
}