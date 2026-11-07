package com.dream.tv.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dream.tv.data.CmsClient
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.VideoItem
import com.dream.tv.data.VideoSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout

/** 单源运行状态 */
sealed interface SourceRun {
    data object Idle : SourceRun
    data object Running : SourceRun
    data class Done(val count: Int, val ms: Long) : SourceRun
    data class Failed(val msg: String) : SourceRun
}

data class SourceState(val source: VideoSource, val run: SourceRun)

/** 聚合结果卡片：同名影片多源归并 */
data class GroupCard(
    val key: String,
    val name: String,
    val poster: String?,
    val year: String?,
    val typeName: String?,
    val remarks: String?,
    val members: List<Member>,
) {
    data class Member(val source: VideoSource, val item: VideoItem)
}

private fun normalize(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), "")

/**
 * 78 源并行聚合搜索 —— 对齐 web 版 /api/search-parallel 编排：
 * 每源 20s 超时、并行受限、结果流式归并上屏。
 */
class SearchViewModel(app: Application) : AndroidViewModel(app) {

    private val semaphore = Semaphore(16)

    private val _states = MutableStateFlow<List<SourceState>>(emptyList())
    val states: StateFlow<List<SourceState>> = _states

    private val _groups = MutableStateFlow<List<GroupCard>>(emptyList())
    val groups: StateFlow<List<GroupCard>> = _groups

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private var job: Job? = null
    private val memberMap = LinkedHashMap<String, MutableList<GroupCard.Member>>()

    fun search(rawQuery: String) {
        val q = rawQuery.trim()
        if (q.isEmpty()) return
        job?.cancel()
        _query.value = q
        _groups.value = emptyList()
        memberMap.clear()
        _states.value = emptyList()
        _running.value = true
        job = viewModelScope.launch {
            // 尊重设置里的禁用源（修复：此前写死空集导致禁用不生效）
            val disabled = (getApplication<Application>() as com.dream.tv.DreamApp)
                .settings.settings.first().disabledSources
            val sources = SourcesRepo.enabled(getApplication(), disabled)
            _states.value = sources.map { SourceState(it, SourceRun.Idle) }
            runAll(sources, q)
        }
    }

    fun cancel() {
        job?.cancel()
        _running.value = false
    }

    private suspend fun runAll(sources: List<VideoSource>, q: String) = coroutineScope {
        sources.map { source ->
            async {
                semaphore.withPermit {
                    updateRun(source) { SourceRun.Running }
                    try {
                        val t0 = System.currentTimeMillis()
                        val resp = withTimeout(20_000) { CmsClient.search(source, q) }
                        val ms = System.currentTimeMillis() - t0
                        updateRun(source) { SourceRun.Done(resp.list.size, ms) }
                        if (resp.list.isNotEmpty()) {
                            val members = resp.list
                                .filter { it.vod_name.isNotBlank() }
                                .map { GroupCard.Member(source, it.copy(source = source.id)) }
                            synchronized(memberMap) {
                                members.forEach { m ->
                                    memberMap.getOrPut(normalize(m.item.vod_name)) {
                                        mutableListOf()
                                    }.add(m)
                                }
                                publishGroups(q)
                            }
                        }
                    } catch (e: Exception) {
                        if (e !is kotlinx.coroutines.CancellationException) {
                            updateRun(source) { SourceRun.Failed(e.message ?: "failed") }
                        }
                    }
                }
            }
        }.awaitAll()
        _running.value = false
    }

    private fun publishGroups(q: String) {
        val list = memberMap.map { (key, members) ->
            val best = members.firstOrNull { !it.item.vod_pic.isNullOrBlank() } ?: members.first()
            GroupCard(
                key = key,
                name = best.item.vod_name,
                poster = best.item.vod_pic,
                year = best.item.vod_year,
                typeName = best.item.type_name,
                remarks = members.mapNotNull { it.item.vod_remarks }
                    .firstOrNull { it.isNotBlank() },
                members = members.sortedBy { it.source.priority },
            )
        }.sortedWith(
            compareByDescending<GroupCard> { it.name == q }
                .thenByDescending { it.members.size }
        )
        _groups.value = list
    }

    private fun updateRun(source: VideoSource, f: (SourceRun) -> SourceRun) {
        _states.value = _states.value.map {
            if (it.source.id == source.id) it.copy(run = f(it.run)) else it
        }
    }
}
