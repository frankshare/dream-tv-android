package com.dream.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.dream.tv.data.DoubanClient
import com.dream.tv.data.LibraryStore
import com.dream.tv.data.SettingsStore
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

class DreamApp : Application(), ImageLoaderFactory {

    lateinit var settings: SettingsStore
        private set
    lateinit var library: LibraryStore
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        library = LibraryStore(this)
    }

    /**
     * Coil 图片加载器：豆瓣图床加 Referer 反防盗链（web 版 /api/douban/image 的原生替代）
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient {
                OkHttpClient.Builder()
                    .cache(Cache(File(cacheDir, "coil"), 200L * 1024 * 1024))
                    .addInterceptor { chain ->
                        val req = chain.request()
                        val host = req.url.host
                        chain.proceed(
                            if (host.endsWith("doubanio.com") || host.endsWith("douban.com")) {
                                req.newBuilder()
                                    .header("Referer", DoubanClient.IMAGE_REFERER)
                                    .header(
                                        "User-Agent",
                                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36"
                                    )
                                    .build()
                            } else req
                        )
                    }
                    .build()
            }
            .crossfade(true)
            .build()
}
