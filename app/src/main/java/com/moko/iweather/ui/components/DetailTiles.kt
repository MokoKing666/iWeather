package com.moko.iweather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moko.iweather.core.Realtime
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import kotlin.math.roundToInt

/**
 * 天气详情网格。
 *
 * Apple 的排布是 2 列 × 若干行的小格：全大写小标题 + 大号数值，
 * 顺序为 体感 / 湿度 / 能见度 / 风 / 气压 / 降水 / 紫外线 / 日出日落。
 */
@Composable
fun DetailGrid(
    realtime: Realtime,
    unit: TemperatureUnit,
    timeZoneId: String,
    sunrise: Long?,
    sunset: Long?,
    modifier: Modifier = Modifier
) {
    data class Tile(
        val label: String,
        val value: String,
        val valueColor: Color = Color.White
    )

    val tiles = buildList {
        add(Tile("体感温度", unit.format(realtime.apparentTemperature)))
        add(Tile("湿度", realtime.humidity?.let { "${(it * 100).roundToInt()}%" } ?: "--"))
        add(Tile("能见度", realtime.visibilityKm?.let { "${it.roundToInt()} km" } ?: "--"))
        add(Tile("风", realtime.windSpeedMs?.let { "${it.roundToInt()} m/s" } ?: "--"))
        add(Tile("气压", realtime.pressureHpa?.let { "${it.roundToInt()} hPa" } ?: "--"))
        add(
            Tile(
                "降水",
                realtime.precipitationIntensity?.let { precipitationText(it) } ?: "--",
                if ((realtime.precipitationIntensity ?: 0.0) > 0.05) Color(0xFF6FD3FF) else Color.White
            )
        )
        add(Tile("紫外线", realtime.uvText ?: realtime.uvIndex?.let { "${it.roundToInt()} 级" } ?: "--"))
        add(
            Tile(
                "日出日落",
                if (sunrise != null && sunset != null) {
                    "${TimeZoneResolver.formatTime(sunrise, timeZoneId)}  " +
                        TimeZoneResolver.formatTime(sunset, timeZoneId)
                } else {
                    "--"
                }
            )
        )
        realtime.comfortText?.let { add(Tile("舒适度", it)) }
        realtime.solarRadiation?.let { add(Tile("太阳辐射", "${it.roundToInt()} W/m²")) }
        realtime.cloudRate?.let { add(Tile("云量", "${(it * 100).roundToInt()}%")) }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    AppleDetailTile(
                        label = tile.label,
                        value = tile.value,
                        valueColor = tile.valueColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun precipitationText(intensity: Double): String = when {
    intensity <= 0.001 -> "无降水"
    intensity < 0.1 -> "弱降水"
    intensity < 0.4 -> "中等降水"
    intensity < 0.7 -> "强降水"
    else -> "极强降水"
}
