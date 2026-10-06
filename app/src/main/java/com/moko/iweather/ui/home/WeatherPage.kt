package com.moko.iweather.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moko.iweather.core.AlertLevel
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.core.WeatherAlert
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.core.WeatherSnapshot
import com.moko.iweather.ui.components.AppleSectionTitle
import com.moko.iweather.ui.components.SkyCardContent
import com.moko.iweather.ui.components.AirQualityStrip
import com.moko.iweather.ui.components.WeatherGlassCard
import com.moko.iweather.ui.components.AppleMetrics
import com.moko.iweather.ui.components.DailyList
import com.moko.iweather.ui.components.DetailGrid
import com.moko.iweather.ui.components.ForecastMode
import com.moko.iweather.ui.components.ForecastModeSwitcher
import com.moko.iweather.ui.components.GlassCircleButton
import com.moko.iweather.ui.components.HourlyStrip
import com.moko.iweather.ui.theme.alertColor
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * 单个城市页，尽量按 Apple Weather 的版式还原：
 *
 *   城市名
 *   23°
 *   晴
 *   最高 24° 最低 15°
 *   ── 空气质量色带条（官方：位于逐时预报上方）
 *   ── [天气状况|降水|风]  ← 官方：点符号在逐时预报上方切换
 *   ── 逐时预报（温度曲线）
 *   ── 天气详情（体感/湿度/能见度/风/气压/降水/紫外线/日出日落）
 *   ── 10 天预报
 *   ── 天气预警（仅在有预警时出现）
 *
 * Apple 主页**不放**自然语言摘要，所以这里也不放（数据仍保留在小组件与设置里）。
 */
