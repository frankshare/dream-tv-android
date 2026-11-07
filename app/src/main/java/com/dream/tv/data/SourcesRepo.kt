package com.dream.tv.data

import android.content.Context

/**
 * 78 个内置源 —— assets/sources.json（由 tools 脚本从 web 版
 * `lib/api/default-sources.ts` 烘焙），启用状态来自 SettingsStore 的禁用表。
 */
object SourcesRepo {

    @Volatile
    private var cached: List<VideoSource>? = null

    fun load(context: Context): List<VideoSource> {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val text = context.assets.open("sources.json").bufferedReader().use { it.readText() }
            val list = CmsClient.json.decodeFromString<List<VideoSource>>(text)
            cached = list
            return list
        }
    }

    fun byId(context: Context, id: String): VideoSource? =
        load(context).firstOrNull { it.id == id }

    /** 全部源（含禁用），按 priority 排序 */
    fun all(context: Context): List<VideoSource> = load(context).sortedBy { it.priority }

    /** 启用中的源（搜索/播放用） */
    fun enabled(context: Context, disabled: Set<String>): List<VideoSource> =
        all(context).filter { !disabled.contains(it.id) }
}
