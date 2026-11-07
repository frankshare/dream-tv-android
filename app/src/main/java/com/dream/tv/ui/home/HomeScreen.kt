package com.dream.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dream.tv.AppViewModel
import com.dream.tv.data.DoubanClient
import com.dream.tv.data.DoubanSubject
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.dtRise
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors
import kotlinx.coroutines.launch

/**
 * 首页 —— 豆瓣热门推荐（移动端原生布局：大标题 + 玻璃搜索胶囊 + 页签 + 双列海报）。
 * 点海报 → 聚合搜索该片名。
 */
@Composable
fun HomeScreen(
    app: AppViewModel,
    onOpenSearch: (String) -> Unit,
) {
    val glass = LocalGlassColors.current
    var selectedTab by remember { mutableStateOf(DoubanClient.tabs.first()) }
    val cache = remember { mutableStateMapOf<DoubanClient.DoubanTab, List<DoubanSubject>>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selectedTab) {
        if (cache[selectedTab] == null) {
            cache[selectedTab] = emptyList()
            scope.launch {
                cache[selectedTab] = try {
                    DoubanClient.recommend(selectedTab.type, selectedTab.tag)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }
    }

    val list = cache[selectedTab].orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 大标题 + 搜索胶囊
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.statusBarsPadding().padding(top = 10.dp)) {
                Text(
                    "Dream TV",
                    style = TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.35f),
                            blurRadius = 22f,
                        )
                    ),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                SearchPill { onOpenSearch("") }
            }
        }
        // 豆瓣页签
        item(span = { GridItemSpan(2) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DoubanClient.tabs.forEachIndexed { i, tab ->
                    val isSel = tab == selectedTab
                    Box(
                        Modifier
                            .dtRise(i)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (isSel) glass.accent.copy(alpha = 0.28f) else glass.glassBg
                            )
                            .border(1.dp, if (isSel) glass.accent.copy(alpha = 0.4f) else glass.glassBorder, RoundedCornerShape(20.dp))
                            .glassClick { selectedTab = tab }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            tab.tag + if (tab.type == "tv") "·剧" else "",
                            fontSize = 13.sp,
                            color = if (isSel) glass.accent else glass.textSecondary,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        if (list.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    Modifier.fillMaxWidth().height(320.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = glass.accent)
                }
            }
        }
        itemsIndexed(list) { index, subject ->
            Box(Modifier.dtRise(index % 8)) {
                DoubanCard(subject) { onOpenSearch(subject.title) }
            }
        }
    }
}

@Composable
private fun DoubanCard(subject: DoubanSubject, onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            Modifier
                .glassClick(onClick = onClick)
                .padding(8.dp)
        ) {
            Box {
                AsyncImage(
                    model = subject.cover,
                    contentDescription = subject.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 2.85f)
                        .clip(RoundedCornerShape(13.dp))
                        .background(glass.textPrimary.copy(alpha = 0.06f)),
                    contentScale = ContentScale.Crop,
                )
                if (subject.rate.isNotBlank() && subject.rate != "0") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(glass.glassBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Icon(
                            Icons.Filled.Star, null,
                            tint = glass.accent, modifier = Modifier.size(11.dp)
                        )
                        Text(
                            subject.rate,
                            fontSize = 11.sp,
                            color = glass.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                subject.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = glass.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 搜索玻璃胶囊入口 */
@Composable
fun SearchPill(onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .glassClick(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                Icons.Filled.Search, null,
                tint = glass.textSecondary, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("搜索 78 个资源站", fontSize = 14.sp, color = glass.textSecondary)
        }
    }
}
