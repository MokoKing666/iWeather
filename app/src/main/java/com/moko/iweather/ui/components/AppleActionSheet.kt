package com.moko.iweather.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 操作面板（Action Sheet）。
 *
 * 用来替换 Material 的 `DropdownMenu` —— 后者在深色天气背景上完全不像 iOS。
 * 规格对齐 iOS：
 *  - 背景遮罩（黑色半透明），点按关闭
 *  - 面板从底部升起，圆角 14dp；有一层"折射"高光
 *  - 面板内每个分组是一张 13dp 圆角的半透明卡片，行间 0.5dp 内缩分隔线
 *  - "取消"单独一张卡片
 *  - 行高 57sp 视觉，图标 20sp 居左
 */
data class AppleActionItem(
    val label: String,
    val icon: ImageVector,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun AppleActionSheet(
    title: String?,
    items: List<AppleActionItem>,
    onDismiss: () -> Unit
) {
    // 之前用 animateFloatAsState(targetValue = 1f) 却没给 initialValue，
    // progress 一上来就是 1，所谓"升起动画"其实根本没播。改用 Animatable 真播。
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩
        Box(
            Modifier
                .fillMaxSize()
                // 遮罩压得比之前重一点：面板升起后背景要明显退到后面去，
                // 否则上层文字和下层的天气数据糊在同一层，分不清谁是谁
                .background(Color.Black.copy(alpha = 0.46f * progress.value))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .graphicsLayer {
                    val p = progress.value
                    translationY = (1f - p) * 56f
                    scaleX = 0.96f + 0.04f * p
                    scaleY = 0.96f + 0.04f * p
                    alpha = p
                }
                .clip(RoundedCornerShape(16.dp))
                .background(LiquidGlass.cardFill)
                .border(LiquidGlass.cardBorder, RoundedCornerShape(16.dp))
                .padding(vertical = 6.dp)
        ) {
            if (title != null) {
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 10.dp)
                )
            }

            items.forEachIndexed { index, item ->
                if (index > 0) {
                    Box(
                        Modifier
                            .padding(start = 54.dp)
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.14f))
                    )
                }
                ActionRow(item)
            }
        }
    }
}

@Composable
private fun ActionRow(item: AppleActionItem) {
    val tint = if (item.isDestructive) Color(0xFFFF6961) else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .clickable {
                item.onClick()
            }
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = item.label,
            color = tint,
            fontSize = 19.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

/** 取消按钮：iOS 里是独立一张卡片。 */
@Composable
fun AppleCancelButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.10f))
                )
            )
            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.20f)), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 17.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Text("取消", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
    }
}


