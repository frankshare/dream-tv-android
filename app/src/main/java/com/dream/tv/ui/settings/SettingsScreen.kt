package com.dream.tv.ui.settings

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.dream.tv.AppViewModel
import com.dream.tv.R
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.ThemeMode
import com.dream.tv.data.WallpaperKind
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.AccentSampler
import com.dream.tv.ui.theme.GlassPalette
import com.dream.tv.ui.theme.GradientWallpapers
import com.dream.tv.ui.theme.LocalGlassColors
import java.io.File

/** 设置 —— 外观（主题/强调色/壁纸）+ 资源站管理入口 + 关于 */
@Composable
fun SettingsScreen(app: AppViewModel, onOpenSources: () -> Unit) {
    val glass = LocalGlassColors.current
    val settings by app.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 相册选壁纸
    val pickWallpaper = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val dir = File(context.filesDir, "wallpapers").apply { mkdirs() }
                val dst = File(dir, "custom-${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dst.outputStream().use { input.copyTo(it) }
                }
                app.setWallpaper(WallpaperKind.CUSTOM, path = dst.absolutePath)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Text(
            "设置",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = glass.textPrimary,
            modifier = Modifier.padding(vertical = 14.dp),
        )

        // ---------- 外观 ----------
        SectionCard("外观") {
            Text("主题模式", fontSize = 13.sp, color = glass.textSecondary)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    ThemeMode.LIGHT to "浅色",
                    ThemeMode.DARK to "深色",
                    ThemeMode.SYSTEM to "跟随系统",
                ).forEach { (mode, label) ->
                    val isSel = settings.themeMode == mode
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSel) glass.accent.copy(alpha = 0.22f)
                                else glass.textPrimary.copy(alpha = 0.06f)
                            )
                            .glassClick { app.setThemeMode(mode) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (isSel) glass.accent else glass.textSecondary,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("强调色", fontSize = 13.sp, color = glass.textSecondary)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GlassPalette.ACCENT_PRESETS.forEach { (id, meta) ->
                    val (_, color) = meta
                    val isSel = settings.accentCustom == null && settings.accentId == id
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                2.dp,
                                if (isSel) glass.textPrimary else Color.Transparent,
                                CircleShape,
                            )
                            .glassClick { app.setAccent(id, null) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSel) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                }
                // 从壁纸取色
                val isCustom = settings.accentCustom != null
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (isCustom) SolidColor(Color(settings.accentCustom!!))
                            else Brush.sweepGradient(
                                listOf(
                                    Color(0xFFE89B4B), Color(0xFFC9603B),
                                    Color(0xFF5FA98F), Color(0xFF4F86FF),
                                    Color(0xFFE89B4B),
                                )
                            )
                        )
                        .border(
                            2.dp,
                            if (isCustom) glass.textPrimary else Color.Transparent,
                            CircleShape,
                        )
                        .glassClick {
                            val bmp = when (settings.wallpaperKind) {
                                WallpaperKind.BUILTIN -> BitmapFactory.decodeResource(
                                    context.resources, R.drawable.wallpaper_dream_night
                                )
                                WallpaperKind.CUSTOM -> settings.wallpaperPath?.let {
                                    BitmapFactory.decodeFile(it)
                                }
                                else -> null
                            }
                            val sampled = AccentSampler.sampleFromBitmap(bmp)
                            if (sampled != null) {
                                app.setAccent("custom", AccentSampler.toArgbLong(sampled))
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isCustom) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("壁纸", fontSize = 13.sp, color = glass.textSecondary)
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                WallpaperThumb(selected = settings.wallpaperKind == WallpaperKind.NONE, label = "无") {
                    app.setWallpaper(WallpaperKind.NONE)
                }
                WallpaperThumb(
                    selected = settings.wallpaperKind == WallpaperKind.BUILTIN,
                    label = "梦境夜空",
                ) {
                    app.setWallpaper(WallpaperKind.BUILTIN)
                }
                GradientWallpapers.ALL.forEach { g ->
                    WallpaperThumb(
                        selected = settings.wallpaperKind == WallpaperKind.GRADIENT &&
                            settings.wallpaperGradient == g.id,
                        label = g.name,
                        brush = g.brush,
                    ) {
                        app.setWallpaper(WallpaperKind.GRADIENT, gradient = g.id)
                    }
                }
                WallpaperThumb(
                    selected = settings.wallpaperKind == WallpaperKind.CUSTOM,
                    label = "相册",
                    customPath = settings.wallpaperPath,
                ) {
                    pickWallpaper.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 资源站管理入口 ----------
        GlassSurface(shape = RoundedCornerShape(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .glassClick(pressedScale = 0.98f, onClick = onOpenSources)
                    .padding(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("资源站管理", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                    Spacer(Modifier.height(2.dp))
                    val enabled = SourcesRepo.all(context).size - settings.disabledSources.size
                    Text(
                        "$enabled 个启用 · 搜索与播放的数据来源",
                        fontSize = 11.sp,
                        color = glass.textSecondary,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                    tint = glass.textSecondary,
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------- 关于 ----------
        SectionCard("关于") {
            Text("Dream TV · Android", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = glass.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "v${com.dream.tv.BuildConfig.VERSION_NAME} · 78 个苹果CMS源内置 · 液态玻璃 + 沉浸壁纸 UI",
                fontSize = 12.sp,
                color = glass.textSecondary,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(12.dp))
            // 检查更新
            val updState = app.update.collectAsStateWithLifecycle().value
            val upd = updState
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    when {
                        upd.checking -> Text("正在检查更新…", fontSize = 12.sp, color = glass.textSecondary)
                        upd.error -> Text("检查失败，点右侧重试", fontSize = 12.sp, color = Color(0xFFE05A4E))
                        upd.isNewer && upd.latest != null ->
                            Text("发现新版本 v${upd.latest.version}", fontSize = 12.sp, color = glass.accent, fontWeight = FontWeight.SemiBold)
                        upd.checked -> Text("已是最新版本", fontSize = 12.sp, color = glass.textSecondary)
                        else -> Text("有新版本会在启动时提醒", fontSize = 12.sp, color = glass.textSecondary)
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(glass.accent.copy(alpha = 0.18f))
                        .glassClick { app.checkUpdate(manual = true) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (upd.checking) "…" else "检查更新",
                        fontSize = 12.sp, color = glass.accent, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (upd.isNewer && upd.latest != null) {
                val latestInfo = upd.latest!!
                Spacer(Modifier.height(8.dp))
                Text(
                    (latestInfo.notes ?: "")
                        .replace(Regex("#+\\s*"), "")
                        .replace(Regex("\\*+"), "")
                        .lines().take(3).joinToString("\n").ifBlank { "详见 Release 页面" },
                    fontSize = 11.sp,
                    color = glass.textSecondary,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(glass.accent)
                            .glassClick(onClick = {
                                val url = latestInfo.apkUrl ?: latestInfo.pageUrl
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            })
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("去下载 v${latestInfo.version}", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(128.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val glass = LocalGlassColors.current
    GlassSurface(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun WallpaperThumb(
    selected: Boolean,
    label: String,
    brush: Brush? = null,
    customPath: String? = null,
    onClick: () -> Unit,
) {
    val glass = LocalGlassColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(58.dp, 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(brush ?: SolidColor(glass.textPrimary.copy(alpha = 0.08f)))
                .border(
                    2.dp,
                    if (selected) glass.accent else glass.glassBorder,
                    RoundedCornerShape(12.dp),
                )
                .glassClick(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (label == "梦境夜空") {
                Image(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.wallpaper_dream_night),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (label == "相册" && !customPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(customPath),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = if (selected) glass.accent else glass.textSecondary)
    }
}
