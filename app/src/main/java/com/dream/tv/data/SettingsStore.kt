package com.dream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString

private val Context.settingsStore by preferencesDataStore(name = "dreamtv-settings")

/** 外观主题模式（对齐 web 版 ThemeDock 循环顺序：亮 → 暗 → 跟随系统） */
object ThemeMode {
    const val LIGHT = 0
    const val DARK = 1
    const val SYSTEM = 2
}

/** 壁纸种类：none 纯色 / builtin 内置梦境夜空 / gradient 内置渐变 / custom 相册选图 */
object WallpaperKind {
    const val NONE = "none"
    const val BUILTIN = "builtin"
    const val GRADIENT = "gradient"
    const val CUSTOM = "custom"
}

/**
 * 全局设置（web 版 settings-store 的安卓子集）。
 */
data class GlassSettings(
    val themeMode: Int = ThemeMode.LIGHT,
    val accentId: String = "starlight",
    val accentCustom: Long? = null,      // 取色/自定义 ARGB
    val wallpaperKind: String = WallpaperKind.BUILTIN,
    val wallpaperGradient: String = "aurora",
    val wallpaperPath: String? = null,   // 自定义壁纸（已拷入 filesDir/wallpapers）
    val disabledSources: Set<String> = emptySet(),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val themeMode = intPreferencesKey("theme_mode")
        val accentId = stringPreferencesKey("accent_id")
        val accentCustom = stringPreferencesKey("accent_custom")
        val wallpaperKind = stringPreferencesKey("wallpaper_kind")
        val wallpaperGradient = stringPreferencesKey("wallpaper_gradient")
        val wallpaperPath = stringPreferencesKey("wallpaper_path")
        val disabledSources = stringSetPreferencesKey("disabled_sources")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val adFilter = androidx.datastore.preferences.core.booleanPreferencesKey("ad_filter")
    }

    val settings: Flow<GlassSettings> = context.settingsStore.data.map { p ->
        GlassSettings(
            themeMode = p[Keys.themeMode] ?: ThemeMode.LIGHT,
            accentId = p[Keys.accentId] ?: "starlight",
            accentCustom = p[Keys.accentCustom]?.toLongOrNull(),
            wallpaperKind = p[Keys.wallpaperKind] ?: WallpaperKind.BUILTIN,
            wallpaperGradient = p[Keys.wallpaperGradient] ?: "aurora",
            wallpaperPath = p[Keys.wallpaperPath],
            disabledSources = p[Keys.disabledSources] ?: emptySet(),
        )
    }

    suspend fun setThemeMode(mode: Int) = context.settingsStore.edit { it[Keys.themeMode] = mode }

    suspend fun setAccent(id: String, custom: Long?) = context.settingsStore.edit {
        it[Keys.accentId] = id
        if (custom != null) it[Keys.accentCustom] = custom.toString() else it.remove(Keys.accentCustom)
    }

    suspend fun setWallpaper(kind: String, gradient: String? = null, path: String? = null) =
        context.settingsStore.edit {
            it[Keys.wallpaperKind] = kind
            if (gradient != null) it[Keys.wallpaperGradient] = gradient
            if (path != null) it[Keys.wallpaperPath] = path
        }

    suspend fun toggleSource(id: String, enabled: Boolean) = context.settingsStore.edit {
        val cur = it[Keys.disabledSources] ?: emptySet()
        it[Keys.disabledSources] = if (enabled) cur - id else cur + id
    }

    suspend fun resetSources() = context.settingsStore.edit { it.remove(Keys.disabledSources) }

    /** 上次检查更新的时间戳（静默检查 24h 节流用） */
    suspend fun lastUpdateCheck(): Long =
        context.settingsStore.data.map { it[Keys.lastUpdateCheck] ?: 0L }.first()

    // ---------- 播放偏好 ----------

    suspend fun adFilter(): Boolean =
        context.settingsStore.data.map { it[Keys.adFilter] ?: true }.first()

    suspend fun setAdFilter(on: Boolean) =
        context.settingsStore.edit { it[Keys.adFilter] = on }

    /** 每部剧的片头/片尾时长（毫秒），键随 showIdentifier 动态生成 */
    suspend fun introOutro(show: String): Pair<Long, Long> =
        context.settingsStore.data.map { p ->
            val i: Long = p[androidx.datastore.preferences.core.longPreferencesKey("sk_intro_$show")] ?: 0L
            val o: Long = p[androidx.datastore.preferences.core.longPreferencesKey("sk_outro_$show")] ?: 0L
            i to o
        }.first()

    suspend fun setIntro(show: String, ms: Long) =
        context.settingsStore.edit { it[androidx.datastore.preferences.core.longPreferencesKey("sk_intro_$show")] = ms }

    suspend fun setOutro(show: String, ms: Long) =
        context.settingsStore.edit { it[androidx.datastore.preferences.core.longPreferencesKey("sk_outro_$show")] = ms }

    suspend fun clearIntroOutro(show: String) =
        context.settingsStore.edit {
            it.remove(androidx.datastore.preferences.core.longPreferencesKey("sk_intro_$show"))
            it.remove(androidx.datastore.preferences.core.longPreferencesKey("sk_outro_$show"))
        }

    suspend fun setLastUpdateCheck(ts: Long) =
        context.settingsStore.edit { it[Keys.lastUpdateCheck] = ts }
}
