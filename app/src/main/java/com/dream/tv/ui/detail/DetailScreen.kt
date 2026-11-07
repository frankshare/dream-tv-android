package com.dream.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dream.tv.AppViewModel
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.dtRise
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors

/**
 * 详情页（移动端原生 hero 布局）：
 * 海报模糊背景 + 悬浮海报卡 + 元信息胶囊 + 全宽播放 CTA + 简介/选集玻璃卡。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(
    app: AppViewModel,
    sourceId: String,
    vodId: String,
    onBack: () -> Unit,
    onPlay: (String, String, Int, Long) -> Unit,
    onSwitchSource: (String, String) -> Unit,
) {
    val vm: DetailViewModel = viewModel()
    val glass = LocalGlassColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val favorite by vm.favorite.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var introExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(sourceId, vodId) { vm.load(sourceId, vodId) }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is DetailState.Loading -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = glass.accent) }

            is DetailState.Error -> Column(
                Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                GlassSurface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("加载失败", fontWeight = FontWeight.Bold, color = glass.textPrimary, fontSize = 16.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.msg, fontSize = 12.sp, color = glass.textSecondary,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(14.dp))
                        AccentButton("重试") { vm.load(sourceId, vodId) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("返回", color = glass.accent, modifier = Modifier.glassClick(onClick = onBack))
            }

            is DetailState.Ready -> {
                val d = s.detail
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ---------- Hero：海报模糊背景 ----------
                    item {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            // 模糊背景
                            if (d.vod_pic.isNotBlank()) {
                                AsyncImage(
                                    model = d.vod_pic,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(28.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            } else {
                                Box(Modifier.fillMaxSize().background(glass.bgColor))
                            }
                            // 底部渐隐融入页面
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.35f to Color.Transparent,
                                            1f to glass.bgColor.copy(alpha = if (glass.isDark) 0.92f else 0.96f),
                                        )
                                    )
                            )
                            // 返回按钮
                            Box(
                                Modifier
                                    .statusBarsPadding()
                                    .padding(start = 16.dp, top = 8.dp)
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(glass.glassBg)
                                    .glassClick(onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack, "返回",
                                    tint = glass.textPrimary, modifier = Modifier.size(19.dp)
                                )
                            }
                            // 悬浮海报 + 信息（沉底）
                            Row(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(horizontal = 20.dp),
                                verticalAlignment = Alignment.Bottom,
                            ) {
                                AsyncImage(
                                    model = d.vod_pic,
                                    contentDescription = d.vod_name,
                                    modifier = Modifier
                                        .width(110.dp)
                                        .aspectRatio(2f / 2.85f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(glass.textPrimary.copy(alpha = 0.08f)),
                                    contentScale = ContentScale.Crop,
                                )
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.padding(bottom = 4.dp)) {
                                    Text(
                                        d.vod_name,
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.6f), blurRadius = 18f
                                            )
                                        ),
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    MetaPill(
                                        listOfNotNull(
                                            d.vod_year, d.vod_area, d.vod_remarks
                                        ).joinToString(" · ").ifBlank { s.sourceName }
                                    )
                                }
                            }
                        }
                    }
                    // ---------- 元信息行（玻璃卡保证对比度） ----------
                    item {
                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.padding(horizontal = 20.dp),
                        ) {
                            Column(Modifier.padding(14.dp).dtRise(1)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MetaPill(d.type_name ?: "影视")
                                    MetaPill(d.vod_lang ?: "")
                                    MetaPill("${d.episodes.size} 集")
                                    MetaPill(s.sourceName)
                                }
                                if (!d.vod_actor.isNullOrBlank()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "主演：${d.vod_actor}",
                                        fontSize = 11.sp, color = glass.textSecondary,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    // ---------- CTA：播放 + 收藏 ----------
                    item {
                        Row(
                            Modifier.padding(horizontal = 20.dp).dtRise(2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val isResume = (history?.episodeIndex ?: 0) > 0
                            Box(
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(glass.accent)
                                    .glassClick(onClick = {
                                        val resume = if (d.episodes.isNotEmpty() && history != null &&
                                            history!!.episodes.size == d.episodes.size
                                        ) history!!.playbackPosition else 0L
                                        onPlay(d.source, d.vod_id, history?.episodeIndex ?: 0, resume)
                                    }, pressedScale = 0.97f)
                                    .padding(vertical = 13.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.PlayArrow, null,
                                        tint = Color.White, modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        if (isResume) "继续播放 · ${
                                            d.episodes.getOrNull(history!!.episodeIndex)?.name ?: "第 ${history!!.episodeIndex + 1} 集"
                                        }" else "立即播放",
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(glass.glassBg)
                                    .glassClick { vm.toggleFavorite(d) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    "收藏",
                                    tint = if (favorite) Color(0xFFE05A6E) else glass.textSecondary,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }
                    // ---------- 换源页签（Kazumi 式：源名横排，点切） ----------
                    item {
                        SourceTabs(
                            title = d.vod_name,
                            currentSourceId = d.source,
                            onSwitch = { s, id -> onSwitchSource(s, id) },
                        )
                    }
                    // ---------- 简介 ----------
                    if (!d.vod_content.isNullOrBlank()) {
                        item {
                            GlassSurface(
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.padding(horizontal = 20.dp),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text("简介", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary)
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        d.vod_content
                                            .replace(Regex("<[^>]+>"), "")
                                            .replace("&nbsp;", " ")
                                            .replace("&amp;", "&")
                                            .replace("&lt;", "<")
                                            .replace("&gt;", ">")
                                            .replace("&quot;", "\"")
                                            .replace(Regex("\\s+"), " ")
                                            .trim(),
                                        fontSize = 12.sp,
                                        color = glass.textSecondary,
                                        lineHeight = 18.sp,
                                        maxLines = if (introExpanded) 40 else 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        if (introExpanded) "收起" else "展开",
                                        fontSize = 12.sp,
                                        color = glass.accent,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .glassClick { introExpanded = !introExpanded },
                                    )
                                }
                            }
                        }
                    }
                    // ---------- 选集 ----------
                    item {
                        GlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .padding(horizontal = 20.dp)
                                .fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "选集（${d.episodes.size}）",
                                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary,
                                )
                                Spacer(Modifier.height(10.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    d.episodes.forEach { ep ->
                                        val isCur = history?.episodeIndex == ep.index
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(11.dp))
                                                .background(
                                                    if (isCur) glass.accent.copy(alpha = 0.25f)
                                                    else glass.textPrimary.copy(alpha = 0.06f)
                                                )
                                                .glassClick {
                                                    onPlay(d.source, d.vod_id, ep.index, 0L)
                                                }
                                                .padding(horizontal = 12.dp, vertical = 7.dp),
                                        ) {
                                            Text(
                                                ep.name,
                                                fontSize = 12.sp,
                                                color = if (isCur) glass.accent else glass.textPrimary,
                                                maxLines = 1,
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
}

/** 小信息胶囊 */
@Composable
private fun MetaPill(text: String) {    val glass = LocalGlassColors.current
    if (text.isBlank()) return
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(glass.textPrimary.copy(alpha = 0.07f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = glass.textSecondary, maxLines = 1)
    }
}

