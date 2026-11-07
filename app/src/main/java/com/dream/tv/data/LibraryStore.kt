package com.dream.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private val Context.libraryStore by preferencesDataStore(name = "dreamtv-library")

/**
 * 收藏 + 历史 —— 语义对齐 web 版 favorites-store / history-store：
 * 收藏上限 100 条（新的顶掉旧的）、历史按 showIdentifier 去重、进度节流保存。
 */
class LibraryStore(private val context: Context) {

    private object Keys {
        val favorites = stringPreferencesKey("favorites")
        val history = stringPreferencesKey("history")
    }

    private val favSer = ListSerializer(FavoriteItem.serializer())
    private val hisSer = ListSerializer(HistoryItem.serializer())

    val favorites: Flow<List<FavoriteItem>> = context.libraryStore.data.map { p ->
        decodeList(p[Keys.favorites], favSer)
    }

    val history: Flow<List<HistoryItem>> = context.libraryStore.data.map { p ->
        decodeList(p[Keys.history], hisSer).sortedByDescending { it.timestamp }
    }

    // ---------- 收藏 ----------

    suspend fun isFavorite(source: String, videoId: String): Boolean =
        favorites.first().any { it.videoId == videoId && it.source == source }

    suspend fun toggleFavorite(detail: VideoDetail, sourceName: String) {
        val id = detail.vod_id
        context.libraryStore.edit { p ->
            val cur = decodeList(p[Keys.favorites], favSer).toMutableList()
            val existing = cur.firstOrNull { it.videoId == id && it.source == detail.source }
            if (existing != null) {
                cur.remove(existing)
            } else {
                cur.add(
                    0,
                    FavoriteItem(
                        videoId = id,
                        title = detail.vod_name,
                        poster = detail.vod_pic,
                        source = detail.source,
                        sourceName = sourceName,
                        addedAt = System.currentTimeMillis(),
                        type = detail.type_name,
                        year = detail.vod_year,
                        remarks = detail.vod_remarks,
                    )
                )
                if (cur.size > 100) cur.subList(100, cur.size).clear()
            }
            p[Keys.favorites] = CmsClient.json.encodeToString(favSer, cur)
        }
    }

    suspend fun removeFavorite(item: FavoriteItem) = context.libraryStore.edit { p ->
        val cur = decodeList(p[Keys.favorites], favSer).toMutableList()
        cur.removeAll { it.videoId == item.videoId && it.source == item.source }
        p[Keys.favorites] = CmsClient.json.encodeToString(favSer, cur)
    }

    // ---------- 历史 ----------

    suspend fun historyOf(showIdentifier: String): HistoryItem? =
        history.first().firstOrNull { it.showIdentifier == showIdentifier }

    /** 播放器每 5s 节流调用；与 web 版一样「整季集数列表」也存进历史，便于续播 */
    suspend fun saveProgress(
        detail: VideoDetail,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long,
    ) {
        val entry = HistoryItem(
            videoId = detail.vod_id,
            title = detail.vod_name,
            episodeIndex = episodeIndex,
            source = detail.source,
            timestamp = System.currentTimeMillis(),
            playbackPosition = positionMs,
            duration = durationMs,
            poster = detail.vod_pic,
            episodes = detail.episodes,
            showIdentifier = detail.showIdentifier,
            remarks = detail.vod_remarks,
            type_name = detail.type_name,
            vod_area = detail.vod_area,
        )
        context.libraryStore.edit { p ->
            val cur = decodeList(p[Keys.history], hisSer)
                .filterNot { it.showIdentifier == entry.showIdentifier }
                .toMutableList()
            cur.add(0, entry)
            if (cur.size > 200) cur.subList(200, cur.size).clear()
            p[Keys.history] = CmsClient.json.encodeToString(hisSer, cur)
        }
    }

    suspend fun removeHistory(item: HistoryItem) = context.libraryStore.edit { p ->
        val cur = decodeList(p[Keys.history], hisSer)
            .filterNot { it.showIdentifier == item.showIdentifier }
        p[Keys.history] = CmsClient.json.encodeToString(hisSer, cur)
    }

    suspend fun clearHistory() = context.libraryStore.edit { it.remove(Keys.history) }

    // ---------- utils ----------

    private fun <T> decodeList(raw: String?, ser: KSerializer<List<T>>): List<T> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            CmsClient.json.decodeFromString(ser, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
