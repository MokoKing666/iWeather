package com.moko.iweather.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.core.DewPoint
import com.moko.iweather.core.LunarCalendar
import com.moko.iweather.core.MoonCalculator
import com.moko.iweather.core.SkyInfo
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.core.WeatherSnapshot
import kotlin.math.roundToInt

/**
 * 「太阳与月亮」卡片：露点、月相、月出月落、农历。
 *
 * 这四项**全部是本地计算**，不需要任何网络请求 ——
 * 露点用 Magnus 公式（温度 + 湿度），月亮用低精度星历，
 * 农历查表（1900–2100）。逻辑在 `core/Astronomy.kt`，由 `AstronomyTest` 锁住。
 *
 * 计算放在 `remember` 里：月亮那套要扫一天的整日高度角（720 次三角运算），
 * 每次重组都算一遍是浪费；只有城市或日期变了才重算。
 */
@Composable
fun SkyCardContent(
    snapshot: WeatherSnapshot?,
    city: WeatherCity,
    unit: TemperatureUnit,
    now: Long,
    modifier: Modifier = Modifier
) {
    val realtime = snapshot?.realtime

    val sky: SkyInfo? = remember(city.id, city.timeZoneId, now / 3_600_000L) {
        runCatching {
            val zone = TimeZoneResolver.zone(city.timeZoneId)
            val calendar = java.util.Calendar.getInstance(zone).apply { timeInMillis = now }
            val today = snapshot?.today(city.timeZoneId, now)

            SkyInfo(
                moon = MoonCalculator.info(now, city.latitude, city.longitude, zone),
                lunar = LunarCalendar.fromCalendar(calendar),
                sunrise = today?.sunrise,
                sunset = today?.sunset
            )
        }.getOrNull()
    }

    val dewPoint = remember(realtime?.temperature, realtime?.humidity) {
        val temperature = realtime?.temperature ?: return@remember null
        DewPoint.celsiusFromRatio(temperature, realtime.humidity)
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {

        // ---------------------------------------- 月相 + 农历
        sky?.let { info ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = info.moon.phase.symbol, fontSize = 34.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = info.moon.phase.text,
                        color = Color.White,
                        fontSize = 17.sp
                    )
                    Text(
                        text = buildString {
                            append("照明 ${(info.moon.illumination * 100).roundToInt()}%")
                            append(" · 月龄 ${"%.1f".format(info.moon.ageDays)} 天")
                        },
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp
                    )
                }
                info.lunar?.let { lunar ->
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = lunar.fullText,
                            color = Color.White,
                            fontSize = 15.sp
                        )
                        Text(
                            text = lunar.festival ?: "农历${lunar.year}年",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
            GlassHairLine(insetStart = 0.dp)
        }

        // ---------------------------------------- 数值栏
        Row(Modifier.fillMaxWidth()) {
            SkyMetric(
                label = "露点",
                value = unit.formatWithUnit(dewPoint),
                hint = dewHint(dewPoint, realtime?.temperature),
                modifier = Modifier.weight(1f)
            )
            SkyMetric(
                label = "月出",
                value = sky?.moon?.moonrise
                    ?.let { TimeZoneResolver.formatTime(it, city.timeZoneId) } ?: "--",
                modifier = Modifier.weight(1f)
            )
            SkyMetric(
                label = "月落",
                value = sky?.moon?.moonset
                    ?.let { TimeZoneResolver.formatTime(it, city.timeZoneId) } ?: "--",
                modifier = Modifier.weight(1f)
            )
        }

        // ---------------------------------------- 下一次满月 / 新月
        sky?.let { info ->
            Text(
                text = "下次满月 ${TimeZoneResolver.formatDate(info.moon.nextFullMoon, city.timeZoneId, "M月d日")}" +
                    " · 下次新月 ${TimeZoneResolver.formatDate(info.moon.nextNewMoon, city.timeZoneId, "M月d日")}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SkyMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp
        )
        Text(text = value, color = Color.White, fontSize = 18.sp)
        Text(
            text = hint ?: " ",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 10.sp,
            maxLines = 1
        )
    }
}

/**
 * 给露点一句人话解释 —— 光有数字没人知道意味着什么。
 * 判据是"露点与气温的差"，不是露点本身：
 * 差越小越接近饱和，体感越闷。
 */
private fun dewHint(dewPoint: Double?, temperature: Double?): String? {
    if (dewPoint == null || temperature == null) return null
    val spread = temperature - dewPoint
    return when {
        spread < 2 -> "很潮湿，接近饱和"
        spread < 5 -> "潮湿，体感闷"
        spread < 10 -> "舒适"
        spread < 15 -> "干爽"
        else -> "非常干燥"
    }
}
