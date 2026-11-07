package com.dream.tv.ui.mine

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dream.tv.AppViewModel
import com.dream.tv.data.FavoriteItem
import com.dream.tv.data.HistoryItem
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.dtRise
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors

/** 我的 —— 收藏/历史双页签（数据结构与 web 版互通） */
@Composable
fun MineScreen(
    app: AppViewModel,
    onOpenDetail: (String, String) -> Unit,
    onPlayHistory: (String, String, Int, Long) -> Unit,
) {
    val glass = LocalGlassColors.current
    var tab by remember { mutableStateOf(0) }
    val favorites by app.favorites.collectAsStateWithLifecycle()
    val history by app.history.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "我的",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = glass.textPrimary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
        )
        // 玻璃分段控件
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
        ) {
            Row(Modifier.padding(5.dp)) {
                listOf("收藏" to Icons.Filled.Favorite, "历史" to com.dream.tv.ui.glass.DtIcons.History)
                    .forEachIndexed { i, (label, icon) ->
                        val isSel = tab == i
                        val bg by animateColorAsState(
                            if (isSel) glass.accent.copy(alpha = 0.25f) else Color.Transparent
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(bg)
                                .glassClick { tab = i }
                                .padding(vertical = 8.dp),
                        ) {
                            Icon(
                                icon, null,
                                tint = if (isSel) glass.accent else glass.textSecondary,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                label,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSel) glass.accent else glass.textSecondary,
                            )
                        }
                    }
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp, end = 20.dp, top = 14.dp, bottom = 120.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (tab == 0) {
                if (favorites.isEmpty()) EmptyHint("还没有收藏，去首页逛逛吧")
                itemsIndexed(favorites) { index, f ->
                    Box(Modifier.dtRise(index % 6)) {
                        FavoriteCard(f, onDelete = { app.removeFavorite(f) }) {
                            onOpenDetail(f.source, f.videoId)
                        }
                    }
                }
            } else {
                if (history.isEmpty()) EmptyHint("看过的影片会出现在这里")
                itemsIndexed(history) { index, h ->
                    Box(Modifier.dtRise(index % 6)) {
                        HistoryCard(h, onDelete = { app.removeHistory(h) }) {
                            onPlayHistory(h.source, h.videoId, h.episodeIndex, h.playbackPosition)
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.EmptyHint(text: String) {
    item {
        val glass = LocalGlassColors.current
        Box(
            Modifier.fillMaxWidth().padding(vertical = 60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = glass.textPrimary.copy(alpha = 0.75f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun FavoriteCard(f: FavoriteItem, onDelete: () -> Unit, onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    GlassSurface(shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .glassClick(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(f.poster)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    f.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(f.year, f.type, f.remarks).joinToString(" · ").ifBlank { " " },
                    fontSize = 12.sp, color = glass.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            DeleteIcon(onDelete)
        }
    }
}

@Composable
private fun HistoryCard(h: HistoryItem, onDelete: () -> Unit, onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    val progress = if (h.duration > 0) (h.playbackPosition.toFloat() / h.duration).coerceIn(0f, 1f) else 0f
    GlassSurface(shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .glassClick(onClick = onClick)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Poster(h.poster)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    h.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    color = glass.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    "看到 ${h.episodes.getOrNull(h.episodeIndex)?.name ?: "第 ${h.episodeIndex + 1} 集"} · ${
                        (progress * 100).toInt()
                    }%",
                    fontSize = 11.sp, color = glass.accent,
                )
                Spacer(Modifier.height(6.dp))
                // 细进度条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(glass.textPrimary.copy(alpha = 0.08f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .height(3.dp)
                            .background(glass.accent, RoundedCornerShape(2.dp))
                    )
                }
            }
            DeleteIcon(onDelete)
        }
    }
}

@Composable
private fun Poster(url: String?) {
    androidx.compose.foundation.layout.Box(
        Modifier
            .width(64.dp)
            .aspectRatio(2f / 2.85f)
            .clip(RoundedCornerShape(10.dp))
            .background(LocalGlassColors.current.textPrimary.copy(alpha = 0.06f))
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null,
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun DeleteIcon(onDelete: () -> Unit) {
    val glass = LocalGlassColors.current
    Icon(
        Icons.Filled.Close, "删除",
        tint = glass.textSecondary.copy(alpha = 0.6f),
        modifier = Modifier
            .size(18.dp)
            .glassClick(onClick = onDelete),
    )
}
