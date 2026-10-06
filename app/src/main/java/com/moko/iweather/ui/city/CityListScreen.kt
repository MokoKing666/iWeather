package com.moko.iweather.ui.city

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moko.iweather.AppUiState
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.ui.components.GlassCell
import com.moko.iweather.ui.components.GlassGroup
import com.moko.iweather.ui.components.GlassGroupFooter
import com.moko.iweather.ui.components.GlassGroupHeader
import com.moko.iweather.ui.components.GlassHairLine
import com.moko.iweather.ui.components.GlassSpecular
import com.moko.iweather.ui.components.LiquidGlass
import com.moko.iweather.ui.weather.SkyconIcons

/**
 * 城市管理页。
 *
 * 背景是当前城市的天气渐变，卡片是 Liquid Glass —— 与设置页、天气页同一套视觉。
 * 行内结构：城市名 + 副标题（省·市）在左，天气图标 + 温度在右；
 * 编辑态长按拖拽排序、红色图标删除。
 */
@Composable
fun CityListScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    onAddCity: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestLocation: () -> Unit,
    backdropColors: List<Color>,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    var showAll by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val visibleCities = if (editing || showAll) state.cities else state.cities.take(20)

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backdropColors))
    ) {
        // ------------------------------------------------ 顶部标题栏
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text("‹ 返回", color = LiquidGlass.accent, fontSize = 17.sp)
            }
            Text(
                text = "城市",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            TextButton(
                onClick = {
                    editing = !editing
                    if (editing) showAll = true
                }
            ) {
                Text(
                    text = if (editing) "完成" else "编辑",
                    color = LiquidGlass.accent,
                    fontSize = 17.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
        ) {
            // ---------------------------------------- 定位
            item {
                GlassGroupHeader("我的位置")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassSpecular()
                        GlassCell(
                            title = state.cities.firstOrNull { it.isCurrentLocation }?.name
                                ?: "定位并匹配所在城市",
                            subtitle = state.locateMessage?.takeIf { it.isNotBlank() }
                                ?: "使用系统定位，精确到区 / 县",
                            icon = Icons.Filled.MyLocation,
                            onTap = onRequestLocation,
                            trailing = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(LiquidGlass.green)
                                )
                            }
                        )
                    }
                }
                GlassGroupFooter("定位优先用 OpenStreetMap 逆地理编码，可精确到区/县。")
                Spacer(Modifier.height(LiquidGlass.groupGap))
            }

            // ---------------------------------------- 城市列表
            item {
                GlassGroupHeader("我的城市（${state.cities.size}）")
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassSpecular()
                        visibleCities.forEachIndexed { index, city ->
                            if (index > 0) GlassHairLine()
                            val isDragging = draggingIndex == index
                            Box(
                                Modifier
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer {
                                        if (isDragging) {
                                            translationY = dragOffsetY
                                            scaleX = 1.02f
                                            scaleY = 1.02f
                                        }
                                    }
                                    .then(
                                        if (editing) {
                                            Modifier.pointerInput(city.id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = {
                                                        draggingIndex = index
                                                        dragOffsetY = 0f
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.LongPress
                                                        )
                                                    },
                                                    onDragEnd = {
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDragCancel = {
                                                        draggingIndex = null
                                                        dragOffsetY = 0f
                                                    },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragOffsetY += amount.y
                                                        val from = draggingIndex
                                                        if (from == null) return@detectDragGesturesAfterLongPress
                                                        val target = when {
                                                            dragOffsetY > 56f -> from + 1
                                                            dragOffsetY < -56f -> from - 1
                                                            else -> from
                                                        }.coerceIn(0, state.cities.lastIndex)
                                                        if (target != from) {
                                                            viewModel.moveCity(from, target)
                                                            draggingIndex = target
                                                            dragOffsetY = 0f
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }
                                                    }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                CityRow(
                                    city = city,
                                    index = index,
                                    state = state,
                                    editing = editing,
                                    onClick = {
                                        viewModel.setCurrentPage(index)
                                        onBack()
                                    },
                                    onRemove = { viewModel.removeCity(city.id) }
                                )
                            }
                        }
                    }
                }
                if (editing) {
                    GlassGroupFooter("长按拖动调整主页顺序；点红色图标删除。")
                }
                Spacer(Modifier.height(LiquidGlass.groupGap))
            }

            if (!editing && state.cities.size > visibleCities.size) {
                item {
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        GlassGroup {
                            GlassSpecular()
                            GlassCell(
                                title = "显示更多（剩余 ${state.cities.size - visibleCities.size} 个）",
                                onTap = { showAll = true }
                            )
                        }
                    }
                    Spacer(Modifier.height(LiquidGlass.groupGap))
                }
            }

            // ---------------------------------------- 操作
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    GlassGroup {
                        GlassSpecular()
                        GlassCell(title = "添加城市", icon = Icons.Filled.Add, onTap = onAddCity)
                        GlassHairLine()
                        GlassCell(
                            title = "刷新全部城市",
                            icon = Icons.Filled.Refresh,
                            onTap = { viewModel.refreshAll(force = true) }
                        )
                        GlassHairLine()
                        GlassCell(title = "设置", showChevron = true, onTap = onOpenSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun CityRow(
    city: WeatherCity,
    index: Int,
    state: AppUiState,
    editing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val snapshot = state.snapshot(city.id)
    val isCurrent = index == state.currentPage

    GlassCell(
        title = city.name,
        subtitle = city.subtitle.takeIf { it.isNotBlank() },
        onTap = onClick,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (snapshot != null) {
                    Icon(
                        imageVector = SkyconIcons.icon(snapshot.realtime.skycon, true),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = state.unit.format(snapshot.realtime.temperature),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 17.sp
                    )
                }
                if (editing) {
                    Spacer(Modifier.size(12.dp))
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = LiquidGlass.destructive,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onRemove() }
                            .padding(2.dp)
                    )
                    Icon(
                        imageVector = Icons.Filled.DragHandle,
                        contentDescription = "拖动排序",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(20.dp)
                    )
                } else if (isCurrent) {
                    Spacer(Modifier.size(10.dp))
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "当前城市",
                        tint = LiquidGlass.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}
