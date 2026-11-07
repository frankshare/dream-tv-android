package com.dream.tv.ui.search

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.dream.tv.AppViewModel
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.dtRise
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors

/**
 * 聚合搜索页（移动端原生）：
 * 玻璃搜索条 + 紧凑进度条（x/78 · 命中N源）+ 归并结果流式上屏。
 */
@Composable
fun SearchScreen(
    app: AppViewModel,
    initialQuery: String,
    onOpenDetail: (String, String) -> Unit,
) {
    val vm: SearchViewModel = viewModel()
    val glass = LocalGlassColors.current
    var text by remember { mutableStateOf(initialQuery) }
    val groups by vm.groups.collectAsState()
    val states by vm.states.collectAsState()
    val running by vm.running.collectAsState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank() && groups.isEmpty() && states.isEmpty()) {
            text = initialQuery
            vm.search(initialQuery)
        }
    }

    val submit = {
        keyboard?.hide()
        vm.search(text)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .imePadding()
    ) {
        Spacer(Modifier.height(6.dp))
        // 搜索玻璃条
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Icon(Icons.Filled.Search, null, tint = glass.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text("片名、剧名…", color = glass.textSecondary, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = TextStyle(color = glass.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(glass.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { submit() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focus),
                    )
                }
                if (text.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Clear, "清空",
                        tint = glass.textSecondary,
                        modifier = Modifier
                            .size(17.dp)
                            .glassClick { text = "" },
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(glass.accent.copy(alpha = 0.22f))
                        .glassClick(onClick = submit)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text("搜索", color = glass.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 紧凑进度（移动端：一行摘要 + 细进度条，替代桌面端点阵）
        val total = states.size
        if (total > 0) {
            val done = states.count {
                it.run is SourceRun.Done || it.run is SourceRun.Failed
            }
            val hits = states.count { (it.run as? SourceRun.Done)?.count ?: 0 > 0 }
            GlassSurface(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (running) "正在搜索 $done/$total 站" else "搜索完成",
                            fontSize = 12.sp,
                            color = glass.textPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            "命中 $hits 源 · ${groups.size} 部",
                            fontSize = 12.sp,
                            color = glass.accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(glass.textPrimary.copy(alpha = 0.10f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (total > 0) done.toFloat() / total else 0f)
                                .height(3.dp)
                                .background(glass.accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // 结果列表
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 128.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (groups.isEmpty() && running) {
                item {
                    Text(
                        "逐个源聚合中，好结果马上来…",
                        fontSize = 12.sp,
                        color = glass.textPrimary.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                    )
                }
            }
            if (groups.isEmpty() && !running && total > 0) {
                item {
                    Text(
                        "没搜到，换个关键词试试",
                        fontSize = 13.sp,
                        color = glass.textPrimary.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                    )
                }
            }
            itemsIndexed(groups, key = { _, g -> g.key }) { index, g ->
                Box(Modifier.dtRise(index % 6)) {
                    GroupCardView(g) {
                        g.members.firstOrNull()?.let { m ->
                            onOpenDetail(m.source.id, m.item.vod_id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupCardView(g: GroupCard, onClick: () -> Unit) {
    val glass = LocalGlassColors.current
    GlassSurface(shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier
                .glassClick(onClick = onClick)
                .padding(10.dp)
        ) {
            Box(
                Modifier
                    .width(92.dp)
                    .aspectRatio(2f / 2.85f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(glass.textPrimary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (g.poster.isNullOrBlank()) {
                    Icon(
                        com.dream.tv.ui.glass.DtIcons.Movie, null,
                        tint = glass.textSecondary.copy(alpha = 0.5f),
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    AsyncImage(
                        model = g.poster,
                        contentDescription = g.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    g.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = glass.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(g.year, g.typeName).joinToString(" · ").ifBlank { "暂无信息" },
                    fontSize = 12.sp,
                    color = glass.textSecondary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                if (!g.remarks.isNullOrBlank()) {
                    Text(
                        g.remarks,
                        fontSize = 12.sp,
                        color = glass.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(glass.accent.copy(alpha = 0.14f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "${g.members.size} 个源",
                        fontSize = 10.sp,
                        color = glass.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
