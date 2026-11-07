package com.dream.tv.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * 广告过滤数据源 —— 播放列表（m3u8）走 OkHttp 独立预取 + M3u8AdFilter 过滤 + 内存吐出
 * （与 media3 的 HttpDataSource 状态机完全解耦）；媒体分片走 DefaultHttpDataSource 透传。
 */
class AdFilterDataSource private constructor(
    private val http: DefaultHttpDataSource,
    private val enabled: Boolean,
) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var buffered: ByteArrayInputStream? = null
    private var bufferedUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        this.dataSpec = dataSpec
        buffered = null
        bufferedUri = null

        val url = dataSpec.uri.toString()
        if (!enabled || dataSpec.position > 0L || !looksLikePlaylist(url)) {
            val len = http.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }

        // 预取 + 过滤（OkHttp 自带重定向，跨协议也支持）
        try {
            val resp = playlistClient.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            ).execute()
            val body = resp.body?.string().orEmpty()
            resp.close()
            if (!body.startsWith("#EXTM3U")) {
                // 异常响应，退回上游直连
                val len = http.open(dataSpec)
                transferStarted(dataSpec)
                return len
            }
            val filtered = M3u8AdFilter.filter(body)
            Log.d("DreamTV", "playlist ${body.length} -> ${filtered.length} chars")
            val bytes = filtered.toByteArray(Charsets.UTF_8)
            buffered = ByteArrayInputStream(bytes)
            bufferedUri = dataSpec.uri
            transferStarted(dataSpec)
            return bytes.size.toLong()
        } catch (e: Exception) {
            Log.e("DreamTV", "playlist prefetch fail, fallback to upstream", e)
            val len = http.open(dataSpec)
            transferStarted(dataSpec)
            return len
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        buffered?.let { stream ->
            val n = stream.read(buffer, offset, length)
            return if (n <= 0) C.RESULT_END_OF_INPUT else n
        }
        return http.read(buffer, offset, length)
    }

    override fun getUri(): Uri? = bufferedUri ?: http.uri

    override fun close() {
        buffered = null
        transferEnded()
        http.close()
    }

    private fun looksLikePlaylist(url: String): Boolean {
        val u = url.substringBefore('?').lowercase()
        return u.endsWith(".m3u8") || u.endsWith(".m3u") || u.contains(".m3u8")
    }

    companion object {
        private val playlistClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    class Factory(private val enabled: Boolean) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            AdFilterDataSource(
                DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(mapOf("User-Agent" to "Mozilla/5.0"))
                    .createDataSource() as DefaultHttpDataSource,
                enabled,
            )
    }
}
