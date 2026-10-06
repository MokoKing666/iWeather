package com.moko.iweather.ui.map

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.moko.iweather.AppUiState
import com.moko.iweather.WeatherViewModel
import com.moko.iweather.core.CmaStation
import com.moko.iweather.core.MapPointWeather
import com.moko.iweather.core.MapTileSource
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.core.WeatherMapLayer
import com.moko.iweather.core.colorFor
import com.moko.iweather.core.compactLabel
import com.moko.iweather.core.createTileSource
import com.moko.iweather.core.formatValue
import com.moko.iweather.core.legendLabels
import com.moko.iweather.core.legendStops
import com.moko.iweather.core.valueOf
import com.moko.iweather.ui.components.GlassCircleButton
import com.moko.iweather.ui.components.GlassGroup
import com.moko.iweather.ui.components.GlassGroupFooter
import com.moko.iweather.ui.components.GlassHairLine
import com.moko.iweather.ui.components.GlassSpecular
import com.moko.iweather.ui.components.LiquidGlass
import com.moko.iweather.ui.components.floatingGlass
import com.moko.iweather.ui.weather.SkyconIcons
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay

/**
 * 天气地图。
 *
 * 图层数据来自**中国气象局的全国站点气象图**（`weather.cma.cn/api/map/weather/1`）：
 * 一次请求返回 2440 个国家级观测站的气温、天气现象、风向风力，
 * 所以气温 / 降水 / 风三张图都是**真实的空间分布**，而且不占彩云配额。
 *
 * 空气质量没有免费的全量数据源，所以那一层是「已保存城市 + 点地图查询」，
 * 界面上会明确写出来，避免看起来像坏了。
 *
 * 渲染用 Compose Canvas 叠在 MapView 上：热力斑块是"中心实、边缘透明"的径向渐变圆，
 * 相邻点互相重叠连成一片；MapView 只负责底图和手势。
 */