/** 强调色胶囊按钮（次要用途） */
@Composable
fun AccentButton(
    label: String,
    filled: Boolean = false,
    onClick: () -> Unit,
) {
    val glass = LocalGlassColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (filled) glass.accent else glass.accent.copy(alpha = 0.18f))
            .glassClick(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            color = if (filled) Color.White else glass.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}


/** 换源页签：当前源 + 其他源同名条目（后台搜索），点即切换（Kazumi 式） */
@Composable
private fun SourceTabs(
    title: String,
    currentSourceId: String,
    onSwitch: (String, String) -> Unit,
) {
    val glass = LocalGlassColors.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var alts by remember(title, currentSourceId) {
        mutableStateOf<List<com.dream.tv.data.VideoItem>?>(null)
    }
    LaunchedEffect(title, currentSourceId) {
        alts = com.dream.tv.data.SourceSwitcher.findAlternates(context, title, currentSourceId)
    }
    GlassSurface(shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "片源",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary,
            )
            Spacer(Modifier.height(10.dp))
            val altsList = alts
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 当前源
                item {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(glass.accent.copy(alpha = 0.22f))
                            .border(1.dp, glass.accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    ) {
                        Text(
                            com.dream.tv.data.SourcesRepo.byId(context, currentSourceId)?.name ?: currentSourceId,
                            fontSize = 12.sp, color = glass.accent, fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
                when {
                    altsList == null -> item {
                        Text(
                            "正在其他源里找同名影片…",
                            fontSize = 12.sp, color = glass.textSecondary,
                            modifier = Modifier.padding(vertical = 7.dp),
                        )
                    }
                    altsList.isEmpty() -> item {
                        Text(
                            "其他源暂无同名片",
                            fontSize = 12.sp, color = glass.textSecondary,
                            modifier = Modifier.padding(vertical = 7.dp),
                        )
                    }
                    else -> items(altsList.size) { i ->
                        val it = altsList[i]
                        val name = com.dream.tv.data.SourcesRepo.byId(context, it.source)?.name ?: it.source
                        Column(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(glass.textPrimary.copy(alpha = 0.06f))
                                .glassClick { onSwitch(it.source, it.vod_id) }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                        ) {
                            Text(name, fontSize = 12.sp, color = glass.textPrimary, maxLines = 1)
                            if (!it.vod_remarks.isNullOrBlank()) {
                                Text(it.vod_remarks, fontSize = 10.sp, color = glass.textSecondary, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
