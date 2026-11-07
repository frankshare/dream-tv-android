package com.dream.tv.data

import android.content.Context
import com.dream.tv.DreamApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.Collections

/**
 * 换源 —— 对齐 web 版 sourceMap 能力：
 * 用当前片名在其余启用源里搜同名影片，返回可切换的 (源, vod_id, 备注) 列表。
 */
object SourceSwitcher {

    fun normalize(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), "")

    /** 返回其他源的同名条目（按源 priority 排序），排除当前源 */
    suspend fun findAlternates(
        context: Context,
        title: String,
        excludeSourceId: String,
    ): List<VideoItem> = withContext(Dispatchers.IO) {
        val disabled = (context.applicationContext as DreamApp)
            .settings.settings.firstOrNull()?.disabledSources ?: emptySet()
        val sources = SourcesRepo.enabled(context, disabled)
            .filter { it.id != excludeSourceId }
        val sem = Semaphore(16)
        val key = normalize(title)
        val found: MutableList<Triple<VideoSource, VideoItem, Int>> =
            Collections.synchronizedList(mutableListOf())

        coroutineScope {
            sources.map { src ->
                async {
                    sem.withPermit {
                        try {
                            withTimeout(15_000) {
                                CmsClient.search(src, title).list
                                    .filter { normalize(it.vod_name) == key }
                                    .minByOrNull { it.vod_id.length } // 同源命中多条时取 id 最短的（通常是正片）
                                    ?.let { found.add(Triple(src, it.copy(source = src.id), src.priority)) }
                            }
                        } catch (_: Exception) {
                            // 单源失败静默跳过
                        }
                    }
                }
            }.awaitAll()
        }
        found.sortedBy { it.third }.map { it.second }
    }
}
