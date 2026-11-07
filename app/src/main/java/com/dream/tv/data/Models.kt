package com.dream.tv.data

import kotlinx.serialization.Serializable

/**
 * Data models —— 与 web 版 `lib/types/index.ts` 逐字段对齐，
 * 便于两端收藏/历史语义一致（id/source/showIdentifier 互通）。
 */

/** Apple CMS v10 聚合源（web 版 VideoSource） */
@Serializable
data class VideoSource(
    val id: String,
    val name: String,
    val baseUrl: String,
    val searchPath: String = "",
    val detailPath: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0,
    val group: String = "normal",
)

/** 搜索结果条目（web 版 VideoItem）。CMS 的 vod_id 数字/字符串皆有，用 isLenient 统一收成 String */
@Serializable
data class VideoItem(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String? = null,
    val type_name: String? = null,
    val vod_remarks: String? = null,
    val vod_year: String? = null,
    val vod_area: String? = null,
    val vod_actor: String? = null,
    val vod_director: String? = null,
    val vod_content: String? = null,
    val vod_lang: String? = null,
    val source: String = "",
)

@Serializable
data class ApiSearchResponse(
    val code: Int? = null,
    val msg: String? = null,
    val pagecount: Int = 1,
    val list: List<VideoItem> = emptyList(),
)

@Serializable
data class ApiDetailVideo(
    val vod_id: String = "",
    val vod_name: String = "",
    val vod_pic: String = "",
    val vod_remarks: String? = null,
    val vod_year: String? = null,
    val vod_area: String? = null,
    val vod_actor: String? = null,
    val vod_director: String? = null,
    val vod_content: String? = null,
    val type_name: String? = null,
    val vod_lang: String? = null,
    val vod_play_from: String? = null,
    val vod_play_url: String? = null,
)

@Serializable
data class ApiDetailResponse(
    val code: Int? = null,
    val msg: String? = null,
    val list: List<ApiDetailVideo> = emptyList(),
)

/** 单集（web 版 Episode） */
@Serializable
data class Episode(val name: String, val url: String, val index: Int)

/** 影片详情（web 版 VideoDetail） */
@Serializable
data class VideoDetail(
    val vod_id: String,
    val vod_name: String,
    val vod_pic: String,
    val vod_remarks: String? = null,
    val vod_year: String? = null,
    val vod_area: String? = null,
    val vod_actor: String? = null,
    val vod_director: String? = null,
    val vod_content: String? = null,
    val type_name: String? = null,
    val vod_lang: String? = null,
    val episodes: List<Episode> = emptyList(),
    val source: String,
    val source_code: String = "",
) {
    val showIdentifier: String get() = "$source|$vod_id"
}

/** 收藏条目（web 版 FavoriteItem） */
@Serializable
data class FavoriteItem(
    val videoId: String,
    val title: String,
    val poster: String? = null,
    val source: String,
    val sourceName: String? = null,
    val addedAt: Long,
    val type: String? = null,
    val year: String? = null,
    val remarks: String? = null,
)

/** 历史条目（web 版 VideoHistoryItem） */
@Serializable
data class HistoryItem(
    val videoId: String,
    val title: String,
    val episodeIndex: Int = 0,
    val source: String,
    val timestamp: Long,
    val playbackPosition: Long = 0,
    val duration: Long = 0,
    val poster: String? = null,
    val episodes: List<Episode> = emptyList(),
    val showIdentifier: String,
    val remarks: String? = null,
    val type_name: String? = null,
    val vod_area: String? = null,
)

/** 豆瓣 search_subjects 条目 */
@Serializable
data class DoubanSubject(
    val title: String = "",
    val rate: String = "",
    val url: String = "",
    val cover: String = "",
    val playable: Boolean = false,
    val is_new: Boolean = false,
)

@Serializable
data class DoubanResponse(
    val subjects: List<DoubanSubject> = emptyList(),
)
