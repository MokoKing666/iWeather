package com.moko.iweather.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import com.moko.iweather.core.CmaStation
import com.moko.iweather.core.MapPointWeather
import com.moko.iweather.core.TemperatureUnit
import com.moko.iweather.core.WeatherMapLayer
import com.moko.iweather.core.colorFor
import com.moko.iweather.core.compactLabel
import com.moko.iweather.core.valueOf
import androidx.compose.ui.graphics.toArgb

/**
 * 地图上的天气图层，用 **osmdroid 的 Overlay** 画，而不是 Compose Canvas。
 *
 * ### 为什么不能用 Compose Canvas（踩过的坑）
 * 最初这两个图层是画在叠在 `AndroidView` 上的 Compose `Canvas` 里的，
 * 靠一个 `mapTick` 状态在 `MapListener.onScroll/onZoom` 里自增来触发重绘。
 * 结果是**地图缩放时点位不跟随** —— 这条链路（地图回调 → 状态变更 →
 * 重组 → 重建 draw lambda → 重绘）每一环都可能被合并或延后，
 * 地图的绘制节奏和 Compose 的重组节奏根本不是同一套东西。
 *
 * Overlay 则是由 `MapView` 自己的绘制循环在**每一帧**调用的，
 * 传入的 canvas 已经是屏幕坐标系，投影随手可取，天然同步。
 *
 * ### 注意 draw 的重载
 * osmdroid 的 `Overlay.draw(Canvas, MapView)` 默认实现就是
 * `draw(c, osmv, false)`。所以这里只覆写三参数版本，
 * 无论地图走哪条调用路径都会进到这里。
 */
private const val MAX_DRAWN_STATIONS = 1400

/** 全国站点热力场。 */
class StationFieldOverlay(
    private val stationsProvider: () -> List<CmaStation>,
    private val layerProvider: () -> WeatherMapLayer
) : Overlay() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    @Deprecated("osmdroid 的三参数版本仍然是实际被调用的入口")
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val layer = layerProvider()
        // 全国图不含空气质量，那一层只有城市标注，没有场
        if (layer == WeatherMapLayer.AIR_QUALITY) return

        val stations = stationsProvider()
        if (stations.isEmpty()) return

        val box = mapView.boundingBox ?: return
        val longitudeSpan = box.lonEast - box.lonWest
        if (longitudeSpan <= 0.0) return

        val width = mapView.width.toFloat()
        if (width <= 0f) return

        // 半径按"每度经度占多少像素"推算，保证相邻站点色斑互相重叠、连成色场
        val pixelsPerDegree = width / longitudeSpan
        val radius = (pixelsPerDegree * 0.5f).toFloat().coerceIn(5f, 260f)
        val margin = radius / pixelsPerDegree

        // 站点太多时等间隔抽样，而不是取前 N 个就停（那会把地图一部分画空）
        val stride = if (stations.size > MAX_DRAWN_STATIONS) {
            stations.size / MAX_DRAWN_STATIONS + 1
        } else {
            1
        }

        val point = android.graphics.Point()
        var index = 0
        while (index < stations.size) {
            val station = stations[index]
            index += stride

            if (station.latitude < box.latSouth - margin ||
                station.latitude > box.latNorth + margin
            ) continue
            if (station.longitude < box.lonWest - margin ||
                station.longitude > box.lonEast + margin
            ) continue

            val value = layer.valueOf(station)
            // 没有降水的地方不铺底色，让底图透出来
            if (layer == WeatherMapLayer.PRECIPITATION && (value ?: 0.0) <= 0.02) continue

            mapView.projection.toPixels(
                GeoPoint(station.latitude, station.longitude),
                point
            )
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < -radius || y < -radius ||
                x > canvas.width + radius || y > canvas.height + radius
            ) continue

            val base = layer.colorFor(value).toArgb()
            // 中心实、边缘透明，叠起来就是连续的场
            paint.shader = RadialGradient(
                x, y, radius,
                intArrayOf(
                    withAlpha(base, 0.95f),
                    withAlpha(base, 0.55f),
                    withAlpha(base, 0f)
                ),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(x, y, radius, paint)
        }
        paint.shader = null
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24 and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }
}

