package com.dream.tv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dream.tv.data.FavoriteItem
import com.dream.tv.data.GlassSettings
import com.dream.tv.data.HistoryItem
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.VideoDetail
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 全局状态入口：设置 + 收藏/历史 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val app_ = app as DreamApp
    val store get() = app_

    val settings: StateFlow<GlassSettings> = app_.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GlassSettings())

    val favorites: StateFlow<List<FavoriteItem>> = app_.library.favorites
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val history: StateFlow<List<HistoryItem>> = app_.library.history
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setThemeMode(mode: Int) = viewModelScope.launch { app_.settings.setThemeMode(mode) }

    fun setAccent(id: String, custom: Long?) =
        viewModelScope.launch { app_.settings.setAccent(id, custom) }

    fun setWallpaper(kind: String, gradient: String? = null, path: String? = null) =
        viewModelScope.launch { app_.settings.setWallpaper(kind, gradient, path) }

    fun toggleSource(id: String, enabled: Boolean) =
        viewModelScope.launch { app_.settings.toggleSource(id, enabled) }

    fun resetSources() = viewModelScope.launch { app_.settings.resetSources() }

    fun toggleFavorite(detail: VideoDetail) = viewModelScope.launch {
        val name = SourcesRepo.byId(getApplication(), detail.source)?.name
        app_.library.toggleFavorite(detail, name ?: detail.source)
    }

    fun removeFavorite(item: FavoriteItem) =
        viewModelScope.launch { app_.library.removeFavorite(item) }

    fun removeHistory(item: HistoryItem) =
        viewModelScope.launch { app_.library.removeHistory(item) }

    fun clearHistory() = viewModelScope.launch { app_.library.clearHistory() }

    // ---------- 检查更新 ----------

    data class UpdateState(
        val checking: Boolean = false,
        val latest: com.dream.tv.data.UpdateChecker.ReleaseInfo? = null,  // 手动检查结果（含"已是最新"= null+checked）
        val checked: Boolean = false,
        val isNewer: Boolean = false,
        val error: Boolean = false,
    )

    private val _update = MutableStateFlow(UpdateState())
    val update: StateFlow<UpdateState> = _update

    /** 启动静默检查：有新版才弹 pendingUpdate */
    private val _pendingUpdate =
        MutableStateFlow<com.dream.tv.data.UpdateChecker.ReleaseInfo?>(null)
    val pendingUpdate: StateFlow<com.dream.tv.data.UpdateChecker.ReleaseInfo?> = _pendingUpdate

    fun dismissPendingUpdate() { _pendingUpdate.value = null }

    fun checkUpdate(manual: Boolean) {
        _update.value = UpdateState(checking = true)
        viewModelScope.launch {
            try {
                val info = com.dream.tv.data.UpdateChecker.check()
                app_.settings.setLastUpdateCheck(System.currentTimeMillis())
                val ctx = getApplication<Application>()
                val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
                val newer = info != null && com.dream.tv.data.UpdateChecker.isNewer(info.version, current)
                _update.value = UpdateState(checked = true, latest = info, isNewer = newer, error = info == null)
                if (!manual && newer && info != null) _pendingUpdate.value = info
            } catch (_: Exception) {
                _update.value = UpdateState(checked = true, error = true)
            }
        }
    }

    /** 启动时调用：距上次检查超过 24h 才静默检查 */
    fun maybeAutoCheckUpdate() {
        viewModelScope.launch {
            val last = app_.settings.lastUpdateCheck()
            if (System.currentTimeMillis() - last > 24 * 3600_000L) checkUpdate(manual = false)
        }
    }
}
