package com.moko.iweather.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.AppUiState
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.core.MapTileSource
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.WidgetPreference
import com.moko.iweather.ui.components.GlassCell
import com.moko.iweather.ui.components.GlassGroup
import com.moko.iweather.ui.components.GlassGroupFooter
import com.moko.iweather.ui.components.GlassGroupHeader
import com.moko.iweather.ui.components.GlassHairLine
import com.moko.iweather.ui.components.GlassStatusDot
import com.moko.iweather.ui.components.GlassToggle
import com.moko.iweather.ui.components.LocationPermissionController
import com.moko.iweather.ui.components.LocationPermissionState
import com.moko.iweather.ui.components.LiquidGlass

/**
 * 设置页 —— iOS 26/27 的 Liquid Glass 观感。
 *
 * 三点刻意取舍：
 *  - **背景是当前城市的天气渐变**，不是灰底，从天气页点「…」上来视觉连续；
 *  - 高度用 `heightIn(max=...)` 而不是 `fillMaxSize`，否则 sheet 永远全高，
 *    天气背景完全看不见；
 *  - **不放任何 API Key 配置项**：彩云 Token 与高德 Key 都在编译期注入
 *    （local.properties），普通用户不需要也不应该在这里看到它们。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    permission: LocationPermissionController,
    backdropColors: List<Color>,
    onDismiss: () -> Unit,
    onRequestLocation: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var tileSource by remember { mutableStateOf(MapTileSource.current(context)) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = 0.35f),
        contentColor = Color.White,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.4f))
                )
            }
        }
    ) {
        Column(
            Modifier
                .heightIn(max = 620.dp)
                .background(Brush.verticalGradient(backdropColors))
        ) {
            SheetHeader()

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 28.dp)
            ) {
                // ---------------------------------------- 温度
                GlassGroupHeader("温度")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "摄氏度",
                            showCheckmark = state.unit == TemperatureUnit.CELSIUS,
                            icon = Icons.Filled.AcUnit,
                            onTap = { viewModel.setTemperatureUnit(TemperatureUnit.CELSIUS) }
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "华氏度",
                            showCheckmark = state.unit == TemperatureUnit.FAHRENHEIT,
                            icon = Icons.Filled.AcUnit,
                            onTap = { viewModel.setTemperatureUnit(TemperatureUnit.FAHRENHEIT) }
                        )
                    }
                }
                GlassGroupFooter("数据始终以公制请求，切换只是本地换算；桌面小组件会同步使用这里的单位。")

                Spacer(Modifier.height(LiquidGlass.groupGap))

                // ---------------------------------------- 外观
                GlassGroupHeader("外观")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "跟随系统深色模式",
                            icon = Icons.Filled.DarkMode,
                            trailing = {
                                GlassToggle(
                                    checked = state.settings.followSystemDarkTheme,
                                    onCheckedChange = {
                                        viewModel.updateSettings { s -> s.copy(followSystemDarkTheme = it) }
                                    }
                                )
                            }
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "减少动画效果",
                            subtitle = "停用雨雪与云层动画，更省电",
                            icon = Icons.Filled.Vibration,
                            trailing = {
                                GlassToggle(
                                    checked = state.settings.reduceMotion,
                                    onCheckedChange = {
                                        viewModel.updateSettings { s -> s.copy(reduceMotion = it) }
                                    }
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(LiquidGlass.groupGap))

                // ---------------------------------------- 定位
                GlassGroupHeader("定位")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "定位权限",
                            value = permissionText(permission.state),
                            icon = Icons.Filled.MyLocation,
                            iconTint = if (permission.isGranted) LiquidGlass.accent else LiquidGlass.destructive,
                            trailing = {
                                GlassStatusDot(
                                    color = when (permission.state) {
                                        LocationPermissionState.GRANTED -> LiquidGlass.green
                                        LocationPermissionState.UNKNOWN -> Color.White.copy(alpha = 0.3f)
                                        LocationPermissionState.DENIED -> Color(0xFFFF9F0A)
                                        LocationPermissionState.BLOCKED -> LiquidGlass.destructive
                                    }
                                )
                            },
                            onTap = {
                                if (permission.state == LocationPermissionState.BLOCKED) {
                                    permission.openSystemSettings()
                                } else {
                                    onRequestLocation()
                                }
                            }
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "启动时刷新定位城市",
                            icon = Icons.Filled.LocationOn,
                            trailing = {
                                GlassToggle(
                                    checked = state.settings.refreshOnLaunch,
                                    onCheckedChange = {
                                        viewModel.updateSettings { s -> s.copy(refreshOnLaunch = it) }
                                    }
                                )
                            }
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "重新定位并匹配城市",
                            subtitle = state.locateMessage?.takeIf { it.isNotBlank() },
                            icon = Icons.Filled.MyLocation,
                            showChevron = true,
                            onTap = onRequestLocation
                        )
                    }
                }
                GlassGroupFooter(
                    if (permission.state == LocationPermissionState.BLOCKED) {
                        "权限已被系统设为「不再询问」，点按会跳转到系统应用设置页手动开启。"
                    } else {
                        "定位可精确到区 / 县。"
                    }
                )

                Spacer(Modifier.height(LiquidGlass.groupGap))

                // ---------------------------------------- 小组件
                GlassGroupHeader("桌面小组件")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "优先跟随定位城市",
                            subtitle = "关闭后可分别固定城市",
                            icon = Icons.Filled.PhoneIphone,
                            trailing = {
                                GlassToggle(
                                    checked = state.widgetPreference.followCurrentLocation,
                                    onCheckedChange = { value ->
                                        viewModel.updateWidgetPreference { it.copy(followCurrentLocation = value) }
                                    }
                                )
                            }
                        )
                        if (!state.widgetPreference.followCurrentLocation) {
                            GlassHairLine()
                            WidgetCityRow(
                                title = "2×2 布局",
                                state = state,
                                icon = Icons.Filled.PhoneIphone,
                                selectedId = state.widgetPreference.smallCityId,
                                onSelect = { id ->
                                    viewModel.updateWidgetPreference { it.withCityId(WidgetPreference.Layout.SMALL, id) }
                                }
                            )
                            GlassHairLine()
                            WidgetCityRow(
                                title = "3×2 布局",
                                state = state,
                                icon = Icons.Filled.PhoneIphone,
                                selectedId = state.widgetPreference.mediumCityId,
                                onSelect = { id ->
                                    viewModel.updateWidgetPreference { it.withCityId(WidgetPreference.Layout.MEDIUM, id) }
                                }
                            )
                            GlassHairLine()
                            WidgetCityRow(
                                title = "4×2 布局",
                                state = state,
                                icon = Icons.Filled.PhoneIphone,
                                selectedId = state.widgetPreference.largeCityId,
                                onSelect = { id ->
                                    viewModel.updateWidgetPreference { it.withCityId(WidgetPreference.Layout.LARGE, id) }
                                }
                            )
                        }
                    }
                }
                GlassGroupFooter("长按桌面小组件 →「编辑小组件」，也可以在这里为每种布局指定城市。")

                Spacer(Modifier.height(LiquidGlass.groupGap))

                // ---------------------------------------- 地图
                GlassGroupHeader("地图")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "地图图源",
                            value = tileSource.label,
                            subtitle = tileSource.note,
                            icon = Icons.Filled.Air,
                            showChevron = true,
                            onTap = {
                                val entries = MapTileSource.entries
                                tileSource = entries[(entries.indexOf(tileSource) + 1) % entries.size]
                                MapTileSource.set(context, tileSource)
                            }
                        )
                    }
                }
                GlassGroupFooter("地图空白通常是图源被当前网络挡住，切换一个即可。")

                Spacer(Modifier.height(LiquidGlass.groupGap))

                // ---------------------------------------- 关于
                GlassGroupHeader("关于")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassCell(
                            title = "版本",
                            value = com.moko.iweather.BuildConfig.VERSION_NAME,
                            icon = Icons.Filled.Info
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "中国城市离线库",
                            value = "${viewModel.chinaCities.count} 条",
                            icon = Icons.Filled.Cloud
                        )
                        GlassHairLine()
                        GlassCell(
                            title = "定位与地理搜索",
                            value = "高德",
                            icon = Icons.Filled.LocationOn
                        )
                        GlassHairLine()
                        // 开源许可与数据来源（点开是全屏浮层）
                        // 通知开关（点开自动申请 POST_NOTIFICATIONS 权限）
                        NotificationsCell()
                        GlassHairLine()
                        LicensesCell()
                    }
                }
                Spacer(Modifier.height(LiquidGlass.groupGap))
            }
        }
    }
}

@Composable
private fun SheetHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "设置",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "iWeather ${com.moko.iweather.BuildConfig.VERSION_NAME}",
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp
        )
    }
}

/**
 * 小组件城市选择。
 *
 * 这里用「点一下切换下一个」而不是弹出下拉菜单 —— iOS 上这类只有一个值的行
 * 就是循环切换，列表反而更重。
 */
@Composable
private fun WidgetCityRow(
    title: String,
    state: AppUiState,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selectedId: String?,
    onSelect: (String?) -> Unit
) {
    val names = state.cities.associate { it.id to it.name }
    val value = selectedId?.let { names[it] } ?: "跟随定位城市"
    if (state.cities.size <= 1) {
        GlassCell(title = title, value = value.ifEmpty { "—" }, icon = icon)
    } else {
        GlassCell(
            title = title,
            value = value,
            icon = icon,
            showChevron = true,
            onTap = {
                val ids = listOf<String?>(null) + state.cities.map { it.id }
                val current = ids.indexOf(selectedId).coerceAtLeast(0)
                onSelect(ids[(current + 1) % ids.size])
            }
        )
    }
}

private fun permissionText(state: LocationPermissionState): String = when (state) {
    LocationPermissionState.GRANTED -> "已授权"
    LocationPermissionState.DENIED -> "已拒绝，点按重新申请"
    LocationPermissionState.BLOCKED -> "已禁止，点按去系统设置"
    LocationPermissionState.UNKNOWN -> "未申请，点按授权"
}
