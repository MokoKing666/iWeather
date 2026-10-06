package com.moko.iweather.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize

import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.moko.iweather.MainActivity
import com.moko.iweather.core.DailyPoint
import com.moko.iweather.core.Prefs
import com.moko.iweather.core.SkyconFamily
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.TimeZoneResolver
import com.moko.iweather.core.WeatherCity
import com.moko.iweather.core.WeatherSnapshot
import com.moko.iweather.core.WidgetPreference
import com.moko.iweather.ui.weather.AppleWeatherTheme
import kotlin.math.roundToInt

/**
 * 三种布局的桌面小组件：2×2 / 3×2 / 4×2。
 *
 * 数据来自 App 写入的 SharedPreferences（同一进程、同一个文件），小组件自身不联网，
 * 所以离线时也能显示最后一次的天气。点按会拉起 App 并切到对应城市。
 */
abstract class BaseWeatherWidget(private val layout: WidgetPreference.Layout) : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = Prefs(context).widgetData()
        val resolved = data.resolve(layout)
        val city = resolved?.first
        val snapshot = resolved?.second
        val unit = data.settings.temperatureUnit

        val isDaytime = snapshot?.let { estimateDaytime(it.timeZoneId, it.fetchedAt) } ?: true
        val theme = AppleWeatherTheme.of(
            family = snapshot?.realtime?.skycon?.family ?: SkyconFamily.UNKNOWN,
            isDaytime = isDaytime
        )

        val cityId = city?.id.orEmpty()

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(ImageProvider(gradientBitmap(theme.colors)))
                    .padding(12.dp)
                    .clickable(
                        actionRunCallback<OpenAppAction>(
                            actionParametersOf(OpenAppAction.KEY_CITY_ID to cityId)
                        )
                    )
            ) {
                val target = city
                if (target != null) {
                    WidgetBody(
                        layout = layout,
                        city = target,
                        snapshot = snapshot,
                        unit = unit
                    )
                } else {
                    Text(
                        text = "请先打开 iWeather 添加城市",
                        style = TextStyle(color = ColorProvider(Color.White), fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

/** 小组件里没有 ViewModel，用城市当地钟点粗判昼夜（缓存里已带日出日落时会更准）。 */
private fun estimateDaytime(timeZoneId: String, now: Long): Boolean =
    TimeZoneResolver.hourOfDay(now, timeZoneId) in 6..17

private val White = ColorProvider(Color.White)
private val Dim = ColorProvider(Color.White.copy(alpha = 0.72f))

@Composable
private fun WidgetBody(
    layout: WidgetPreference.Layout,
    city: WeatherCity,
    snapshot: WeatherSnapshot?,
    unit: TemperatureUnit
) {
    val now = System.currentTimeMillis()
    val today = snapshot?.today(city.timeZoneId, now)
    val small = layout == WidgetPreference.Layout.SMALL

    Column(GlanceModifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (city.isCurrentLocation) "📍 ${city.name}" else city.name,
                style = TextStyle(color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(4.dp))

        Text(
            text = "${unit.format(snapshot?.realtime?.temperature)}${unit.symbol}",
            style = TextStyle(color = White, fontSize = if (small) 30.sp else 34.sp)
        )
        Text(
            text = snapshot?.realtime?.skycon?.text ?: "--",
            style = TextStyle(color = Dim, fontSize = 12.sp)
        )

        if (!small) {
            Text(
                text = "↑${unit.format(today?.temperatureMax)}  ↓${unit.format(today?.temperatureMin)}",
                style = TextStyle(color = Dim, fontSize = 11.sp)
            )
        }

        snapshot?.alerts?.firstOrNull()?.let { alert ->
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = "⚠ ${alert.title}",
                style = TextStyle(color = White, fontSize = 11.sp),
                maxLines = 1
            )
        }

        // 3×2 / 4×2：未来 6 小时
        if (!small) {
            val hours = snapshot?.hourly.orEmpty().take(6)
            if (hours.isNotEmpty()) {
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    hours.forEachIndexed { index, hour ->
                        Column(
                            modifier = GlanceModifier.width(44.dp),
                            horizontalAlignment = Alignment.Horizontal.CenterHorizontally
                        ) {
                            Text(
                                text = if (index == 0) "现在" else TimeZoneResolver.formatTime(hour.time, city.timeZoneId, "H时"),
                                style = TextStyle(color = Dim, fontSize = 10.sp)
                            )
                            Text(
                                text = unit.format(hour.temperature),
                                style = TextStyle(color = White, fontSize = 12.sp)
                            )
                        }
                    }
                }
            }
        }

        // 4×2：再加未来几天
        if (layout == WidgetPreference.Layout.LARGE) {
            snapshot?.daily.orEmpty().take(4).forEachIndexed { index, day ->
                Spacer(GlanceModifier.height(4.dp))
                ForecastRow(index, day, unit, city.timeZoneId)
            }
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            snapshot?.airQuality?.aqi?.let {
                Text("AQI ${it.roundToInt()}", style = TextStyle(color = Dim, fontSize = 10.sp))
                Spacer(GlanceModifier.width(8.dp))
            }
            snapshot?.fetchedAt?.let {
                Text(
                    text = "${TimeZoneResolver.formatTime(it, city.timeZoneId)} 更新",
                    style = TextStyle(color = Dim, fontSize = 10.sp)
                )
            }
        }
    }
}

@Composable
private fun ForecastRow(index: Int, day: DailyPoint, unit: TemperatureUnit, timeZoneId: String) {
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (index == 0) "今天" else TimeZoneResolver.formatDate(day.date, timeZoneId, "EEE"),
            style = TextStyle(color = Dim, fontSize = 11.sp),
            modifier = GlanceModifier.width(40.dp)
        )
        Text(
            text = "${unit.format(day.temperatureMin)} ~ ${unit.format(day.temperatureMax)}",
            style = TextStyle(color = White, fontSize = 11.sp)
        )
    }
}

/** 把渐变画成位图，作为小组件背景。Glance 没有渐变背景 API。 */
private fun gradientBitmap(colors: List<Color>, height: Int = 480): Bitmap {
    val bitmap = Bitmap.createBitmap(8, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint()
    paint.shader = LinearGradient(
        0f, 0f, 0f, height.toFloat(),
        colors.map { it.toArgb() }.toIntArray(),
        null,
        Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, 8f, height.toFloat(), paint)
    return bitmap
}

/** 点按小组件：拉起 App 并切到对应城市（iweather://city/<cityId>）。 */
class OpenAppAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cityId = parameters[KEY_CITY_ID].orEmpty()
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("iweather://city/$cityId"),
            context,
            MainActivity::class.java
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
    }

    companion object {
        val KEY_CITY_ID: ActionParameters.Key<String> = ActionParameters.Key("cityId")
    }
}

/** 2×2 */
class WeatherSmallWidget : BaseWeatherWidget(WidgetPreference.Layout.SMALL)

/** 3×2 */
class WeatherMediumWidget : BaseWeatherWidget(WidgetPreference.Layout.MEDIUM)

/** 4×2 */
class WeatherLargeWidget : BaseWeatherWidget(WidgetPreference.Layout.LARGE)

class WeatherWidgetSmallReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherSmallWidget()
}

class WeatherWidgetMediumReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherMediumWidget()
}

class WeatherWidgetLargeReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherLargeWidget()
}
