package com.moko.iweather.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 26 / 27 的 Liquid Glass 控件集 —— 给设置页与城市页用。
 *
 * 与旧版 iOS inset-grouped 的区别，也是"看起来像不像 iOS 27"的关键：
 *  - 卡片**不是纯白/纯灰实色**，而是半透明白渐变 + 0.5dp 亮边（玻璃）；
 *  - 卡片顶部有一道镜面高光，底部更透，模拟玻璃的厚度；
 *  - 圆角更大（18dp），行之间靠玻璃卡片的间隙而不是分隔线；
 *  - 开关更大更"鼓"，带内阴影与高光；
 *  - 分组标题是全大写 + 字距的小字。
 */

object LiquidGlass {
    /**
     * 玻璃底衬。
     *
     * **这是"分层看不出来"的根因修复**：之前卡片只有 17%→7% 的白色渐变，
     * 没有任何深色垫底，压在地图瓦片、热力图这种高频背景上时几乎透明，
     * 上层文字和下层的路线名、站点名糊在一起。
     *
     * iOS 的材质本身就是"半透明底＋高光"，底衬不透明度和背景亮度有关；
     * 这个 App 的背景永远是深色天气，所以固定垫一层暗色最稳，
     * 保证任何背景下白字对比度都够。
     */
    val scrim = Color(0x40000000)

    /**
     * 卡片填充。
     *
     * iOS 18 起「天气」的卡片从 iOS 17 那种"厚玻璃"（大圆角 + 亮边 + 阴影）
     * 改成了**更扁的半透明块**：圆角变小、几乎没有边框、没有投影，
     * 上下的白色差也很小。这里对齐的是新样式。
     */
    val cardFill = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.20f),
        1f to Color.White.copy(alpha = 0.14f)
    )

    /** 悬浮面板（地图上的切换条、圆形按钮）：比卡片更实，因为它直接压在地图上。 */
    val floatingFill = Brush.verticalGradient(
        0f to Color.White.copy(alpha = 0.26f),
        1f to Color.White.copy(alpha = 0.16f)
    )

    val floatingScrim = Color(0x66000000)

    /* iOS 18 的卡片几乎没有描边，只在顶部有一点环境光反射 */
    val cardBorder = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.12f))

    /** iOS 17 是 18dp，iOS 18 收到 12dp 左右 */
    val cardCorner = 12.dp
    val accent = Color(0xFF0A84FF)
    val destructive = Color(0xFFFF6961)
    val green = Color(0xFF30D158)

    /** 组间留白比旧版更大，配合玻璃卡片才有"呼吸感"。 */
    val groupGap = 22.dp

    /** 悬浮面板的投影，用来把"上层"从背景里抬起来。 */
    val floatingElevation = 18.dp
}

/** 一组玻璃卡片。 */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    corner: Dp = LiquidGlass.cardCorner,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LiquidGlass.scrim)
            .background(LiquidGlass.cardFill)
            .border(LiquidGlass.cardBorder, shape)
            .padding(vertical = 2.dp),
        content = content
    )
}

/**
 * 悬浮面板的材质：暗底衬 + 高光 + 亮边 + 投影。
 *
 * 地图上那几个浮层用的是它 —— 底图、热力图都算高频内容，
 * 必须比普通卡片更实，再靠投影把层级拉开。
 */
fun Modifier.floatingGlass(shape: androidx.compose.ui.graphics.Shape, corner: Dp = LiquidGlass.cardCorner): Modifier =
    this
        .shadow(LiquidGlass.floatingElevation, shape, clip = false)
        .clip(shape)
        .background(LiquidGlass.floatingScrim)
        .background(LiquidGlass.floatingFill)
        .border(LiquidGlass.cardBorder, shape)

/** 卡片顶部的镜面高光条。 */
@Composable
fun GlassSpecular(corner: Dp = LiquidGlass.cardCorner) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.30f),
                        Color.Transparent
                    )
                )
            )
    )
}

/** 全大写分组标题。 */
@Composable
fun GlassGroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)
    )
}

/** 说明脚注。 */
@Composable
fun GlassGroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
    )
}

/** 玻璃行之间的细分隔（更淡，融进玻璃里）。 */
@Composable
fun GlassHairLine(insetStart: Dp = 56.dp) {
    Box(
        Modifier
            .padding(start = insetStart)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Color.White.copy(alpha = 0.12f))
    )
}

/** 玻璃行。 */
@Composable
fun GlassCell(
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = LiquidGlass.accent,
    trailing: (@Composable () -> Unit)? = null,
    showCheckmark: Boolean = false,
    showChevron: Boolean = false,
    isDestructive: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onTap != null) Modifier.clickable(role = Role.Button) { onTap() } else Modifier)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) LiquidGlass.destructive else iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(14.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (isDestructive) LiquidGlass.destructive else Color.White,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (value != null) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp)
            )
        }

        trailing?.let {
            Spacer(Modifier.width(10.dp))
            it()
        }

        if (showCheckmark) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = LiquidGlass.accent,
                modifier = Modifier.size(18.dp)
            )
        }

        if (showChevron) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "›",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.width(10.dp)
            )
        }
    }
}

/** Liquid Glass 开关：更宽更"鼓"，带轨道内阴影。 */
@Composable
fun GlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val trackWidth = 51.dp
    val trackHeight = 31.dp
    val thumbSize = 27.dp
    val inset = 2.dp

    val knobOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - inset else inset,
        animationSpec = tween(220),
        label = "glass-switch"
    )

    Box(
        modifier
            .size(trackWidth, trackHeight)
            .clip(CircleShape)
            .background(
                if (checked) LiquidGlass.green
                else Color.White.copy(alpha = 0.16f)
            )
            .border(
                BorderStroke(0.5.dp, Color.White.copy(alpha = if (checked) 0.0f else 0.22f)),
                CircleShape
            )
            .then(
                if (enabled) Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) }
                else Modifier
            )
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .shadow(3.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White, Color.White.copy(alpha = 0.86f))
                    )
                )
        )
    }
}

/** 状态点。 */
@Composable
fun GlassStatusDot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}

/** 一张强调用的大玻璃卡（用于"接口状态"这类需要吸睛的信息）。 */
@Composable
fun GlassInfoCard(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(LiquidGlass.cardCorner)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f))
                )
            )
            .border(LiquidGlass.cardBorder, shape)
            .padding(16.dp),
        content = content
    )
}
