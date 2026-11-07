package com.dream.tv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalGlassColors = staticCompositionLocalOf<GlassColors> {
    error("GlassColors not provided")
}

/** Material3 ColorScheme 与玻璃变量对齐（primary=强调色，surface=玻璃底） */
@Composable
fun GlassTheme(glass: GlassColors, content: @Composable () -> Unit) {
    val scheme = if (glass.isDark) {
        darkColorScheme(
            primary = glass.accent,
            onPrimary = Color.White,
            background = glass.bgColor,
            onBackground = glass.textPrimary,
            surface = glass.glassBg,
            onSurface = glass.textPrimary,
            surfaceVariant = glass.glassBg,
            onSurfaceVariant = glass.textSecondary,
            outline = glass.glassBorder,
        )
    } else {
        lightColorScheme(
            primary = glass.accent,
            onPrimary = Color.White,
            background = glass.bgColor,
            onBackground = glass.textPrimary,
            surface = glass.glassBg,
            onSurface = glass.textPrimary,
            surfaceVariant = glass.glassBg,
            onSurfaceVariant = glass.textSecondary,
            outline = glass.glassBorder,
        )
    }
    CompositionLocalProvider(LocalGlassColors provides glass) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
