package com.dream.tv.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dream.tv.data.DanmakuClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 弹幕层 —— 4 轨道从右向左滚动，由播放位置驱动（弹弹play 开放弹幕库数据）。
 */
private const val TRACK_COUNT = 4
private const val SCROLL_MS = 9_000L
private const val SPAWN_AHEAD_MS = 150L

private class ActiveDanmaku(
    val id: Long,
    val text: String,
    val color: Color,
    val fromTop: Boolean,
    val track: Int,
    val x: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    val job: Job,
)

@Composable
fun DanmakuOverlay(
    comments: List<DanmakuClient.DanmakuItem>,
    positionMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled || comments.isEmpty()) return
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val active = remember { mutableStateListOf<ActiveDanmaku>() }
    var pointer by remember(comments) { mutableIntStateOf(0) }
    var trackCounter by remember(comments) { mutableIntStateOf(0) }
    var idCounter by remember(comments) { mutableIntStateOf(0) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val widthPx = constraints.maxWidth.toFloat()
        val exitPx = -widthPx * 0.9f  // 滚出左缘的近似终点

        // 弹幕孵化：位置推进到 comment.timeMs 时发射
        LaunchedEffect(comments, enabled, positionMs) {
            if (pointer < comments.size && comments[pointer].timeMs > positionMs + 3_000) {
                pointer = 0 // 回跳
                active.clear()
            }
            while (pointer < comments.size && comments[pointer].timeMs <= positionMs + SPAWN_AHEAD_MS) {
                val item = comments[pointer]
                if (item.timeMs >= positionMs - SPAWN_AHEAD_MS) {
                    val track = trackCounter % TRACK_COUNT
                    trackCounter += 1
                    val id = idCounter.toLong(); idCounter += 1
                    val x = Animatable(widthPx)
                    val job = scope.launch {
                        x.animateTo(exitPx, tween(SCROLL_MS.toInt(), easing = LinearEasing))
                    }
                    val ad = ActiveDanmaku(id, item.text, Color(item.color), item.fromTop, track, x, job)
                    active.add(ad)
                    job.invokeOnCompletion { active.removeAll { it.id == id } }
                }
                pointer += 1
            }
            active.removeAll { it.x.value <= exitPx + 1f }
        }

        active.forEach { ad ->
            Text(
                text = ad.text,
                color = ad.color,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = with(density) { ad.x.value.roundToInt().toDp() },
                        y = with(density) {
                            ((if (ad.fromTop) 44f else 8f + ad.track * 26f)).roundToInt().toDp()
                        },
                    )
                    .padding(horizontal = 2.dp),
            )
        }
    }
}
