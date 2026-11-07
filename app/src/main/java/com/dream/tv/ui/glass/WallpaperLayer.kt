package com.dream.tv.ui.glass

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import com.dream.tv.data.GlassSettings
import com.dream.tv.data.WallpaperKind
import com.dream.tv.ui.theme.GradientWallpapers
import dev.chrisbanes.haze.haze
import java.io.File

/**
 * 壁纸沉浸层（拾音式）：铺满全屏，作为全局 haze 源，
 * 所有玻璃面板对其取样毛玻璃。web 版 ThemeProvider + body.wallpaper-on 的对应物。
 */
@Composable
fun WallpaperLayer(settings: GlassSettings, isDark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .haze(AppHazeState)
    ) {
        when (settings.wallpaperKind) {
            WallpaperKind.BUILTIN -> Image(
                painter = painterResource(com.dream.tv.R.drawable.wallpaper_dream_night),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            WallpaperKind.CUSTOM -> settings.wallpaperPath?.let { path ->
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            WallpaperKind.GRADIENT -> Box(
                Modifier
                    .fillMaxSize()
                    .background(GradientWallpapers.of(settings.wallpaperGradient).brush)
            )
            else -> Unit
        }
        // 暗色压黑纱 / 亮色提白纱：保证大标题与玻璃在深色壁纸上都可读
        if (isDark) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color(0x40000000), Color(0x55000000))))
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x26FFFFFF))
            )
        }
    }
}
