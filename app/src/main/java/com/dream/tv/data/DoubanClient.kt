package com.dream.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 豆瓣推荐 —— 移植 web 版 `app/api/douban/recommend/route.ts`：
 * 原生直连 + UA/Referer 反防盗链（安卓无 CORS，不需要代理）。
 */
object DoubanClient {

    /** 图片 Referer —— 交给 Coil 的 ImageLoader（DreamApp）对豆瓣域加头 */
    const val IMAGE_REFERER = "https://movie.douban.com/"

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"

    suspend fun recommend(
        type: String,        // movie | tv
        tag: String,
        pageStart: Int = 0,
        pageLimit: Int = 24,
    ): List<DoubanSubject> = withContext(Dispatchers.IO) {
        val url = "https://movie.douban.com/j/search_subjects" +
            "?type=$type&tag=${urlEncode(tag)}&sort=recommend" +
            "&page_limit=$pageLimit&page_start=$pageStart"
        val body = http.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", "https://movie.douban.com/")
                .build()
        ).execute().use { resp ->
            require(resp.isSuccessful) { "Douban API returned ${resp.code}" }
            resp.body?.string().orEmpty()
        }
        CmsClient.json.decodeFromString<DoubanResponse>(body).subjects
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())

    /** 首页页签（对齐 web 版豆瓣默认标签） */
    data class DoubanTab(val type: String, val tag: String)

    val tabs: List<DoubanTab> = listOf(
        DoubanTab("movie", "热门"),
        DoubanTab("tv", "热门"),
        DoubanTab("movie", "豆瓣高分"),
        DoubanTab("tv", "国产剧"),
        DoubanTab("movie", "最新"),
        DoubanTab("tv", "美剧"),
        DoubanTab("movie", "冷门佳片"),
        DoubanTab("tv", "综艺"),
    )
}
