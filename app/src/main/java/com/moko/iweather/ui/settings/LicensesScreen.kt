package com.moko.iweather.ui.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moko.iweather.ui.components.GlassCell
import com.moko.iweather.ui.components.GlassGroup
import com.moko.iweather.ui.components.GlassHairLine
import com.moko.iweather.ui.components.GlassSpecular
import com.moko.iweather.ui.components.LiquidGlass

/**
 * 开源许可与数据来源声明。
 *
 * ## 为什么必须有这个页面
 * 本工程用到的第三方库几乎全是 **Apache License 2.0**。
 * 该许可证第 4 条要求：再分发时必须**保留版权、专利、商标与归属声明**。
 * 之前一个字都没写 —— 这是法律义务，不是可选项。
 * 另外 iOS 的「天气」本身也有致谢页，加上它观感上也更完整。
 *
 * ## 只列真正用到的
 * 声明页只列**本工程实际依赖**的库。
 * 把没用的库也写进致谢里是错的：那等于声明了一个不存在的依赖关系。
 */

/** 一条开源声明。 */
private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val license: String,
    val note: String? = null
)

/** 按许可证分组。 */
private data class LicenseSection(
    val license: String,
    val summary: String,
    val entries: List<LicenseEntry>
)

private val LicenseSections = listOf(
    LicenseSection(
        license = "Apache License 2.0",
        summary = "版权归各自所有者，允许使用、修改、再分发，需保留本声明。",
        entries = listOf(
            LicenseEntry(
                "AndroidX（Compose、Activity、Core、Lifecycle、Glance、Graphics）",
                "Copyright The Android Open Source Project",
                "Apache-2.0"
            ),
            LicenseEntry(
                "OkHttp",
                "Copyright 2019 Square, Inc.",
                "Apache-2.0"
            ),
            LicenseEntry(
                "Kotlin / kotlinx.serialization",
                "Copyright JetBrains s.r.o. and Kotlin Programming Language contributors",
                "Apache-2.0"
            ),
            LicenseEntry(
                "osmdroid",
                "Copyright osmdroid contributors",
                "Apache-2.0",
                note = "地图控件。底图数据另行来自 OpenStreetMap。"
            ),
            LicenseEntry(
                "PAG（libpag）",
                "Copyright Tencent",
                "Apache-2.0",
                note = "动效播放。工程内附带的 .pag 文件取自 libpag 官方仓库的示例资源。"
            )
        )
    )
)

/** 数据与素材来源。这些不是代码依赖，但同样需要署名。 */
private val AttributionEntries = listOf(
    LicenseEntry(
        "天气数据",
        "彩云天气 Caiyun Weather API v2.6",
        "服务条款",
        note = "实时、逐小时、逐日预报与空气质量。按坐标计费，需自备 Token。"
    ),
    LicenseEntry(
        "全国站点气象图",
        "中国气象局 weather.cma.cn",
        "公开数据",
        note = "气温、降水、风场图层，一次请求 2440 个观测站。"
    ),
    LicenseEntry(
        "地图底图",
        "© OpenStreetMap 贡献者",
        "ODbL 1.0",
        note = "底图瓦片由 CARTO 提供。"
    ),
    LicenseEntry(
        "地点解析",
        "高德地图 Web 服务",
        "服务条款",
        note = "逆地理编码。"
    ),
    LicenseEntry(
        "全球城市检索",
        "Android Geocoder",
        "系统服务",
        note = "设备自带的地理编码服务。"
    ),
    LicenseEntry(
        "中国城市离线库",
        "本项目内置资源",
        "—",
        note = "2567 条城市与经纬度，源自原 iWeather 工程的 city_code_mapping.json。"
    )
)

/**
 * 字体单独拎出来说：**它们不是开源字体，不在上面的许可证范围内。**
 * SF Pro 与苹方的许可只覆盖 Apple 平台，把字形打进 Android 包属于超出授权。
 * 本工程仅供自用，不分发 —— 必须在这里写明，否则接手的人会以为可以直接发出去。
 */
private val FontNotices = listOf(
    LicenseEntry(
        "SF Pro Display / SF Pro Text",
        "Copyright Apple Inc.",
        "Apple 专有",
        note = "许可仅覆盖 Apple 平台。本工程自用，禁止分发。"
    ),
    LicenseEntry(
        "苹方-简 PingFang SC",
        "Copyright Apple Inc.",
        "Apple 专有",
        note = "同上。子集基于 GB2312，与 SF Pro 合并为单一字体文件（见 tools/build_fonts.py）。"
    )
)

/**
 * 设置页里的一行「开源许可」。点开是一个全屏浮层。
 *
 * 自带状态，不依赖宿主页面 —— 这样接进设置页只需要插一行，不用去改它的状态结构。
 * 浮层用 `Dialog(usePlatformDefaultWidth = false)` 而不是普通的 Box 叠加：
 * 前者不参与宿主的布局与滚动，任何页面里都能直接弹。
 */
@Composable
fun LicensesCell() {
    var visible by remember { mutableStateOf(false) }

    GlassCell(
        title = "开源许可与数据来源",
        icon = Icons.Filled.Description,
        showChevron = true,
        onTap = { visible = true }
    )

    if (visible) {
        Dialog(
            onDismissRequest = { visible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF10243D), Color(0xFF1B3A5C), Color(0xFF25506F))
                        )
                    )
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "开源许可",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "第三方组件与数据来源",
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f))
                                .clickable { visible = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "关闭",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    LicensesScreen(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun LicensesScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(LiquidGlass.groupGap)
    ) {
        Text(
            text = "本应用使用了以下开源项目与公开数据。\n" +
                "字体部分不在开源范围内，请勿分发本安装包。",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        LicenseSections.forEach { section ->
            SectionBlock(
                title = section.license,
                subtitle = section.summary,
                entries = section.entries
            )
        }

        SectionBlock(
            title = "数据来源",
            subtitle = "以下服务不是代码依赖，但同样需要署名。",
            entries = AttributionEntries
        )

        SectionBlock(
            title = "字体",
            subtitle = "非开源，仅限自用。",
            entries = FontNotices
        )

        Text(
            text = "完整许可证文本：apache.org/licenses/LICENSE-2.0",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun SectionBlock(
    title: String,
    subtitle: String,
    entries: List<LicenseEntry>
) {
    Column {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)
        )

        GlassGroup {
            GlassSpecular()
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        GlassHairLine(insetStart = 16.dp)
                    }
                    LicenseRow(entry)
                }
            }
        }
    }
}

@Composable
private fun LicenseRow(entry: LicenseEntry) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.name,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = entry.license,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp
                )
            }
        }

        Text(
            text = entry.copyright,
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp
        )

        entry.note?.let {
            Spacer(Modifier.height(1.dp))
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
