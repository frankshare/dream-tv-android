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
}
