package com.dream.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Apple CMS v10 聚合源客户端 —— 原生直连（无 CORS），移植 web 版
 * `lib/api/search-api.ts` / `lib/api/detail-api.ts` / `lib/api/parsers.ts`。
 */
object CmsClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true          // vod_id 数字/字符串混出，统一收成 String
        coerceInputValues = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0"

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        http.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Accept", "application/json, */*")
                .build()
        ).execute().use { resp ->
            require(resp.isSuccessful) { "HTTP ${resp.code}" }
            resp.body?.string().orEmpty()
        }
    }

    /** 搜索单源（web 版 searchVideosBySource）：`?ac=detail&wd=&pg=` */
    suspend fun search(source: VideoSource, query: String, page: Int = 1): ApiSearchResponse {
        val url = buildString {
            append(source.baseUrl.trimEnd('/'))
            append(source.searchPath)
            append(if (source.searchPath.contains('?')) '&' else '?')
            append("ac=detail&wd=").append(urlEncode(query))
            append("&pg=").append(page)
        }
        val data = json.decodeFromString<ApiSearchResponse>(get(url))
        require(data.code == 1 || data.code == 0) { data.msg ?: "Invalid API response" }
        return data
    }

    /** 详情（web 版 getVideoDetail）：`?ac=detail&ids=`，`$$$` 多播放组优先 m3u8 */
    suspend fun detail(source: VideoSource, id: String): VideoDetail {
        val url = buildString {
            append(source.baseUrl.trimEnd('/'))
            append(source.detailPath)
            append(if (source.detailPath.contains('?')) '&' else '?')
            append("ac=detail&ids=").append(urlEncode(id))
        }
        val data = json.decodeFromString<ApiDetailResponse>(get(url))
        require(data.code == 1 || data.code == 0) { data.msg ?: "Invalid API response" }
        val v = data.list.firstOrNull() ?: throw IllegalArgumentException("Video not found")

        val playFrom = (v.vod_play_from ?: "").split("$$$")
        val playUrls = (v.vod_play_url ?: "").split("$$$")
        // 与 web 版一致：优先选名字含 m3u8 的播放组
        val selectedIndex = playFrom.indexOfFirst { it.lowercase().contains("m3u8") }
            .takeIf { it != -1 && it < playUrls.size } ?: 0
        val episodes = parseEpisodes(playUrls.getOrNull(selectedIndex).orEmpty())

        return VideoDetail(
            vod_id = v.vod_id,
            vod_name = v.vod_name,
            vod_pic = v.vod_pic,
            vod_remarks = v.vod_remarks,
            vod_year = v.vod_year,
            vod_area = v.vod_area,
            vod_actor = v.vod_actor,
            vod_director = v.vod_director,
            vod_content = v.vod_content,
            type_name = v.type_name,
            vod_lang = v.vod_lang,
            episodes = episodes,
            source = source.id,
            source_code = playFrom.getOrNull(selectedIndex).orEmpty(),
        )
    }

    /**
     * 选集解析（web 版 lib/api/parsers.ts parseEpisodes）：
     * `第1集$url1#第2集$url2#...`，无 `$` 时整段视为 URL。
     */
    fun parseEpisodes(playUrl: String): List<Episode> {
        if (playUrl.isBlank()) return emptyList()
        return playUrl.split('#')
            .filter { it.isNotBlank() }
            .mapIndexed { index, seg ->
                val parts = seg.split('$')
                val (name, url) = if (parts.size > 1) {
                    parts[0] to parts[1]
                } else {
                    "第 ${index + 1} 集" to parts[0]
                }
                // 清理协议外的双斜杠（web 版 /([^:])\/\//g）
                Episode(
                    name = name.ifBlank { "第 ${index + 1} 集" },
                    url = url.replace(Regex("([^:])//"), "$1/"),
                    index = index,
                )
            }
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
}
