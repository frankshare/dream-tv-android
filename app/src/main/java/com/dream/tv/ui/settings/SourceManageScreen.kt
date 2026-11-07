package com.dream.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dream.tv.AppViewModel
import com.dream.tv.data.SourcesRepo
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors

/** 资源站管理子页 —— 78 源启停 + 筛选 + 恢复默认 */
@Composable
fun SourceManageScreen(app: AppViewModel, onBack: () -> Unit) {
    val glass = LocalGlassColors.current
    val settings by app.settings.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }

    val all = SourcesRepo.all(app.store)
    val enabledCount = all.size - settings.disabledSources.size
    val filtered = if (filter.isBlank()) all
    else all.filter { it.name.contains(filter, true) || it.id.contains(filter, true) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                Modifier
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
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("资源站管理", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                Text(
                    "$enabledCount / ${all.size} 启用",
                    fontSize = 11.sp,
                    color = glass.textSecondary,
                )
            }
            Text(
                "恢复默认",
                fontSize = 13.sp,
                color = glass.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .glassClick { app.resetSources() }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
        // 筛选
        GlassSurface(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    singleLine = true,
                    textStyle = TextStyle(color = glass.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(glass.accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    decorationBox = { inner ->
                        Box {
                            if (filter.isEmpty()) {
                                Text("筛选资源站…", fontSize = 13.sp, color = glass.textSecondary)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // 列表
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 40.dp),
        ) {
            items(filtered, key = { it.id }) { src ->
                val enabled = !settings.disabledSources.contains(src.id)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            src.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (enabled) glass.textPrimary else glass.textSecondary.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            src.baseUrl.removePrefix("https://").removePrefix("http://"),
                            fontSize = 10.sp,
                            color = glass.textPrimary.copy(alpha = 0.55f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { app.toggleSource(src.id, it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = glass.accent),
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
