package com.moko.iweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.core.HourlyPoint
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.ui.weather.SkyconIcons

/** 逐时/逐日预报的显示维度（Apple 官方：点符号在逐时预报上方切换）。 */
enum class ForecastMode(val label: String, val icon: ImageVector) {
    CONDITIONS("天气状况", Icons.Filled.WbSunny),
    PRECIPITATION("降水", Icons.Filled.WaterDrop),
    WIND("风", Icons.Filled.Air)
}

private object HourlyLayout {
    const val ITEM_WIDTH = 52
    const val SPACING = 6
    const val TIME_HEIGHT = 18
    const val ICON_HEIGHT = 26
    const val VALUE_HEIGHT = 18
    const val CURVE_HEIGHT = 34

    val CURVE_TOP_OFFSET: Int =
        TIME_HEIGHT + SPACING + ICON_HEIGHT + SPACING + VALUE_HEIGHT + SPACING
}

/** 上方那排视图切换按钮。 */
@Composable
fun ForecastModeSwitcher(
    selected: ForecastMode,
    onSelect: (ForecastMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ForecastMode.entries.forEach { mode ->
            val active = mode == selected
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(
                        if (active) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    mode.icon,
                    contentDescription = mode.label,
                    tint = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    mode.label,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/**
 * 逐时预报。
 *
 * 三种视图共用同一套格子骨架，高度全部固定，所以温度曲线能和格子严格对齐。
 */
@Composable
fun HourlyStrip(
    hours: List<HourlyPoint>,
    mode: ForecastMode,
    unit: TemperatureUnit,
    timeZoneId: String,
    isDaytime: Boolean,
    modifier: Modifier = Modifier
) {
    if (hours.isEmpty()) {
        Text(
            "暂无逐小时数据",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        return
    }

    val totalWidth = (hours.size * HourlyLayout.ITEM_WIDTH).dp

    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Box(Modifier.width(totalWidth)) {
            Row(Modifier.padding(horizontal = 2.dp)) {
                hours.forEachIndexed { index, hour -> cell(hour, index, mode, unit, timeZoneId, isDaytime) }
            }

            if (mode == ForecastMode.CONDITIONS) {
                TemperatureCurve(
                    values = hours.map { it.temperature },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = HourlyLayout.CURVE_TOP_OFFSET.dp)
                )
            } else {
                ValueBars(
                    values = when (mode) {
                        ForecastMode.PRECIPITATION -> hours.map {
                            it.precipProbability ?: it.precipitationIntensity
                        }
                        else -> hours.map { it.windSpeedMs?.div(30.0) }
                    },
                    color = when (mode) {
                        ForecastMode.PRECIPITATION -> Color(0xFF6FD3FF)
                        else -> Color.White.copy(alpha = 0.75f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(y = HourlyLayout.CURVE_TOP_OFFSET.dp)
                )
            }
        }
    }
}

@Composable
private fun cell(
    hour: HourlyPoint,
    index: Int,
    mode: ForecastMode,
    unit: TemperatureUnit,
    timeZoneId: String,
    isDaytime: Boolean
) {
    Column(
        modifier = Modifier.width(HourlyLayout.ITEM_WIDTH.dp),
        verticalArrangement = Arrangement.spacedBy(HourlyLayout.SPACING.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (index == 0) "现在" else hourLabel(hour.time, timeZoneId),
            color = if (index == 0) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier.height(HourlyLayout.TIME_HEIGHT.dp)
        )

        when (mode) {
            ForecastMode.CONDITIONS -> Icon(
                imageVector = SkyconIcons.icon(hour.skycon, isDaytime),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(HourlyLayout.ICON_HEIGHT.dp - 4.dp)
            )

            ForecastMode.PRECIPITATION -> Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                tint = Color(0xFF6FD3FF),
                modifier = Modifier.size(HourlyLayout.ICON_HEIGHT.dp - 6.dp)
            )

            ForecastMode.WIND -> Icon(
                imageVector = Icons.Filled.Air,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(HourlyLayout.ICON_HEIGHT.dp - 6.dp)
            )
        }

        // 第二行数值：随视图切换内容
        Text(
            text = when (mode) {
                ForecastMode.CONDITIONS ->
                    unit.formatProbability(hour.precipProbability).takeIf { it != "--" } ?: " "
                ForecastMode.PRECIPITATION ->
                    unit.formatProbability(hour.precipProbability)
                ForecastMode.WIND ->
                    hour.windSpeedMs?.let { "${it.toInt()}" } ?: "--"
            },
            color = when (mode) {
                ForecastMode.PRECIPITATION -> Color(0xFF6FD3FF)
                else -> Color.White
            },
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.height(HourlyLayout.VALUE_HEIGHT.dp)
        )

        // 给曲线 / 柱状图留出的固定空间
        Spacer(Modifier.height(HourlyLayout.CURVE_HEIGHT.dp))
    }
}

@Composable
private fun TemperatureCurve(values: List<Double?>, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .width((values.size * HourlyLayout.ITEM_WIDTH).dp)
            .height(HourlyLayout.CURVE_HEIGHT.dp)
    ) {
        val known = values.filterNotNull()
        if (known.size < 2) return@Canvas

        val minValue = known.min()
        val maxValue = known.max()
        val span = (maxValue - minValue).coerceAtLeast(1.0)
        val usable = (HourlyLayout.CURVE_HEIGHT - 10).coerceAtLeast(1)
        val step = HourlyLayout.ITEM_WIDTH.dp.toPx()

        fun pointAt(index: Int, value: Double) = Offset(
            x = index * step + step / 2f,
            y = 5f + usable * (1f - ((value - minValue) / span).toFloat())
        )

        val segments = mutableListOf<List<Offset>>()
        var segment = mutableListOf<Offset>()
        values.forEachIndexed { index, value ->
            if (value == null) {
                if (segment.size >= 2) segments += segment
                segment = mutableListOf()
            } else {
                segment += pointAt(index, value)
            }
        }
        if (segment.size >= 2) segments += segment

        segments.forEach { points ->
            drawPath(
                path = smoothPath(points),
                color = Color.White,
                style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
        values.forEachIndexed { index, value ->
            if (value != null) drawCircle(Color.White, radius = 2.6f, center = pointAt(index, value))
        }
    }
}

/** 降水 / 风速用的竖条。 */
@Composable
private fun ValueBars(values: List<Double?>, color: Color, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .width((values.size * HourlyLayout.ITEM_WIDTH).dp)
            .height(HourlyLayout.CURVE_HEIGHT.dp)
    ) {
        val known = values.filterNotNull()
        if (known.isEmpty()) return@Canvas
        val maxValue = known.max().coerceAtLeast(0.0001)
        val step = HourlyLayout.ITEM_WIDTH.dp.toPx()
        val barWidth = step * 0.34f

        values.forEachIndexed { index, value ->
            if (value == null) return@forEachIndexed
            val ratio = (value / maxValue).coerceIn(0.0, 1.0).toFloat()
            val barHeight = (ratio * (size.height - 4f)).coerceAtLeast(2f)
            drawRoundRect(
                color = color.copy(alpha = 0.35f + 0.65f * ratio),
                topLeft = Offset(index * step + (step - barWidth) / 2f, size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 2) {
        path.lineTo(points[1].x, points[1].y)
        return path
    }
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f,
            p2.x, p2.y
        )
    }
    return path
}

private fun hourLabel(time: Long, timeZoneId: String): String =
    TimeZoneResolver.formatTime(time, timeZoneId, "H时")
