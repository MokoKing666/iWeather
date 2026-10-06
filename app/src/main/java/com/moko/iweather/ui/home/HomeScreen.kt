package com.moko.iweather.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.AppUiState
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.core.SkyconFamily
import com.moko.iweather.ui.components.AppleActionItem
import com.moko.iweather.ui.components.AppleActionSheet
import com.moko.iweather.ui.components.ApplePageDots
import com.moko.iweather.ui.components.GlassCircleButton
import com.moko.iweather.ui.weather.AppleWeatherTheme
import com.moko.iweather.ui.weather.PagWeatherEffect
import com.moko.iweather.ui.weather.WeatherBackdrop
import com.moko.iweather.ui.weather.rememberParticleProgress

/**
 * 天气主页。
 *
 * 复刻 Apple 的两点关键手感：
 *  - 横向滑动切城市时，**当前页会轻微缩小并降低不透明度**，背景跟着过渡；
 *  - 底部只有"页码圆点 + 悬浮圆形 …" ，没有多余按钮。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    onOpenCityList: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenMap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cities = state.cities
    val pagerState = rememberPagerState(pageCount = { cities.size })

    LaunchedEffect(pagerState, cities.size) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (cities.isNotEmpty() && page != state.currentPage) viewModel.setCurrentPage(page)
        }
    }
    LaunchedEffect(state.currentPage, cities.size) {
        if (cities.isNotEmpty() && pagerState.currentPage != state.currentPage) {
            pagerState.animateScrollToPage(state.currentPage.coerceIn(0, cities.lastIndex))
        }
    }

    val currentCity = cities.getOrNull(pagerState.currentPage)
    val currentSnapshot = currentCity?.let { state.snapshot(it.id) }
    val isDaytime = currentCity?.let { viewModel.dayNight(it.id).isDaytime } ?: true
    val theme = AppleWeatherTheme.of(
        family = currentSnapshot?.realtime?.skycon?.family ?: SkyconFamily.UNKNOWN,
        isDaytime = isDaytime
    )

    val particleProgress by rememberParticleProgress(isAnimated = !state.settings.reduceMotion)
    // 整屏尺寸：玻璃层要在**屏幕坐标系**里画粒子，才能和背景对上
    val density = LocalDensity.current
    val screenDp = LocalConfiguration.current.screenWidthDp to LocalConfiguration.current.screenHeightDp
    val fieldWidth = with(density) { screenDp.first.dp.toPx() }
    val fieldHeight = with(density) { screenDp.second.dp.toPx() }

    WeatherBackdrop(
        theme = theme,
        modifier = modifier.fillMaxSize(),
        isAnimated = !state.settings.reduceMotion,
        particleProgress = particleProgress
    ) {
        // PAG 天气动效：只在有对应 .pag 素材时播放，
        // 没有素材（目前除晴朗夜晚外都没有）就什么都不画，
        // 继续用 WeatherBackdrop 里的程序化粒子。
        cities.getOrNull(pagerState.currentPage)?.let { page ->
            PagWeatherEffect(
                family = state.snapshot(page.id)?.realtime?.skycon?.family ?: SkyconFamily.UNKNOWN,
                isDaytime = viewModel.dayNight(page.id).isDaytime
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 0.dp,
            beyondViewportPageCount = 1
        ) { page ->
            val city = cities.getOrNull(page) ?: return@HorizontalPager
            val snapshot = state.snapshot(city.id)
            val dayNight = viewModel.dayNight(city.id)
            val isRefreshing = state.isLoading(city.id)

            // Apple 的切页手感：相邻页缩小 + 变淡
            val offset = (pagerState.currentPage - page + pagerState.currentPageOffsetFraction)
                .coerceIn(-1f, 1f)
            val scale by animateFloatAsState(
                targetValue = 1f - 0.06f * kotlin.math.abs(offset),
                label = "page-scale"
            )
            val alpha by animateFloatAsState(
                targetValue = 1f - 0.35f * kotlin.math.abs(offset),
                label = "page-alpha"
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh(city.id, force = true) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            ) {
                WeatherPageContent(
                    city = city,
                    snapshot = snapshot,
                    unit = state.unit,
                    isDaytime = dayNight.isDaytime,
                    isLoading = isRefreshing,
                    errorMessage = state.errors[city.id],
                    sunrise = dayNight.sunrise,
                    sunset = dayNight.sunset,
                    nextTransition = dayNight.nextTransition,
                    transitionIsSunrise = dayNight.transitionIsSunrise,
                    onOpenCityList = onOpenCityList,
                    particleStyle = theme.particle,
                    particleProgress = particleProgress,
                    fieldWidth = fieldWidth,
                    fieldHeight = fieldHeight,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        BottomBar(
            cityCount = cities.size,
            currentPage = pagerState.currentPage,
            onPageClick = { viewModel.focusCurrentLocation() },
            onRefreshAll = { viewModel.refreshAll(force = true) },
            onLocate = onRequestLocation,
            onCityList = onOpenCityList,
            onSearch = onOpenSearch,
            onSettings = onOpenSettings,
            onOpenMap = onOpenMap
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BoxScope.BottomBar(
    cityCount: Int,
    currentPage: Int,
    onPageClick: () -> Unit,
    onRefreshAll: () -> Unit,
    onLocate: () -> Unit,
    onCityList: () -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onOpenMap: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp)
            .padding(bottom = 12.dp)
    ) {
        // 左下：天气地图
        GlassCircleButton(onClick = onOpenMap) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = "天气地图",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }

        // 底部中间：分页圆点（点一下回到「我的位置」）
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .clip(CircleShape)
                .clickable { onPageClick() }
                .padding(horizontal = 10.dp, vertical = 14.dp)
        ) {
            ApplePageDots(count = cityCount, current = currentPage)
        }

        // 右下：设置
        Box(
            Modifier
                .align(Alignment.BottomEnd)
        ) {
            GlassCircleButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "设置",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    // iOS 操作面板（替换掉 Material 的 DropdownMenu，那个在深色背景上完全不像 iOS）
    if (menuExpanded) {
        AppleActionSheet(
            title = null,
            items = listOf(
                AppleActionItem("设置", Icons.Filled.Settings) {
                    menuExpanded = false; onSettings()
                },
                AppleActionItem("管理城市", Icons.Filled.List) {
                    menuExpanded = false; onCityList()
                },
                AppleActionItem("添加城市", Icons.Filled.Add) {
                    menuExpanded = false; onSearch()
                },
                AppleActionItem("重新定位", Icons.Filled.MyLocation) {
                    menuExpanded = false; onLocate()
                },
                AppleActionItem("刷新全部", Icons.Filled.Refresh) {
                    menuExpanded = false; onRefreshAll()
                }
            ),
            onDismiss = { menuExpanded = false }
        )
    }
}


