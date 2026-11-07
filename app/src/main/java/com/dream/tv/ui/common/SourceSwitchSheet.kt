package com.dream.tv.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.SourceSwitcher
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors

/**
 * 换源半屏面板（播放器/详情页共用）——
 * 搜同名片源列出可切换项；来源：SourceSwitcher.findAlternates。
 */
@Composable
fun SourceSwitchSheet(
    context: android.content.Context,
    title: String,
    currentSourceId: String,
    onDismiss: () -> Unit,
    onPick: (sourceId: String, vodId: String) -> Unit,
) {
    val glass = LocalGlassColors.current
    var loading by remember { mutableStateOf(true) }
    var alts by remember { mutableStateOf<List<com.dream.tv.data.VideoItem>>(emptyList()) }

    LaunchedEffect(title, currentSourceId) {
        loading = true
        alts = SourceSwitcher.findAlternates(context, title, currentSourceId)
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        // 遮罩（点按关闭）
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        AnimatedVisibility(
            visible = true,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
        GlassSurface(
            Modifier.fillMaxWidth().fillMaxHeight(0.55f),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "换源",
                        color = glass.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "当前：${SourcesRepo.byId(context, currentSourceId)?.name.orEmpty()}",
                        color = glass.textSecondary, fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (loading) {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = glass.accent, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.height(8.dp))
                            Text("正在其他源里找「$title」…", color = glass.textSecondary, fontSize = 11.sp)
                        }
                    }
                } else {
                    val items = alts.take(40)
                    if (items.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("其他源没有找到同名影片", color = glass.textSecondary, fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(items) { _, item ->
                                val srcName = SourcesRepo.byId(context, item.source)?.name ?: item.source
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(glass.textPrimary.copy(alpha = 0.06f))
                                        .glassClick { onPick(item.source, item.vod_id) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(srcName, color = glass.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        item.vod_remarks ?: "",
                                        color = glass.textPrimary, fontSize = 12.sp,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    }
}
