package com.moko.iweather.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Apple Weather 的卡片。
 *
 * 官方并没有对外的 UI 稿，这里按 iOS 17+ 实机观感还原。
 *
 * 材质说明（重要）：这里**不是**真正的背景模糊。Compose 的 `Modifier.blur`
 * 只能模糊节点自身内容、不能模糊背后的东西，而天气背景本身是一张平滑渐变 ——
 * 模糊它等于没做。真正有高频内容的是雨雪云粒子那一层（已在 `WeatherBackdrop`
 * 里做真实模糊），卡片叠在它上面时才会有玻璃感。
 * 所以这里用的是「半透明白渐变 + 0.5dp 亮边 + 顶部镜面高光」这套材质语言。
 */
object AppleMetrics {
    val ScreenPadding = 16.dp
    val CardSpacing = 10.dp
    val CardCorner = LiquidGlass.cardCorner
    val CardPadding = 14.dp
}

/** 玻璃卡片。与设置页的 `GlassGroup` 共用同一套材质参数，保证全 App 统一。 */
@Composable
fun AppleCard(
    modifier: Modifier = Modifier,
    corner: Dp = AppleMetrics.CardCorner,
    contentPadding: Dp = AppleMetrics.CardPadding,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LiquidGlass.cardFill)
            .border(LiquidGlass.cardBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(contentPadding)
    ) {
        // 顶部镜面高光：玻璃边缘对环境光的反射
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
        )
        Spacer(Modifier.height(contentPadding - 1.dp))
        content()
    }
}

/** 卡片内的分组标题（13pt 半透明白）。 */
@Composable
fun AppleSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/**
 * 详情网格里的一格。
 * Apple 的排版：小标题全大写 11pt 半透明，下方 22pt 中等字重数值。
 */
@Composable
fun AppleDetailTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Color.White
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * 悬浮圆形玻璃按钮（底部栏的"…"、地图上的操作钮）。
 *
 * 用 [floatingGlass]：暗底衬 + 高光 + 亮边 + 投影。
 * 这些钮常常直接压在地图瓦片或热力图这种高频内容上，
 * 只靠一层 16% 的白根本立不住，文字和图标的层级会糊在一起。
 */
@Composable
fun GlassCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier
            .size(diameter)
            .floatingGlass(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** 页码圆点：当前页是长条，其余是小圆点。 */
@Composable
fun ApplePageDots(
    count: Int,
    current: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Spacer(
                Modifier
                    .height(6.dp)
                    .width(if (index == current) 16.dp else 6.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color.White.copy(alpha = 0.4f))
            )
        }
    }
}
