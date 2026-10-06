package com.moko.iweather.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 真·背景模糊（backdrop blur）—— 「类似 Apple 液态玻璃」的那一层。
 *
 * ### 为什么需要这套东西
 * Compose 的 `Modifier.blur()` 只能模糊**节点自己画的内容**，
 * 没有 `backdrop-filter` 那种"模糊背后"的能力。
 * 所以想要玻璃面板真的把背后的内容糊掉，只能自己把背景录下来再模糊。
 *
 * ### 做法
 * 1. [rememberGlassBackdrop] 建一个 `GraphicsLayer`；
 * 2. 给"要当背景的内容"加 [GlassBackdrop.backdropSource]，它会把内容录进图层
 *    （`record`）之后照常画出来，所以**视觉上完全不变**，只是多了一份副本；
 * 3. 给玻璃面板套 [GlassPanel]，它把那份副本平移对齐后画进一个带 `BlurEffect`
 *    的图层里，就得到真实模糊，再叠暗底衬与高光。
 *
 * ### 使用约束（很容易踩）
 * - **背景内容和玻璃面板必须是同一个父容器下的兄弟节点**。
 *   对齐用的是 `positionInParent()`，跨父容器坐标系就对不上了。
 * - 需要 **Android 12（API 31）以上**才有 `RenderEffect`。
 *   低版本自动退化成"暗底衬 + 高光"的常规玻璃，不会崩、也不会变透明。
 * - `AndroidView` 里的内容（例如 osmdroid 地图）**录不进 Compose 图层**，
 *   那种场景只能用 [Modifier.floatingGlass]。
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {

    /**
     * 加在"要当背景"的内容上：录一份进图层，同时原样画出来。
     * 作用完全在绘制阶段，不会影响布局与命中测试。
     */
    fun Modifier.backdropSource(): Modifier = drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * 玻璃面板。
 *
 * @param backdrop 传 [rememberGlassBackdrop] 的结果就会启用真实背景模糊；
 *   传 null、或系统低于 Android 12，则只画材质。
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    corner: Dp = LiquidGlass.cardCorner,
    shape: Shape = RoundedCornerShape(corner),
    blurRadius: Dp = 24.dp,
    scrim: Color = LiquidGlass.floatingScrim,
    fill: Brush = LiquidGlass.floatingFill,
    elevation: Dp = LiquidGlass.floatingElevation,
    content: @Composable BoxScope.() -> Unit
) {
    val blurPx = with(LocalDensity.current) { blurRadius.toPx() }
    val blurEnabled = backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var offsetInParent by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .shadow(elevation, shape, clip = false)
            .clip(shape)
            // 垫底：模糊层万一没生效，这里保证文字对比度依然够
            .background(scrim)
    ) {
        if (blurEnabled && backdrop != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .onGloballyPositioned { offsetInParent = it.positionInParent() }
                    // 这个 renderEffect 只作用于本层内容（也就是那份背景副本），
                    // 面板上的文字在兄弟节点里，不会被糊掉
                    .graphicsBlur(blurPx)
                    .drawWithContent {
                        translate(-offsetInParent.x, -offsetInParent.y) {
                            drawLayer(backdrop.layer)
                        }
                    }
            )
        }

        // 模糊之上再压一层材质，玻璃才有"厚度"
        Box(Modifier.matchParentSize().background(fill))

        content()

        // 亮边画在最后，避免被模糊层盖住
        Box(Modifier.matchParentSize().border(LiquidGlass.cardBorder, shape))
    }
}

/** 单独抽出来只是为了让上面那串修饰符别太长。 */
private fun Modifier.graphicsBlur(radiusPx: Float): Modifier =
    this.graphicsLayer {
        renderEffect = BlurEffect(radiusPx, radiusPx)
    }
