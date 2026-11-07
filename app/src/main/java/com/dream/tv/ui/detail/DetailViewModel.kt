package com.dream.tv.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dream.tv.data.CmsClient
import com.dream.tv.data.HistoryItem
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.VideoDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface DetailState {
    data object Loading : DetailState
    data class Ready(val detail: VideoDetail, val sourceName: String) : DetailState
    data class Error(val msg: String) : DetailState
}

class DetailViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    private val _favorite = MutableStateFlow(false)
    val favorite: StateFlow<Boolean> = _favorite

    private val _history = MutableStateFlow<HistoryItem?>(null)
    val history: StateFlow<HistoryItem?> = _history

    private var loadedId: Pair<String, String>? = null

    fun load(sourceId: String, vodId: String) {
        if (loadedId == sourceId to vodId && _state.value is DetailState.Ready) return
        loadedId = sourceId to vodId
        _state.value = DetailState.Loading
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            try {
                val source = requireNotNull(SourcesRepo.byId(ctx, sourceId)) { "未知源 $sourceId" }
                val detail = CmsClient.detail(source, vodId)
                _state.value = DetailState.Ready(detail, source.name)
                _favorite.value = (ctx as com.dream.tv.DreamApp).library.isFavorite(detail.source, detail.vod_id)
                _history.value = (ctx as com.dream.tv.DreamApp).library.historyOf(detail.showIdentifier)
            } catch (e: Exception) {
                _state.value = DetailState.Error(e.message ?: "加载失败")
            }
        }
    }

    fun toggleFavorite(detail: VideoDetail) {
        viewModelScope.launch {
            (getApplication<Application>() as com.dream.tv.DreamApp).library
                .toggleFavorite(detail, SourcesRepo.byId(getApplication(), detail.source)?.name ?: detail.source)
            _favorite.value = (getApplication<Application>() as com.dream.tv.DreamApp).library
                .isFavorite(detail.source, detail.vod_id)
        }
    }
}