@Composable
fun WeatherPageContent(
    city: WeatherCity,
    snapshot: WeatherSnapshot?,
    unit: TemperatureUnit,
    isDaytime: Boolean,
    isLoading: Boolean,
    errorMessage: String?,
    sunrise: Long?,
    sunset: Long?,
    nextTransition: Long?,
    transitionIsSunrise: Boolean,
    onOpenCityList: () -> Unit,
    particleStyle: com.moko.iweather.ui.weather.ParticleStyle,
    particleProgress: Float,
    fieldWidth: Float,
    fieldHeight: Float,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forecastMode by remember { mutableStateOf(ForecastMode.CONDITIONS) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val collapse by animateFloatAsState(
        targetValue = (scrollState.value / 150f).coerceIn(0f, 1f),
        label = "collapse"
    )

    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = AppleMetrics.ScreenPadding)
            .padding(top = 8.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(AppleMetrics.CardSpacing)
    ) {
        // ------------------------------------------------ 顶部大标题
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // 城市名可点：Apple Weather 就是点顶部城市名展开城市列表
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onOpenCityList() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .graphicsLayer {
                        val scale = 1f - 0.18f * collapse
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                if (city.isCurrentLocation) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = "我的位置",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(15.dp)
                    )
                }
                Text(
                    text = city.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "切换城市",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(2.dp))

            // 大温度：超细字重、紧字距
            Crossfade(targetState = unit.format(snapshot?.realtime?.temperature), label = "temp") { value ->
                Text(
                    text = value,
                    color = Color.White,
                    fontSize = 92.sp,
                    fontWeight = FontWeight.Thin,
                    letterSpacing = (-4).sp,
                    lineHeight = 96.sp,
                    modifier = Modifier.graphicsLayer {
                        val scale = 1f - 0.22f * collapse
                        scaleX = scale
                        scaleY = scale
                    }
                )
            }

            Text(
                text = snapshot?.realtime?.skycon?.text ?: if (isLoading) "正在获取…" else "--",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            val today = snapshot?.today(city.timeZoneId, now)
            if (today?.temperatureMax != null || today?.temperatureMin != null) {
                Text(
                    text = "最高 ${unit.format(today?.temperatureMax)}  最低 ${unit.format(today?.temperatureMin)}",
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 17.sp
                )
            }

            if (isLoading) {
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            }
            if (errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFFFD166),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ------------------------------------------------ 空气质量色带（逐时上方）
        snapshot?.airQuality?.let { AirQualityStrip(it) }

        // ------------------------------------------------ 太阳与月亮
        // 露点、月相、月出月落、农历 —— 全是本地计算，不占任何请求配额
        WeatherGlassCard(particleStyle, particleProgress, fieldWidth, fieldHeight) {
            AppleSectionTitle("太阳与月亮")
            Spacer(Modifier.height(12.dp))
            SkyCardContent(
                snapshot = snapshot,
                city = city,
                unit = unit,
                now = now
            )
        }

        // ------------------------------------------------ 视图切换 + 逐时
        ForecastModeSwitcher(selected = forecastMode, onSelect = { forecastMode = it })

        WeatherGlassCard(particleStyle, particleProgress, fieldWidth, fieldHeight) {
            HourlyStrip(
                hours = snapshot?.hourly?.take(24).orEmpty(),
                mode = forecastMode,
                unit = unit,
                timeZoneId = city.timeZoneId,
                isDaytime = isDaytime
            )
        }

        // ------------------------------------------------ 天气详情
        WeatherGlassCard(particleStyle, particleProgress, fieldWidth, fieldHeight) {
            DetailGrid(
                realtime = snapshot?.realtime ?: com.moko.iweather.core.Realtime.EMPTY,
                unit = unit,
                timeZoneId = city.timeZoneId,
                sunrise = sunrise,
                sunset = sunset
            )
        }

        // ------------------------------------------------ 10 天
        WeatherGlassCard(particleStyle, particleProgress, fieldWidth, fieldHeight) {
            AppleSectionTitleCompat("未来 10 天")
            Spacer(Modifier.height(10.dp))
            DailyList(
                days = snapshot?.daily.orEmpty(),
                mode = forecastMode,
                unit = unit,
                timeZoneId = city.timeZoneId
            )
        }

        // ------------------------------------------------ 生活指数
        snapshot?.daily?.firstOrNull()?.let { day ->
            val items = listOf(
                "紫外线" to day.uvText,
                "穿衣" to day.dressingText,
                "舒适" to day.comfortText,
                "洗车" to day.carWashText
            ).mapNotNull { (label, value) -> value?.let { label to it } }
            if (items.isNotEmpty()) {
                WeatherGlassCard(particleStyle, particleProgress, fieldWidth, fieldHeight) {
                    AppleSectionTitleCompat("生活指数")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.chunked(2).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { (label, value) ->
                                    AppleDetailTileCompat(label, value, Modifier.weight(1f))
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ 预警
        val alerts = snapshot?.alerts.orEmpty()
        if (alerts.isNotEmpty()) {
            AlertsBanner(alerts, city.timeZoneId)
        }

        // ------------------------------------------------ 昼夜提示 + 数据来源
        Column(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (nextTransition != null) {
                val minutes = ((nextTransition - now) / 60_000).coerceAtLeast(0)
                Text(
                    text = "${if (transitionIsSunrise) "日出" else "日落"}还有 " +
                        if (minutes < 60) "$minutes 分钟" else "${minutes / 60} 小时 ${minutes % 60} 分钟",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }
            Text(
                text = "当地时间 ${TimeZoneResolver.formatTime(now, city.timeZoneId)} · " +
                    (snapshot?.let { "${TimeZoneResolver.formatTime(it.fetchedAt, city.timeZoneId)} 更新" } ?: "未更新"),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Text(
                text = "天气数据 彩云天气 v2.6",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp
            )
        }
    }
}

/** 预警横幅：Apple 用的是醒目的橙色/红色圆角条。 */
@Composable
private fun AlertsBanner(alerts: List<WeatherAlert>, timeZoneId: String) {
    val alert = alerts.first()
    val color = alertColor(alert.level)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppleMetrics.CardCorner))
            .background(color.copy(alpha = 0.22f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = listOfNotNull(alert.level.text.takeIf { it != "未知" }, alert.type.takeIf { it.isNotBlank() })
                    .joinToString("预警"),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            alert.publishedAt?.let {
                Text(
                    text = TimeZoneResolver.formatDate(it, timeZoneId, "M/d HH:mm"),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
        Text(
            text = alert.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (alert.body.isNotBlank()) {
            Text(
                text = alert.body,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (alerts.size > 1) {
            Text(
                text = "另有 ${alerts.size - 1} 条预警",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AppleSectionTitleCompat(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.72f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun AppleDetailTileCompat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(text = value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
