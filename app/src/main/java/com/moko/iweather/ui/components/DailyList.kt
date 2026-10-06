package com.moko.iweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.core.DailyPoint
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.ui.weather.SkyconIcons
import kotlin.math.roundToInt

/**
 * 逐日预报（10 天）。
 *
 * 复刻 Apple 的行结构：日期 · 图标 · 降水% · 最低温 · 温度区间条 · 最高温。
 * 区间条按全周极值归一化，颜色沿用 Apple 的冷→暖过渡。
 */
@Composable
fun DailyList(
    days: List<DailyPoint>,
    mode: ForecastMode,
    unit: TemperatureUnit,
    timeZoneId: String,
    modifier: Modifier = Modifier
) {
    if (days.isEmpty()) {
        Text(
            "暂无逐日数据",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            modifier = modifier.fillMaxWidth().padding(vertical = 16.dp)
        )
        return
    }

    val lows = days.mapNotNull { it.temperatureMin }
    val highs = days.mapNotNull { it.temperatureMax }
    val base = lows.minOrNull() ?: 0.0
    val span = ((highs.maxOrNull() ?: 1.0) - base).coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        days.forEachIndexed { index, day ->
            when (mode) {
                ForecastMode.CONDITIONS -> ConditionRow(
                    day = day,
                    unit = unit,
                    timeZoneId = timeZoneId,
                    isToday = index == 0,
                    base = base,
                    span = span
                )

                ForecastMode.PRECIPITATION -> PrecipitationRow(day, unit, index == 0, timeZoneId)

                ForecastMode.WIND -> WindRow(day, index == 0, timeZoneId)
            }
            if (index < days.lastIndex) {
                Spacer(Modifier.height(9.dp))
                HairLineDivider()
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun ConditionRow(
    day: DailyPoint,
    unit: TemperatureUnit,
    timeZoneId: String,
    isToday: Boolean,
    base: Double,
    span: Double
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel(day.date, timeZoneId, isToday),
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1
        )

        Row(
            Modifier.width(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = SkyconIcons.icon(day.skyconDay, true),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            val probability = unit.formatProbability(day.precipProbability)
            if (probability != "--") {
                Text(
                    probability,
                    color = Color(0xFF6FD3FF),
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }

        Text(
            text = unit.format(day.temperatureMin),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 17.sp,
            modifier = Modifier.width(34.dp),
            maxLines = 1
        )

        TemperatureRangeBar(day, base, span, Modifier.weight(1f))

        Text(
            text = unit.format(day.temperatureMax),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(34.dp),
            maxLines = 1
        )
    }
}

@Composable
private fun PrecipitationRow(day: DailyPoint, unit: TemperatureUnit, isToday: Boolean, timeZoneId: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel(day.date, timeZoneId, isToday),
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1
        )
        Icon(
            Icons.Filled.WaterDrop,
            contentDescription = null,
            tint = Color(0xFF6FD3FF),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = unit.formatProbability(day.precipProbability),
            color = Color(0xFF6FD3FF),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = day.precipMax?.let { "峰值 ${(it * 100).roundToInt()}%" } ?: "--",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp
        )
    }
}

@Composable
private fun WindRow(day: DailyPoint, isToday: Boolean, timeZoneId: String) {
    // 彩云的逐天数据里没有风速，这里用云量/降水强度之外的可用字段兜底为"无数据"提示
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel(day.date, timeZoneId, isToday),
            color = Color.White,
            fontSize = 17.sp,
            modifier = Modifier.width(52.dp),
            maxLines = 1
        )
        Icon(
            Icons.Filled.Air,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = "彩云逐天接口未提供风速",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TemperatureRangeBar(
    day: DailyPoint,
    base: Double,
    span: Double,
    modifier: Modifier = Modifier
) {
    Canvas(modifier.height(5.dp)) {
        fun ratio(value: Double?): Float =
            value?.let { (((it - base) / span).coerceIn(0.0, 1.0)).toFloat() } ?: 0f

        val start = minOf(ratio(day.temperatureMin), ratio(day.temperatureMax))
        val end = maxOf(ratio(day.temperatureMin), ratio(day.temperatureMax))
        val radius = CornerRadius(size.height / 2f, size.height / 2f)

        drawRoundRect(color = Color.White.copy(alpha = 0.18f), cornerRadius = radius)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                listOf(
                    Color(0xFF5AC8FA), Color(0xFF7ED9A0), Color(0xFFFFD666),
                    Color(0xFFFFA64D), Color(0xFFFF6B5B)
                )
            ),
            topLeft = Offset(start * size.width, 0f),
            size = Size(
                width = ((end - start) * size.width).coerceAtLeast(size.height),
                height = size.height
            ),
            cornerRadius = radius
        )
    }
}

@Composable
private fun HairLineDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(0.7.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

private fun dayLabel(date: Long, timeZoneId: String, isToday: Boolean): String {
    if (isToday) return "今天"
    val zone = TimeZoneResolver.zone(timeZoneId)
    val now = java.util.Calendar.getInstance(zone)
    val target = java.util.Calendar.getInstance(zone).apply { timeInMillis = date }
    val dayDiff = Math.round((target.timeInMillis - now.timeInMillis) / 86_400_000.0).toInt()
    return when (dayDiff) {
        1 -> "明天"
        2 -> "后天"
        else -> TimeZoneResolver.formatDate(date, timeZoneId, "EEE")
    }
}
