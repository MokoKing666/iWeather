package com.moko.iweather.ui.city

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.AppUiState
import com.moko.iweather.SearchRegion
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.core.ProvinceGroup
import com.moko.iweather.ui.components.GlassCell
import com.moko.iweather.ui.components.GlassGroup
import com.moko.iweather.ui.components.GlassGroupFooter
import com.moko.iweather.ui.components.GlassGroupHeader
import com.moko.iweather.ui.components.GlassHairLine
import com.moko.iweather.ui.components.LiquidGlass

/**
 * 添加城市：中国走内置离线城市库，全球走系统地理编码。
 * 版式与城市管理页保持一致（iOS inset-grouped 列表）。
 */
@Composable
fun CitySearchScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    onBack: () -> Unit,
    backdropColors: List<Color>,
    modifier: Modifier = Modifier
) {
    var keyword by remember { mutableStateOf("") }
    val provinces = remember { viewModel.provinces() }

    LaunchedEffect(keyword) { viewModel.search(keyword) }

    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(backdropColors))
    ) {
            // ------------------------------------------------ 顶部
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = LiquidGlass.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "添加城市",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ------------------------------------------------ 中国 / 全球
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchRegion.entries.forEach { region ->
                    val active = state.searchRegion == region
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(
                                if (active) LiquidGlass.accent else Color.White.copy(alpha = 0.12f)
                            )
                            .clickable { viewModel.setSearchRegion(region) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Public,
                            contentDescription = null,
                            tint = if (active) Color.White else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = region.label,
                            color = if (active) Color.White else Color.White,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            // ------------------------------------------------ 搜索框
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                placeholder = {
                    Text(
                        state.searchRegion.searchHint,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 17.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.12f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.12f),
                    focusedBorderColor = LiquidGlass.accent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = LiquidGlass.accent,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            if (state.searchInProgress) {
                Box(Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White.copy(alpha = 0.65f)
                    )
                }
            }

            state.searchError?.let {
                Text(
                    text = it,
                    color = LiquidGlass.destructive,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // ------------------------------------------------ 内容
            val trimmed = keyword.trim()
            if (trimmed.isEmpty() && state.searchRegion == SearchRegion.CHINA) {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        GlassGroupHeader("按省份浏览")
                        GlassGroup {
                            provinces.forEachIndexed { index, group ->
                                if (index > 0) GlassHairLine()
                                ProvinceRow(group) { viewModel.search(it) }
                            }
                        }
                        GlassGroupFooter("共 ${viewModel.chinaCities.count} 个城市，完全离线可用。")
                    }
                }
            } else if (trimmed.isEmpty()) {
                GlassGroupFooter("输入关键词搜索任意国家或城市，例如 Tokyo、Paris、Sydney。")
            } else if (state.searchResults.isEmpty() && !state.searchInProgress) {
                GlassGroupFooter("没有找到匹配的城市，换个关键词试试。")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        GlassGroupHeader("搜索结果（${state.searchResults.size}）")
                        GlassGroup {
                            state.searchResults.forEachIndexed { index, place ->
                                if (index > 0) GlassHairLine()
                                val city = place.toCity(isCurrentLocation = false)
                                val added = state.cities.any { it.id == city.id }
                                GlassCell(
                                    title = city.name,
                                    subtitle = city.subtitle.takeIf { it.isNotBlank() },
                                    onTap = {
                                        viewModel.addCity(city)
                                        onBack()
                                    },
                                    trailing = {
                                        if (added) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "已添加",
                                                tint = LiquidGlass.accent,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
}

@Composable
private fun ProvinceRow(group: ProvinceGroup, onPick: (String) -> Unit) {
    GlassCell(
        title = group.name,
        value = "${group.cities.size} 个",
        onTap = { onPick(group.name) }
    )
}

private val SearchRegion.searchHint: String
    get() = if (this == SearchRegion.CHINA) "搜索中国城市" else "搜索全球城市"
