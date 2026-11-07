package com.dream.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * 弹弹play 开放平台弹幕客户端。
 * 签名：X-Signature = Base64(HMAC-SHA256(AppId + Timestamp, AppSecret))
 * 文档：https://doc.dandanplay.com/open/
 */
object DanmakuClient {

    // 弹弹play 开放平台凭证（DevCenter 已创建应用 dreamtv）
    private const val APP_ID = "qbvhrbjnrd"
    private const val APP_SECRET = "UZWHxxZF32i5qhvPBKwzieIXAUMBNRdC"

    private const val BASE = "https://api.dandanplay.net"

    @Serializable
    data class DanmakuItem(
        val timeMs: Long,
        val text: String,
        val color: Int,      // 0xRRGGBB
        val fromTop: Boolean // 顶部固定弹幕
    )

    @Serializable
    data class EpisodeInfo(
        val episodeId: Long,
        val animeTitle: String,
        val episodeTitle: String,
    )

    @Serializable
    private data class SearchEpisodesResp(
        val success: Boolean = false,
        val animes: List<Anime> = emptyList(),
    ) {
        @Serializable
        data class Anime(val episodes: List<Ep> = emptyList()) {
            @Serializable
            data class Ep(
                val episodeId: Long = 0,
                val episodeTitle: String = "",
            )
        }
    }

    @Serializable
    private data class CommentResp(
        val count: Int = 0,
        val comments: List<C> = emptyList(),
    ) {
        @Serializable
        data class C(val cid: Long = 0, val p: String = "", val m: String = "")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 关键词搜剧集（剧名/片名），返回可挂弹幕的 episode 列表 */
    suspend fun searchEpisodes(keyword: String): List<EpisodeInfo> =
        withContext(Dispatchers.IO) {
            val url = "$BASE/api/v2/search/episodes?keyword=${urlEncode(keyword)}"
            val body = signedGet(url) ?: return@withContext emptyList()
            try {
                val resp = json.decodeFromString<SearchEpisodesResp>(body)
                resp.animes.flatMap { anime ->
                    anime.episodes.map { ep ->
                        EpisodeInfo(ep.episodeId, animeTitle = "", episodeTitle = ep.episodeTitle)
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** 拉取某集的弹幕（含相关弹幕库），解析为时间轴列表 */
    suspend fun fetchComments(episodeId: Long): List<DanmakuItem> =
        withContext(Dispatchers.IO) {
            val url = "$BASE/api/v2/comment/$episodeId?withRelated=true&chConvert=1"
            val body = signedGet(url) ?: return@withContext emptyList()
            try {
                val resp = json.decodeFromString<CommentResp>(body)
                resp.comments.mapNotNull { c ->
                    // p = "time,mode,color,source"  mode: 1-3滚动 4底部 5顶部
                    val parts = c.p.split(",")
                    val timeSec = parts.getOrNull(0)?.toDoubleOrNull() ?: return@mapNotNull null
                    val mode = parts.getOrNull(1)?.toIntOrNull() ?: 1
                    val colorStr = parts.getOrNull(2)?.toLongOrNull() ?: 0xFFFFFF
                    DanmakuItem(
                        timeMs = (timeSec * 1000).toLong(),
                        text = c.m,
                        color = (colorStr and 0xFFFFFF).toInt(),
                        fromTop = mode >= 5,
                    )
                }.sortedBy { it.timeMs }
            } catch (_: Exception) {
                emptyList()
            }
        }

    // ---------- 签名请求 ----------

    private fun signedGet(url: String): String? = runCatching {
        val (ts, sig) = signature()
        val req = Request.Builder().url(url)
            .header("User-Agent", "DreamTV/1.4 (Android)")
            .header("X-AppId", APP_ID)
            .header("X-Timestamp", ts)
            .header("X-Signature", sig)
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            resp.body?.string()
        }
    }.getOrNull()

    private fun signature(): Pair<String, String> {
        val ts = (System.currentTimeMillis() / 1000).toString()
        val data = APP_ID + ts
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(APP_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val sig = Base64.encodeToString(mac.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        return ts to sig
    }

    private fun urlEncode(s: String): String =
        java.net.URLEncoder.encode(s, Charsets.UTF_8.name())
}
