package com.moko.iweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moko.iweather.ui.weather.ParticleStyle
import com.moko.iweather.ui.weather.drawParticles
import com.moko.iweather.ui.weather.glassBlurOf

/**
 * 天气页的玻璃卡片。
 *
 * **踩过的坑（之前一直有个方框的原因）**：
 * 卡片里各层最初用 `Modifier.fillMaxSize()`。卡片位于纵向滚动容器中，
 * 传入的最大高度约束是**无限**，`fillMaxSize()` 在这种约束下不会真正铺满，
 * 材质和粒子层被压成一条矩形 —— 看起来就是"玻璃中间一个方框"。
 * 正确做法是 Box 作用域下的 **`matchParentSize()`**：它按 Box 实测尺寸铺满，
 * 拿的是兄弟节点测完之后的结果，不受无限约束影响。
 *
 * 其次 `Modifier.blur` 默认会把模糊裁在自身边界内，边界会形成一圈可见矩形。
 * 这里用 `BlurredEdgeTreatment.Unbounded`，让模糊自然溢出、再由外层圆角裁掉。
 */
@Composable
fun WeatherGlassCard(
    particleStyle: ParticleStyle,
    /** 背景动画进度，与 WeatherBackdrop 用同一个值，两层才能对上 */
    particleProgress: Float,
    /** 粒子场尺寸 = 整屏尺寸。玻璃层必须在整屏坐标系里画粒子，才能和背景对上。 */
    fieldWidth: Float,
    fieldHeight: Float,
    modifier: Modifier = Modifier,
    corner: Dp = LiquidGlass.cardCorner,
    contentPadding: Dp = 14.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(corner)

    // 卡片在窗口中的位置：把整屏粒子场平移过来，卡片才能透出"背后那一片"
    var offsetInWindow by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // 玻璃材质直接铺在容器上，而不是另开 fillMaxSize 子层
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.18f),
                    0.55f to Color.White.copy(alpha = 0.10f),
                    1f to Color.White.copy(alpha = 0.07f)
                )
            )
            .border(
                width = 0.5.dp,
                color = Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .onGloballyPositioned { offsetInWindow = it.positionInWindow() }
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        if (particleStyle != ParticleStyle.None) {
            Canvas(
                Modifier
                    // matchParentSize：按 Box 实测尺寸铺满（fillMaxSize 在无限约束下会失效）
                    .matchParentSize()
                    // Unbounded：模糊溢出边界，由外层圆角裁切，避免出现矩形边
                    .blur(glassBlurOf(particleStyle), BlurredEdgeTreatment.Unbounded)
            ) {
                clipRect {
                    translate(-offsetInWindow.x, -offsetInWindow.y) {
                        drawParticles(
                            progress = particleProgress,
                            style = particleStyle,
                            alphaScale = 0.45f,
                            fieldWidth = fieldWidth,
                            fieldHeight = fieldHeight
                        )
                    }
                }
            }
        }

        // 底部内阴影：让卡片像一片有厚度的玻璃
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.82f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.08f)
                    )
                )
        )

        Column(Modifier.padding(contentPadding)) { content() }
    }
}
