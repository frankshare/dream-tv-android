package com.dream.tv.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import com.dream.tv.ui.theme.LocalGlassColors
import kotlinx.coroutines.delay

/** 全局 HazeState —— 壁纸层为 haze 源，所有玻璃面 hazeChild（拾音式毛玻璃） */
val AppHazeState = HazeState()

/**
 * 液态玻璃面板 —— 对齐 web 版 `.glass-card` 语言：
 * 玻璃底色(haze 毛玻璃) + 1dp 半透明描边 + 顶部高光 + 柔和投影。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    blur: Boolean = true,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    val style = HazeStyle(
        backgroundColor = glass.glassBg,
        tints = listOf(HazeTint(glass.glassBg)),
        blurRadius = 24.dp,
        noiseFactor = HazeDefaults.noiseFactor,
        fallbackTint = HazeTint(glass.glassBg),
    )
    val core = modifier
        .zIndex(1f)
        .shadowSoft(shape)
        .then(
            if (blur) {
                Modifier.hazeChild(AppHazeState, style = style)
            } else {
                Modifier.background(glass.glassBg, shape)
            }
        )
        .border(1.dp, glass.glassBorder, shape)
        .clip(shape)
    Box(modifier = core, contentAlignment = contentAlignment) {
        // 顶部高光（玻璃反光）
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to glass.textPrimary.copy(alpha = 0.06f),
                        0.35f to Color.Transparent,
                    )
                )
        )
        content()
    }
}

private fun Modifier.shadowSoft(shape: Shape): Modifier = composed {
    val glass = LocalGlassColors.current
    this.shadow(
        10.dp, shape, ambientColor = glass.shadowColor, spotColor = glass.shadowColor
    )
}

/** dt-rise 入场动效（web 版 globals.css：位移+缩放+透明三合一，弹簧缓动） */
fun Modifier.dtRise(index: Int = 0): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (index > 0) delay((index * 45L).coerceAtMost(360L))
        progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow * 1.6f))
    }
    val a = progress.value
    graphicsLayer {
        alpha = a
        translationY = (1f - a) * 26f
        val s = 0.96f + 0.04f * a
        scaleX = s; scaleY = s
        transformOrigin = TransformOrigin(0.5f, 0.7f)
    }
}

/**
 * 统一点击反馈：按压弹性缩放 + 无水波纹点击（拾音手感）。
 * 全 App 可点元素一律用它，替代裸 clickable。
 */
fun Modifier.glassClick(pressedScale: Float = 0.96f, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        scale.animateTo(
            if (isPressed) pressedScale else 1f,
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
        )
    }
    this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

/** 按压物理（web 版 button:active scale(.96)）——传入与 clickable 共用的 interactionSource */
fun Modifier.pressScale(
    interaction: MutableInteractionSource,
    pressed: Float = 0.96f,
): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(isPressed) {
        scale.animateTo(
            if (isPressed) pressed else 1f,
            spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium)
        )
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}
