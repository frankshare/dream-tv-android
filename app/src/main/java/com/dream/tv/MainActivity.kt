package com.dream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dream.tv.data.GlassSettings
import com.dream.tv.data.ThemeMode
import com.dream.tv.data.WallpaperKind
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.WallpaperLayer
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.nav.DreamNavRoot
import com.dream.tv.ui.theme.GlassPalette
import com.dream.tv.ui.theme.GlassTheme
import com.dream.tv.ui.theme.LocalGlassColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DreamRoot() }
    }
}

@Composable
private fun DreamRoot() {
    val app = viewModel<AppViewModel>()
    val settings by app.settings.collectAsStateWithLifecycle()
    val pendingUpdate by app.pendingUpdate.collectAsStateWithLifecycle()
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }
    val accent = GlassPalette.accentOf(settings.accentId, settings.accentCustom)
    val wallpaperOn = settings.wallpaperKind != WallpaperKind.NONE
    val glass = GlassPalette.of(isDark, wallpaperOn, accent)

    // 启动静默检查更新（24h 节流）
    LaunchedEffect(Unit) { app.maybeAutoCheckUpdate() }

    GlassTheme(glass) {
        Box(
            Modifier
                .fillMaxSize()
                .background(glass.bgColor)
        ) {
            // 层 0：壁纸（haze 源）
            WallpaperLayer(settings, isDark)
            // 层 1：内容 + 底部玻璃 Tab（玻璃面 hazeChild 取样壁纸）
            DreamNavRoot(app)
            // 层 2：发现新版弹窗
            pendingUpdate?.let { info ->
                UpdateDialog(
                    info = info,
                    onDismiss = { app.dismissPendingUpdate() },
                )
            }
        }
    }
}

@Composable
private fun UpdateDialog(info: com.dream.tv.data.UpdateChecker.ReleaseInfo, onDismiss: () -> Unit) {
    val glass = LocalGlassColors.current
    val context = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center,
    ) {
        GlassSurface(
            Modifier
                .padding(horizontal = 36.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            blur = false,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("发现新版本", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = glass.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("v${info.version}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = glass.accent)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    info.notes
                        ?.replace(Regex("#+\\s*"), "")
                        ?.replace(Regex("\\*+"), "")
                        ?.lines()
                        ?.take(6)
                        ?.joinToString("\n")
                        ?.ifBlank { null }
                        ?: "修复与优化，详见 Release 页面",
                    fontSize = 12.sp,
                    color = glass.textSecondary,
                    lineHeight = 18.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
                Row {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(glass.textPrimary.copy(alpha = 0.08f))
                            .glassClick(onClick = onDismiss)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("下次再说", fontSize = 13.sp, color = glass.textSecondary)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(glass.accent)
                            .glassClick(onClick = {
                                onDismiss()
                                val url = com.dream.tv.data.UpdateChecker.accelerated(info.apkUrl ?: info.pageUrl)
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(url)
                                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                                Unit
                            })
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("去下载", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
