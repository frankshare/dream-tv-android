package com.dream.tv.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 壁纸取色 —— 逐行移植 web 版 `lib/config/accent.ts sampleAccentFromImage`：
 * 48px 采样 → HSL 色相 24° 分桶 → 取最大桶均值 → s∈[0.45,0.8] 截断、l=0.56。
 */
object AccentSampler {

    fun sampleFromBitmap(src: Bitmap?): Color? {
        src ?: return null
        val size = 48
        val bmp = try {
            Bitmap.createScaledBitmap(src, size, size, true)
        } catch (_: Exception) {
            return null
        }
        val pixels = IntArray(size * size)
        bmp.getPixels(pixels, 0, size, 0, 0, size, size)
        if (bmp !== src) bmp.recycle()

        // 桶 key → (count, hSum, sSum)
        val buckets = HashMap<Int, FloatArray>(3)
        for (px in pixels) {
            val a = (px ushr 24) and 0xFF
            if (a < 128) continue
            val r = ((px shr 16) and 0xFF) / 255f
            val g = ((px shr 8) and 0xFF) / 255f
            val b = (px and 0xFF) / 255f
            val (h, s, l) = rgbToHsl(r, g, b)
            if (s < 0.28f || l < 0.18f || l > 0.78f) continue
            val key = (h / 24f).roundToInt() * 24
            val bucket = buckets.getOrPut(key) { floatArrayOf(0f, 0f, 0f) }
            bucket[0] += 1f; bucket[1] += h; bucket[2] += s
        }
        var best: FloatArray? = null
        for (b in buckets.values) if (best == null || b[0] > best[0]) best = b
        best ?: return null
        val h = best[1] / best[0]
        val s = (best[2] / best[0]).coerceIn(0.45f, 0.8f)
        return Color.hsl(h, s, 0.56f)
    }

    fun sampleFromBytes(bytes: ByteArray): Color? {
        val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        return sampleFromBitmap(bmp)?.also { bmp.recycle() }
    }

    /** Color → ARGB Long（存设置用） */
    fun toArgbLong(c: Color): Long = c.toArgb().toLong() and 0xFFFFFFFFL

    fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
        val rr = r.coerceIn(0f, 1f); val gg = g.coerceIn(0f, 1f); val bb = b.coerceIn(0f, 1f)
        val mx = max(rr, max(gg, bb)); val mn = min(rr, min(gg, bb))
        val l = (mx + mn) / 2f
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        var h = when (mx) {
            rr -> ((gg - bb) / d + (if (gg < bb) 6f else 0f))
            gg -> ((bb - rr) / d + 2f)
            else -> ((rr - gg) / d + 4f)
        } * 60f
        if (h < 0) h += 360f
        return floatArrayOf(h, s, l)
    }
}
