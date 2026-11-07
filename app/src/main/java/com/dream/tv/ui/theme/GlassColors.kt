package com.dream.tv.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 液态玻璃色板 —— 逐值移植 web 版 `app/styles/variables.css`：
 * 亮/暗两套 + `body.wallpaper-on` 的真玻璃（55% 半透明）变量。
 */
data class GlassColors(
    val isDark: Boolean,
    val wallpaperOn: Boolean,
    val bgColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val glassBg: Color,
    val glassBorder: Color,
    val shadowColor: Color,
)

object GlassPalette {

    /** 强调色预设（web 版 lib/config/accent.ts ACCENT_PRESETS） */
    val ACCENT_PRESETS = listOf(
        "starlight" to ("星空" to Color(0xFF4F86FF)),
        "caramel" to ("焦糖" to Color(0xFFE89B4B)),
        "cocoa" to ("可可" to Color(0xFFA76B4F)),
        "ochre" to ("赭石" to Color(0xFFC9603B)),
        "latte" to ("拿铁" to Color(0xFFC4A981)),
        "celadon" to ("青瓷" to Color(0xFF5FA98F)),
    )

    fun accentOf(id: String, custom: Long?): Color =
        custom?.let { Color(it) } ?: ACCENT_PRESETS
            .firstOrNull { it.first == id }?.second?.second
            ?: ACCENT_PRESETS[0].second.second

    fun light(wallpaperOn: Boolean, accent: Color): GlassColors = GlassColors(
        isDark = false,
        wallpaperOn = wallpaperOn,
        bgColor = if (wallpaperOn) Color.Transparent else Color(0xFFF2F4F7),
        textPrimary = Color(0xFF1D1D1F),
        textSecondary = Color(0xFF6E6E73),
        accent = accent,
        glassBg = if (wallpaperOn) Color(0x8CFFFFFF) else Color(0xF2FFFFFF), // 55% / 95%
        glassBorder = if (wallpaperOn) Color(0x14000000) else Color(0x0D000000),
        shadowColor = if (wallpaperOn) Color(0x400A081E) else Color(0x0D000000),
    )

    fun dark(wallpaperOn: Boolean, accent: Color): GlassColors = GlassColors(
        isDark = true,
        wallpaperOn = wallpaperOn,
        bgColor = if (wallpaperOn) Color.Transparent else Color(0xFF121212),
        textPrimary = Color(0xFFF5F5F7),
        textSecondary = Color(0xFF8E8E93),
        accent = accent,
        glassBg = if (wallpaperOn) Color(0x8C100D22) else Color(0xE61E1E1E), // 55% / 90%
        glassBorder = if (wallpaperOn) Color(0x24FFFFFF) else Color(0x1AFFFFFF),
        shadowColor = if (wallpaperOn) Color(0x73000000) else Color(0x4D000000),
    )

    fun of(isDark: Boolean, wallpaperOn: Boolean, accent: Color): GlassColors =
        if (isDark) dark(wallpaperOn, accent) else light(wallpaperOn, accent)
}

/** 内置渐变壁纸（web 版 WallpaperSettings 渐变预设的安卓对应物） */
object GradientWallpapers {
    data class Grad(val id: String, val name: String, val brush: Brush)

    val ALL = listOf(
        Grad("aurora", "极光", Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))),
        Grad("dusk", "暮色", Brush.verticalGradient(listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84)))),
        Grad("deepsea", "深海", Brush.verticalGradient(listOf(Color(0xFF0F0C29), Color(0xFF302B63), Color(0xFF24243E)))),
        Grad("night", "梦境夜空", Brush.verticalGradient(listOf(Color(0xFF241A4E), Color(0xFF0C0820)))),
    )

    fun of(id: String): Grad = ALL.firstOrNull { it.id == id } ?: ALL[0]
}