/** 城市标注 + 点选位置的环。数值直接写在圆圈里。 */
class MapMarkerOverlay(
    private val pointsProvider: () -> List<MapPointWeather>,
    private val queryProvider: () -> MapPointWeather?,
    private val layerProvider: () -> WeatherMapLayer,
    private val unitProvider: () -> TemperatureUnit
) : Overlay() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val nameLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(115, 0, 0, 0)
    }
    private val shadowRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(64, 0, 0, 0)
    }

    @Deprecated("osmdroid 的三参数版本仍然是实际被调用的入口")
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return

        val density = mapView.resources.displayMetrics.density
        val layer = layerProvider()
        val unit = unitProvider()

        val radius = 15f * density
        val ringWidth = 2f * density
        ring.strokeWidth = ringWidth
        label.textSize = 10f * density
        nameLabel.textSize = 10f * density
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
            color = Color.argb(217, 255, 255, 255)
        }

        val point = android.graphics.Point()
        val nameBounds = RectF()

        pointsProvider().forEach { item ->
            mapView.projection.toPixels(GeoPoint(item.latitude, item.longitude), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            if (x < 0f || y < 0f || x > canvas.width || y > canvas.height) return@forEach

            val value = layer.valueOf(item)
            drawBadge(
                canvas, x, y, layer.colorFor(value).toArgb(),
                layer.compactLabel(value, unit), radius, shadowRing, fill, ring
            )
            drawName(canvas, x, y + radius, item.name, nameLabel, pill, nameBounds, density)
        }

        queryProvider()?.let { item ->
            mapView.projection.toPixels(GeoPoint(item.latitude, item.longitude), point)
            val x = point.x.toFloat()
            val y = point.y.toFloat()
            val value = layer.valueOf(item)
            val color = layer.colorFor(value).toArgb()

            fill.color = withAlpha(color, 0.28f)
            canvas.drawCircle(x, y, radius + 9f * density, fill)
            canvas.drawCircle(x, y, radius + 9f * density, stroke)

            drawBadge(
                canvas, x, y, color,
                layer.compactLabel(value, unit), radius, shadowRing, fill, ring
            )
        }
    }

    private fun drawBadge(
        canvas: Canvas,
        x: Float,
        y: Float,
        color: Int,
        text: String,
        radius: Float,
        shadowRing: Paint,
        fill: Paint,
        ring: Paint
    ) {
        canvas.drawCircle(x, y, radius + 1.5f, shadowRing)
        fill.color = color
        canvas.drawCircle(x, y, radius, fill)
        ring.color = Color.WHITE
        canvas.drawCircle(x, y, radius, ring)
        canvas.drawText(text, x, y - (label.descent() + label.ascent()) / 2f, label)
    }

    private fun drawName(
        canvas: Canvas,
        x: Float,
        topY: Float,
        name: String,
        nameLabel: Paint,
        pill: Paint,
        bounds: RectF,
        density: Float
    ) {
        val halfWidth = nameLabel.measureText(name) / 2f
        val pad = 4f * density
        bounds.set(
            x - halfWidth - pad,
            topY + 2f * density,
            x + halfWidth + pad,
            topY + 2f * density + (nameLabel.descent() - nameLabel.ascent()) + 2f * density
        )
        canvas.drawRoundRect(bounds, 4f * density, 4f * density, pill)
        canvas.drawText(name, x, bounds.bottom - nameLabel.descent() - 1f * density, nameLabel)
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        val alpha = ((color ushr 24 and 0xFF) * factor).toInt().coerceIn(0, 255)
        return (alpha shl 24) or (color and 0x00FFFFFF)
    }
}
