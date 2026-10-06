package com.moko.iweather.core

import android.content.Context
import androidx.core.content.edit
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex

/**
 * 地图图源。
 *
 * 之前只用 CARTO 的 `dark_all`，在国内**大概率连不上**（境外 CDN），
 * 表现就是一片空白。这里给出三个源，并且**按可用性排序**：
 *
 *  1. **高德栅格瓦片** —— 国内直连、无需 API Key，首选；
 *  2. **OpenStreetMap 标准瓦片** —— 免 key 但国内较慢，兜底；
 *  3. **CARTO dark_all** —— 配色最配深色界面，境外网络下用。
 *
 * 用户可以在设置里切换，用来排除"某个源被你的网络挡住"的情况。
 */
enum class MapTileSource(val label: String, val note: String) {
    AMAP(
        "高德地图",
        "国内直连，无需 Key。默认图源。"
    ),
    OSM(
        "OpenStreetMap",
        "免 Key，但国内访问较慢。"
    ),
    CARTO_DARK(
        "CARTO 深色",
        "低饱和深色配色最搭深色界面，但为境外 CDN。"
    );

    companion object {
        private const val PREFS = "iweather_map_source"
        private const val KEY = "tile_source"

        fun current(context: Context): MapTileSource {
            val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, null) ?: return AMAP
            return entries.firstOrNull { it.name == name } ?: AMAP
        }

        fun set(context: Context, source: MapTileSource) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                putString(KEY, source.name)
            }
        }
    }
}

private const val AMAP_TEMPLATE =
    "https://webrd0{s}.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x={x}&y={y}&z={z}"
private const val OSM_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png"
private const val CARTO_TEMPLATE = "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"

private val AMAP_HOSTS = arrayOf("1", "2", "3", "4")
private val CARTO_HOSTS = arrayOf("a", "b", "c")

/**
 * 按索引选主机 + 展开模板。
 *
 * `序号`参与轮询，只是为了把请求分散到多个边缘节点，
 * 真正决定"能不能出图"的是这个域名在你的网络里通不通。
 */
private class MultiHostTileSource(
    name: String,
    private val template: String,
    private val hosts: Array<String>
) : OnlineTileSourceBase(name, 0, 19, 256, ".png", arrayOf()) {

    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        val host = hosts[Math.floorMod((x + y + z).toInt(), hosts.size)]
        return template
            .replace("{s}", host)
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }
}

fun createTileSource(source: MapTileSource) = when (source) {
    MapTileSource.AMAP -> MultiHostTileSource("AMap", AMAP_TEMPLATE, AMAP_HOSTS)
    MapTileSource.OSM -> XYTileSource("OSM", 0, 19, 256, ".png", arrayOf(OSM_TEMPLATE))
    MapTileSource.CARTO_DARK -> MultiHostTileSource("CARTO", CARTO_TEMPLATE, CARTO_HOSTS)
}
