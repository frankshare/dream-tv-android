package com.dream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dream.tv.data.GlassSettings
import com.dream.tv.data.ThemeMode
import com.dream.tv.data.WallpaperKind
import com.dream.tv.ui.glass.WallpaperLayer
import com.dream.tv.ui.nav.DreamNavRoot
import com.dream.tv.ui.theme.GlassPalette
import com.dream.tv.ui.theme.GlassTheme

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
    val systemDark = isSystemInDarkTheme()
    val isDark = when (settings.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }
    val accent = GlassPalette.accentOf(settings.accentId, settings.accentCustom)
    val wallpaperOn = settings.wallpaperKind != WallpaperKind.NONE
    val glass = GlassPalette.of(isDark, wallpaperOn, accent)

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
        }
    }
}
