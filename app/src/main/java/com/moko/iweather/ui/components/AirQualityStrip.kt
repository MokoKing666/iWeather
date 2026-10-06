package com.moko.iweather.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.core.AirQuality
import com.moko.iweather.core.AqiLevel
import com.moko.iweather.ui.theme.AqiGradientColors
import com.moko.iweather.ui.theme.aqiColor
import kotlin.math.roundToInt

/**
 * 空气质量色带条。
 *
 * 按 Apple 官方说明，这条会出现在**逐时预报的上方**（而不是单独一张卡片），
 * 所以这里做成窄条 + 渐变条 + 等级文案。
 */
@Composable
fun AirQualityStrip(
    airQuality: AirQuality,
    modifier: Modifier = Modifier
) {
    val aqi = airQuality.aqi ?: return
    val level: AqiLevel = airQuality.level ?: return
    val text = airQuality.category ?: level.text

    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppleMetrics.CardCorner))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.07f))
                )
            )
            .padding(horizontal = AppleMetrics.CardPadding, vertical = 11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "空气质量",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = aqi.roundToInt().toString(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Brush.horizontalGradient(AqiGradientColors.map { it.copy(alpha = 0.4f) }))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(markerFraction(aqi))
                    .height(5.dp)
                    .background(aqiColor(level))
            )
        }
    }
}

/** 国标分段：AQI 0~500 映射到 0~1。 */
private fun markerFraction(aqi: Double): Float {
    val bounds = listOf(0.0 to 50.0, 51.0 to 100.0, 101.0 to 150.0, 151.0 to 200.0, 201.0 to 300.0, 301.0 to 500.0)
    bounds.forEachIndexed { index, (low, high) ->
        if (aqi <= high) {
            val offset = ((aqi - low) / (high - low)).coerceIn(0.0, 1.0)
            return ((index + offset) / bounds.size).toFloat()
        }
    }
    return 1f
}