@Composable
fun WeatherMapScreen(
    state: AppUiState,
    viewModel: WeatherViewModel,
    currentCity: WeatherCity?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var layer by remember { mutableStateOf(WeatherMapLayer.current(context)) }
    var baseMapMenuOpen by remember { mutableStateOf(false) }
    var mapSize by remember { mutableStateOf(IntSize.Zero) }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(createTileSource(MapTileSource.current(context)))
            setMultiTouchControls(true)
            // 这两个开关默认都是 true，会在屏幕上渲染一对系统样式的 +/− 方块按钮，
            // 和 App 的玻璃风格完全不搭。缩放靠双指捏合就够了。
            setBuiltInZoomControls(false)
            setUseDataConnection(true)
            controller.setZoom(5.5)
            isTilesScaledToDpi = true
            setBackgroundColor(android.graphics.Color.parseColor("#0B0F14"))
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        mapView.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { mapView.onDetach() }
        }
    }

    // 天气图层用 osmdroid 的 Overlay 画：由地图自己的绘制循环每帧调用，
    // 投影天然同步。之前画在 Compose Canvas 里、靠状态自增触发重绘，
    // 结果是缩放时点位不跟随。
    val layerRef = rememberUpdatedState(layer)
    val unitRef = rememberUpdatedState(state.unit)
    val stationsRef = rememberUpdatedState(state.cmaSnapshot?.stations.orEmpty())
    val queryRef = rememberUpdatedState(state.mapQuery)

    DisposableEffect(mapView) {
        val provider = { viewModel.mapPoints() }
        val field = StationFieldOverlay(
            stationsProvider = { stationsRef.value },
            layerProvider = { layerRef.value }
        )
        val markers = MapMarkerOverlay(
            pointsProvider = provider,
            queryProvider = { queryRef.value },
            layerProvider = { layerRef.value },
            unitProvider = { unitRef.value }
        )
        // 顺序即绘制顺序：色场在下，标注在上
        mapView.overlays.add(0, field)
        mapView.overlays.add(1, markers)
        onDispose {
            mapView.overlays.remove(field)
            mapView.overlays.remove(markers)
        }
    }

    // 数据或图层变了要让地图重画一次；平移缩放由地图自己驱动，不用管
    LaunchedEffect(layer, state.cmaSnapshot, state.mapQuery, state.cities, state.snapshots) {
        mapView.invalidate()
    }

    // 气泡的位置也是按投影算的，地图一动就会对不上，干脆收起
    DisposableEffect(mapView, viewModel) {
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                viewModel.clearMapQuery()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                viewModel.clearMapQuery()
                return true
            }
        }
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // 点地图任意位置 → 查该点实况
    DisposableEffect(mapView) {
        val receiver = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                p?.let { viewModel.queryMapPoint(it.latitude, it.longitude) }
                return true
            }

            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        mapView.overlays.add(receiver)
        onDispose { mapView.overlays.remove(receiver) }
    }

    LaunchedEffect(mapSize) {
        if (mapSize.height > 0) {
            currentCity?.let { mapView.controller.setCenter(GeoPoint(it.latitude, it.longitude)) }
            viewModel.loadCmaStations()
        }
    }

    val stations = state.cmaSnapshot?.stations.orEmpty()
    // 气象局全国图不含空气质量，这一层只用城市标注 + 点击查询
    val showsStationField = layer != WeatherMapLayer.AIR_QUALITY && stations.isNotEmpty()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F14))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize().onSizeChanged { mapSize = it },
            factory = { mapView }
        )

        // 天气图层在 AndroidView 内部由 osmdroid 绘制，这里没有 Compose 层

        // ---------------------------------------------- 顶部
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                    )
                )
                .padding(start = 20.dp, end = 76.dp, top = 50.dp, bottom = 26.dp)
        ) {
            Text(
                text = currentCity?.name ?: "天气地图",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle(state, layer, stations),
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(10.dp))
            Legend(
                layer = layer,
                unit = state.unit,
                range = remember(stations, layer) {
                    val values = stations.mapNotNull { layer.valueOf(it) }
                    if (values.isEmpty()) null else values.min()..values.max()
                }
            )
        }

        // ---------------------------------------------- 右上操作
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 46.dp, end = 12.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Box {
                GlassCircleButton(onClick = { baseMapMenuOpen = true }) {
                    Icon(
                        Icons.Filled.Layers,
                        contentDescription = "底图源",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = baseMapMenuOpen,
                    onDismissRequest = { baseMapMenuOpen = false },
                    modifier = Modifier.background(Color(0xFF1C1C1E))
                ) {
                    Text(
                        text = "底图",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                    )
                    MapTileSource.entries.forEach { source ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(source.label, color = Color.White, fontSize = 15.sp)
                                    Text(
                                        source.note,
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            onClick = {
                                baseMapMenuOpen = false
                                MapTileSource.set(context, source)
                                mapView.setTileSource(createTileSource(source))
                                mapView.invalidate()
                            }
                        )
                    }
                }
            }

            GlassCircleButton(onClick = { viewModel.loadCmaStations(force = true) }) {
                if (state.cmaLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新图层数据",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            GlassCircleButton(onClick = {
                viewModel.clearMapQuery()
                currentCity?.let {
                    mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                }
            }) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "回到我的位置",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            GlassCircleButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭地图",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        state.cmaError?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFFD166),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 150.dp, start = 20.dp, end = 20.dp)
            )
        }

        // ---------------------------------------------- 点选气泡（锚在被点的位置上）
        state.mapQuery?.let { query ->
            MapAnchorBubble(
                point = query,
                layer = layer,
                unit = state.unit,
                inProgress = state.mapQueryInProgress,
                errorMessage = state.mapQueryError,
                mapView = mapView,
                containerSize = mapSize,
                onDismiss = { viewModel.clearMapQuery() },
                onAddCity = {
                    viewModel.addCity(
                        WeatherCity(
                            id = WeatherCity.makeId(query.latitude, query.longitude),
                            name = query.name,
                            latitude = query.latitude,
                            longitude = query.longitude
                        )
                    )
                    viewModel.clearMapQuery()
                }
            )
        }

        if (state.mapQueryInProgress && state.mapQuery == null) {
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 156.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    "正在查询该位置（彩云限流约 1.2 秒一次）…",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // ---------------------------------------------- 底部图层切换
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 14.dp)
        ) {
            LayerSwitcher(
                selected = layer,
                onSelect = {
                    layer = it
                    WeatherMapLayer.set(context, it)
                }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (layer == WeatherMapLayer.AIR_QUALITY) {
                    "空气质量：点地图任意位置逐点查询（彩云天气）"
                } else {
                    "图层数据：中国气象局全国站点 · 底图 © OpenStreetMap 贡献者"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

// ---------------------------------------------------------------- 文案

private fun subtitle(
    state: AppUiState,
    layer: WeatherMapLayer,
    stations: List<CmaStation>
): String {
    if (state.cmaLoading) return "正在获取气象局站点数据…"
    if (layer == WeatherMapLayer.AIR_QUALITY) {
        return "全国图不含空气质量 · 点地图查询任意点"
    }
    if (stations.isEmpty()) return "暂时没有站点数据，点右上角刷新"
    val values = stations.mapNotNull { layer.valueOf(it) }
    val range = if (values.isEmpty()) null else values.min()..values.max()
    val span = range?.let {
        " · ${layer.compactLabel(it.start, state.unit)}~${layer.compactLabel(it.endInclusive, state.unit)}"
    } ?: ""
    val update = state.cmaSnapshot?.lastUpdate?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
    return "${stations.size} 个观测站$span$update"
}

// ---------------------------------------------------------------- 图层渲染

// ---------------------------------------------------------------- 组件

/**
 * 图例。
 *
 * 气温/降水/风用**连续色带 + 当前数据的实际刻度**（数值直接从站点数据里取，
 * 所以图例上的数字和地图上的颜色是同一套）；空气质量用国标六段离散色块，
 * 因为国标本身就是分级的，画成连续色带反而误导。
 */
@Composable
private fun Legend(
    layer: WeatherMapLayer,
    unit: TemperatureUnit,
    range: ClosedFloatingPointRange<Double>?
) {
    Column(
        Modifier
            .floatingGlass(RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = layer.label,
            color = Color.White.copy(alpha = 0.62f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (layer == WeatherMapLayer.AIR_QUALITY) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AqiLegend.forEach { entry ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            Modifier
                                .width(30.dp)
                                .height(9.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(entry.second)
                        )
                        Text(
                            entry.first,
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 9.sp
                        )
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .width(194.dp)
                    .height(9.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Brush.horizontalGradient(layer.legendStops()))
            )
            // 刻度取当前数据的 4 等分点：地图上什么颜色对应多少度，一眼能对上
            Row(
                Modifier.width(194.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                legendTicks(range).forEach { value ->
                    Text(
                        text = if (layer == WeatherMapLayer.TEMPERATURE) {
                            unit.formatWithUnit(value)
                        } else {
                            layer.compactLabel(value, unit) + unitSuffixOf(layer)
                        },
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

private val AqiLegend = listOf(
    "优" to Color(0xFF35C759),
    "良" to Color(0xFF9BD44F),
    "轻度" to Color(0xFFF5C542),
    "中度" to Color(0xFFF59E42),
    "重度" to Color(0xFFEB5757),
    "严重" to Color(0xFFB04A6E)
)

/** 取 4 个等分刻度；没有数据时退回图层的参考范围，保证图例不空着。 */
private fun legendTicks(range: ClosedFloatingPointRange<Double>?): List<Double> {
    val (start, end) = if (range != null && range.endInclusive > range.start) {
        range.start to range.endInclusive
    } else {
        return emptyList()
    }
    return (0..3).map { index -> start + (end - start) * index / 3.0 }
}

private fun unitSuffixOf(layer: WeatherMapLayer): String = when (layer) {
    WeatherMapLayer.PRECIPITATION -> ""
    WeatherMapLayer.TEMPERATURE -> ""
    WeatherMapLayer.AIR_QUALITY -> ""
    WeatherMapLayer.WIND -> " m/s"
}

@Composable
private fun LayerSwitcher(
    selected: WeatherMapLayer,
    onSelect: (WeatherMapLayer) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .floatingGlass(RoundedCornerShape(22.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WeatherMapLayer.entries.forEach { item ->
            val active = item == selected
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color.White.copy(alpha = 0.22f) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (active) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = item.label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun MapAnchorBubble(
    point: MapPointWeather,
    layer: WeatherMapLayer,
    unit: TemperatureUnit,
    inProgress: Boolean,
    errorMessage: String?,
    mapView: MapView,
    containerSize: IntSize,
    onDismiss: () -> Unit,
    onAddCity: () -> Unit
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 34.dp.toPx() }   // 让开下面的标注圈
    val tailHeight = 10.dp
    val marginPx = with(density) { 8.dp.toPx() }

    val anchor = remember(point, containerSize) {
        val projected = mapView.projection.toPixels(
            GeoPoint(point.latitude, point.longitude),
            null
        )
        Offset(projected.x.toFloat(), projected.y.toFloat())
    }

    var bubbleSize by remember { mutableStateOf(IntSize.Zero) }
    // 容器尺寸还没测量出来时先不画，避免第一帧闪到左上角
    if (containerSize.width == 0 || bubbleSize.width == 0) {
        Box(
            Modifier
                .offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) }
                .onSizeChanged { bubbleSize = it }
        ) {
            BubbleBody(point, layer, unit, inProgress, errorMessage, onDismiss, onAddCity)
        }
        return
    }

    val width = bubbleSize.width
    val height = bubbleSize.height

    // 水平：以锚点为中心，并夹在屏幕内
    val left = (anchor.x - width / 2f).coerceIn(
        marginPx,
        (containerSize.width - width - marginPx).coerceAtLeast(marginPx)
    )
    // 垂直：优先放在锚点上方；上方放不下就翻到下方
    val above = anchor.y - height - gapPx >= marginPx
    val top = if (above) anchor.y - height - gapPx else anchor.y + gapPx

    Column(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .onSizeChanged { bubbleSize = it }
    ) {
        if (above) {
            BubbleBody(point, layer, unit, inProgress, errorMessage, onDismiss, onAddCity)
            BubbleTail(tipX = anchor.x - left, pointingUp = false, height = tailHeight)
        } else {
            BubbleTail(tipX = anchor.x - left, pointingUp = true, height = tailHeight)
            BubbleBody(point, layer, unit, inProgress, errorMessage, onDismiss, onAddCity)
        }
    }
}

/** 气泡的小尖角，用同色填充让它看起来是气泡的一部分。 */
@Composable
private fun BubbleTail(tipX: Float, pointingUp: Boolean, height: Dp) {
    Canvas(Modifier.fillMaxWidth().height(height)) {
        val half = 9.dp.toPx()
        val x = tipX.coerceIn(half, (size.width - half).coerceAtLeast(half))
        val path = Path().apply {
            if (pointingUp) {
                moveTo(x, 0f)
                lineTo(x - half, size.height)
                lineTo(x + half, size.height)
            } else {
                moveTo(x, size.height)
                lineTo(x - half, 0f)
                lineTo(x + half, 0f)
            }
            close()
        }
        drawPath(path, LiquidGlass.floatingScrim)
    }
}

@Composable
private fun BubbleBody(
    point: MapPointWeather,
    layer: WeatherMapLayer,
    unit: TemperatureUnit,
    inProgress: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAddCity: () -> Unit
) {
    GlassGroup(modifier = Modifier.width(268.dp)) {
        GlassSpecular()

        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = SkyconIcons.icon(point.skycon, true),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    point.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = "${unit.formatWithUnit(point.temperature)} · ${point.skycon.text}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            Box(
                Modifier.size(28.dp).clip(CircleShape).clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        GlassHairLine(insetStart = 16.dp)

        // 四项指标一次列全，不用切换图层也能看全
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            WeatherMapLayer.entries.forEach { item ->
                val value = item.valueOf(point)
                val current = item == layer
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(if (current) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(item.colorFor(value))
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.label,
                        color = Color.White.copy(alpha = if (current) 0.95f else 0.6f),
                        fontSize = 12.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = item.formatValue(value, unit),
                        color = Color.White.copy(alpha = if (current) 1f else 0.8f),
                        fontSize = 13.sp,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        GlassHairLine(insetStart = 16.dp)

        Row(
            Modifier
                .fillMaxWidth()
                .clickable(enabled = !inProgress) { onAddCity() }
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (inProgress) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = LiquidGlass.accent,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text("加为城市", color = LiquidGlass.accent, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            errorMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFFFFD166),
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}
