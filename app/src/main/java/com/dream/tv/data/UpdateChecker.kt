package com.dream.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 检查更新 —— 主路径零限流：
 * GET github.com/.../releases/latest 跟随重定向，从最终 URL 解析最新 tag；
 * API（releases/latest）只用来补充更新日志与 APK 直链，失败不影响主判断。
 */
object UpdateChecker {

    const val REPO = "frankshare/dream-tv-android"
    private const val LATEST_PAGE = "https://github.com/$REPO/releases/latest"
    private const val LATEST_API = "https://api.github.com/repos/$REPO/releases/latest"

    /** GitHub release 资产加速镜像（大陆直连 objects/release-assets 被限速到几十 KB/s，经镜像实测 ~2MB/s） */
    const val GH_MIRROR = "https://ghfast.top/"

    /** APK 直链转加速链接；仅处理 /releases/download/ 路径，Release 页面等原样返回 */
    fun accelerated(url: String): String =
        if (url.startsWith("https://github.com/") && url.contains("/releases/download/")) GH_MIRROR + url
        else url

    @Serializable
    data class GhAsset(
        val name: String = "",
        @kotlinx.serialization.SerialName("browser_download_url") val browserDownloadUrl: String = "",
    )

    @Serializable
    data class GhRelease(
        @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
        val body: String? = null,
        val assets: List<GhAsset> = emptyList(),
    )

    data class ReleaseInfo(
        val version: String,     // "1.3.0"（无 v 前缀）
        val pageUrl: String,     // Release 页面（浏览器打开可下载）
        val notes: String? = null,
        val apkUrl: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 返回最新版本信息；网络失败返回 null（静默） */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            // 主路径：重定向 URL 拿 tag（不受 API 限流影响）
            val resp = http.newCall(
                Request.Builder().url(LATEST_PAGE).header("User-Agent", "DreamTV-Android").build()
            ).execute()
            val finalPath = resp.request.url.encodedPath   // .../releases/tag/v1.3.0
            resp.close()
            val tag = finalPath.substringAfterLast("/").removePrefix("v").takeIf {
                finalPath.contains("/releases/tag/") && it.isNotBlank()
            } ?: return@withContext null

            // 增强：API 拿更新日志与 APK 直链（失败忽略）
            var notes: String? = null
            var apkUrl: String? = null
            runCatching {
                val api = http.newCall(
                    Request.Builder().url(LATEST_API).header("User-Agent", "DreamTV-Android").build()
                ).execute()
                val body = api.body?.string()
                api.close()
                if (api.isSuccessful && body != null) {
                    val rel = json.decodeFromString<GhRelease>(body)
                    notes = rel.body
                    apkUrl = rel.assets.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
                }
            }
            ReleaseInfo(
                version = tag,
                pageUrl = "https://github.com/$REPO/releases/tag/v$tag",
                notes = notes,
                apkUrl = apkUrl,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 语义化版本比较：latest > current 返回 true */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.removePrefix("v").split('.').map { it.trim().toIntOrNull() ?: 0 }
        val c = current.removePrefix("v").split('.').map { it.trim().toIntOrNull() ?: 0 }
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val li = l.getOrElse(i) { 0 }
            val ci = c.getOrElse(i) { 0 }
            if (li != ci) return li > ci
        }
        return false
    }
}
