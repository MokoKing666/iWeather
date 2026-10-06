package com.moko.iweather.ui.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.AppUiState
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.ui.weather.AppleWeatherTheme
import com.moko.iweather.core.SkyconFamily
import kotlinx.coroutines.launch

/**
 * 首次启动引导：三步。
 *
 * 视觉上与天气主页共用同一套渐变 + 毛玻璃卡片语言，
 * 所以从引导切到主页不会有"换了个 App"的割裂感。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pageCount = 3
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    val theme = AppleWeatherTheme.of(SkyconFamily.PARTLY_CLOUDY, isDaytime = true)

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) viewModel.locateCurrentCity()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(theme.colors))
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> IntroPage()
                    1 -> PermissionPage(state, viewModel)
                    else -> FinishPage(state)
                }
            }

            // ------------------------------------------------ 底部操作
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pageCount) { index ->
                        Box(
                            Modifier
                                .height(7.dp)
                                .width(if (index == pagerState.currentPage) 20.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index == pagerState.currentPage) Color.White
                                    else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = {
                        if (pagerState.currentPage < pageCount - 1) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.completeOnboarding()
                            onFinished()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color(0xFF1B3A6B)
                    )
                ) {
                    Text(
                        text = if (pagerState.currentPage == pageCount - 1) "开始使用" else "继续",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (pagerState.currentPage < pageCount - 1) {
                    TextButton(onClick = {
                        viewModel.completeOnboarding()
                        onFinished()
                    }) {
                        Text("跳过", color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                    }
                }
            }
        }
    }
}

/** 毛玻璃卡片（引导页与天气主页同源）。 */
@Composable
private fun OnboardingCard(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.08f))
                )
            )
            .padding(18.dp)
    ) { content() }
}

@Composable
private fun IntroPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        Icon(
            Icons.Filled.Cloud,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("iWeather", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "按最新设计规范重做的天气应用",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 15.sp
        )

        Spacer(Modifier.height(24.dp))

        OnboardingCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                FeatureRow(Icons.Filled.Refresh, "实时 · 逐小时 · 逐日", "一次请求取回全部预报，含降水概率与体感温度")
                FeatureRow(Icons.Filled.Air, "空气质量与天气预警", "国标 AQI 色带、六项污染物，预警按级别配色")
                FeatureRow(Icons.Filled.Public, "中国与全球城市", "中国城市内置离线库，全球城市走系统地理编码")
                FeatureRow(Icons.Filled.GridOn, "2×2 / 3×2 / 4×2 小组件", "可固定城市，也可优先跟随定位城市")
                FeatureRow(Icons.Filled.LocationOn, "定位精确到区/县", "不再只到地级市")
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionPage(state: AppUiState, viewModel: WeatherViewModel) {
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.locateCurrentCity() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("允许定位", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            "用于把「我的位置」匹配成天气城市，精确到区/县。\n定位坐标只保存在本机。",
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        when {
            state.locateInProgress -> CircularProgressIndicator(color = Color.White)
            state.locateMessage?.startsWith("已定位") == true ->
                Text(
                    state.locateMessage!!,
                    color = Color.White,
                    fontSize = 16.sp
                )
            else -> Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_FINE_LOCATION
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1B3A6B)
                )
            ) {
                Text("允许使用我的位置", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        state.locateMessage?.takeIf { !it.startsWith("已定位") }?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                it,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FinishPage(state: AppUiState) {
    val located = state.cities.firstOrNull { it.isCurrentLocation }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("准备就绪", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text(
            if (located != null) {
                "已将「${located.name}」设为默认城市。点顶部城市名可切换，点左下小圆圈看天气地图，右下小圆圈进设置。"
            } else {
                "未获取到定位也没关系：进入城市页即可手动添加中国或全球城市。"
            },
            color = Color.White.copy(alpha = 0.9f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
        }
    }
}
