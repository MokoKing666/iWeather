package com.moko.iweather.ui.weather

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * 天气背景：Apple 那种**四段纵向渐变** + 云层漂移 / 雨雪 / 雾尘。
 *
 * 粒子统一画在一张 Canvas 上（而不是每颗一个 composable），由一个 0→1 的
 * 循环进度驱动，既省性能也便于控制速度。
 */
/**
 * 记住一个 0→1 循环的动画进度。
 *
 * 背景层和卡片内的玻璃层**必须用同一个进度**，否则透过卡片看到的云/雨
 * 和背景里的对不上，一眼就穿帮。
 */
@Composable
fun rememberParticleProgress(isAnimated: Boolean): State<Float> {
    val transition = rememberInfiniteTransition(label = "particles")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "progress"
    )
    // 关闭动画时固定在中间值，粒子静止但仍然渲染（玻璃层才不会是空的）
    return remember(progress, isAnimated) { mutableStateOf(if (isAnimated) progress else 0.5f) }
}

@Composable
fun WeatherBackdrop(
    theme: AppleWeatherTheme,
    modifier: Modifier = Modifier,
    isAnimated: Boolean = true,
    /** 与卡片玻璃层共用的动画进度 */
    particleProgress: Float = 0.5f,
    /**
     * 内容遮罩。
     *
     * 白天渐变底部（尤其晴/多云）本身很亮，白色小字压上去对比度不够。
     * 这里在背景与内容之间铺一层很淡的纵向压暗，保证卡片区始终可读 ——
     * Apple 自己的兜底手段也是文字阴影 + 底部压暗。
     */
    contentScrim: Boolean = true,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(brush = Brush.verticalGradient(theme.colors))
        }

        if (theme.particle != ParticleStyle.None) {
            // 粒子做真实模糊：云的边缘本来就该是虚的。
            // minSdk 35 保证 RenderEffect 一定可用，不需要再判断 API 等级。
            Canvas(Modifier.fillMaxSize().blur(backgroundBlurOf(theme.particle))) {
                drawParticles(particleProgress, theme.particle)
            }
        }

        if (contentScrim) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.30f to Color.Transparent,
                        0.65f to Color.Black.copy(alpha = 0.10f),
                        1f to Color.Black.copy(alpha = 0.20f)
                    )
                )
            }
        }

        content()
    }
}

/** 背景层粒子的模糊半径。 */
fun backgroundBlurOf(style: ParticleStyle): Dp = when (style) {
    ParticleStyle.None -> 0.dp
    is ParticleStyle.Clouds -> 10.dp
    is ParticleStyle.Rain -> 1.5.dp
    is ParticleStyle.Snow -> 3.dp
    ParticleStyle.Fog -> 18.dp
    ParticleStyle.Dust -> 6.dp
}

/** 玻璃层粒子：卡片内部透出的那一份，模糊更强、对比更低，像被磨砂玻璃化过。 */
fun glassBlurOf(style: ParticleStyle): Dp = when (style) {
    ParticleStyle.None -> 0.dp
    is ParticleStyle.Clouds -> 26.dp
    is ParticleStyle.Rain -> 8.dp
    is ParticleStyle.Snow -> 12.dp
    ParticleStyle.Fog -> 40.dp
    ParticleStyle.Dust -> 18.dp
}

/**
 * 粒子绘制。抽出来是为了让**背景层**和**卡片内的玻璃层**画同一批东西，
 * 这样透过卡片看到的云/雨就是背景里那一片，而不是另画的装饰。
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    progress: Float,
    style: ParticleStyle,
    alphaScale: Float = 1f,
    /** 粒子场的尺寸（通常是整屏），默认用当前画布尺寸 */
    fieldWidth: Float = size.width,
    fieldHeight: Float = size.height
) {
    val w = if (fieldWidth > 0f) fieldWidth else size.width
    val h = if (fieldHeight > 0f) fieldHeight else size.height
    when (style) {
        ParticleStyle.None -> Unit
        is ParticleStyle.Clouds -> drawClouds(progress, style.opacity * alphaScale, w, h)
        is ParticleStyle.Rain -> drawRainAlpha(progress, style.intensity, alphaScale, w, h)
        is ParticleStyle.Snow -> drawSnowAlpha(progress, style.intensity, alphaScale, w, h)
        ParticleStyle.Fog -> drawFog(progress, w, h)
        ParticleStyle.Dust -> drawDust(progress, w, h)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawClouds(progress: Float, opacity: Float, w: Float, h: Float) {
    repeat(5) { index ->
        val seed = index.toFloat()
        val cloudWidth = w * (0.45f + seed * 0.08f)
        val cloudHeight = cloudWidth * 0.32f
        var x = (seed * 97f + progress * (0.5f + seed * 0.22f) * w) % (w + cloudWidth)
        if (x < 0f) x += w + cloudWidth
        x -= cloudWidth / 2f
        val y = h * (0.06f + seed * 0.18f)

        drawOval(
            color = Color.White.copy(alpha = opacity),
            topLeft = Offset(x, y - cloudHeight / 2f),
            size = Size(cloudWidth, cloudHeight)
        )
        drawOval(
            color = Color.White.copy(alpha = opacity),
            topLeft = Offset(x + cloudWidth * 0.2f, y - cloudHeight * 0.3f),
            size = Size(cloudWidth * 0.62f, cloudHeight * 0.7f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRainAlpha(progress: Float, intensity: Int, alphaScale: Float, w: Float, h: Float) {
    val count = (60 * intensity.coerceAtLeast(1)).coerceIn(20, 220)
    repeat(count) { index ->
        val rx = pseudoRandom(index * 17.13f)
        val ry = pseudoRandom(index * 3.71f + 1f)
        val x = rx * w
        val y = ((ry + progress) % 1f) * (h + 40f) - 20f
        drawLine(
            color = Color.White.copy(alpha = 0.30f * alphaScale),
            start = Offset(x, y),
            end = Offset(x - 3f, y + 16f + ry * 10f),
            strokeWidth = 1.3f
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSnowAlpha(progress: Float, intensity: Int, alphaScale: Float, w: Float, h: Float) {
    val count = (60 * intensity.coerceAtLeast(1)).coerceIn(20, 220)
    repeat(count) { index ->
        val rx = pseudoRandom(index * 11.37f)
        val ry = pseudoRandom(index * 2.91f + 1f)
        val sway = sin(progress * 6.28f + ry * 12f) * 18f
        val x = rx * w + sway
        val y = ((ry + progress * 0.9f) % 1f) * (h + 30f) - 15f
        val radius = 1.4f + pseudoRandom(index * 7.77f) * 2.4f
        drawCircle(Color.White.copy(alpha = 0.6f * alphaScale), radius, Offset(x, y))
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFog(progress: Float, w: Float, h: Float) {
    repeat(4) { index ->
        val ry = pseudoRandom(index * 4.37f + 1f)
        val y = h * 0.30f + index * h * 0.15f - ((progress * (0.35f + ry * 0.3f)) % 1f) * h * 0.32f
        drawOval(
            color = Color.White.copy(alpha = 0.05f + ry * 0.012f),
            topLeft = Offset(-w * 0.2f, y),
            size = Size(w * 1.4f, h * 0.24f)
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDust(progress: Float, w: Float, h: Float) {
    repeat(22) { index ->
        val rx = pseudoRandom(index * 9.11f)
        val ry = pseudoRandom(index * 6.13f + 1f)
        val x = ((rx + progress * (0.6f + ry)) % 1f) * (w + 30f) - 15f
        val y = ry * h + sin(progress * 6.28f + ry * 9f) * 14f
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(x, y),
            end = Offset(x + 14f + rx * 22f, y),
            strokeWidth = 2f
        )
    }
}

/** 稳定伪随机：同一 index 每次位置一致，避免闪烁。 */
private fun pseudoRandom(value: Float): Float {
    val raw = value * 12_989.13f
    return raw - kotlin.math.floor(raw)
}
