package com.dream.tv.ui.glass

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * 播放器自绘图标 —— material-icons-core 集合不含 Pause/SkipNext/Replay，
 * 按 Material Symbols 24dp 路径补齐（Apache-2.0）。
 */
object DtIcons {

    val Pause: ImageVector by lazy {
        iconOf("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    }

    val SkipNext: ImageVector by lazy {
        iconOf("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    }

    val SkipPrevious: ImageVector by lazy {
        iconOf("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
    }

    val Replay: ImageVector by lazy {
        iconOf(
            "Replay",
            "M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 " +
                "3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z"
        )
    }

    val Forward: ImageVector by lazy {
        iconOf(
            "Forward",
            "M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-" +
                "3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z"
        )
    }

    val Movie: ImageVector by lazy {
        iconOf(
            "Movie",
            "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 " +
                "1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z"
        )
    }

    val Lock: ImageVector by lazy {
        iconOf(
            "Lock",
            "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 " +
                "2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 " +
                "2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z"
        )
    }

    val LockOpen: ImageVector by lazy {
        iconOf(
            "LockOpen",
            "M12 17c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm6-9h-1V6c0-2.76-2.24-5-5-5S7 " +
                "3.24 7 6h1.9c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2H6c-1.1 0-2 .9-2 " +
                "2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm0 12H6V10h12v10z"
        )
    }

    val VolumeUp: ImageVector by lazy {
        iconOf(
            "VolumeUp",
            "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 " +
                "2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 " +
                "7-4.49 7-8.77s-2.99-7.86-7-8.77z"
        )
    }

    val BrightnessHigh: ImageVector by lazy {
        iconOf(
            "BrightnessHigh",
            "M20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 " +
                "20H20v-4.69L23.31 12 20 8.69zM12 18c-3.31 0-6-2.69-6-6s2.69-6 6-6 6 2.69 6 " +
                "6-2.69 6-6 6zm0-10c-2.21 0-4 1.79-4 4s1.79 4 4 4 4-1.79 4-4-1.79-4-4-4z"
        )
    }

    val History: ImageVector by lazy {
        iconOf(
            "History",
            "M13 3c-4.97 0-9 4.03-9 9H1l3.89 3.89.07.14L9 12H6c0-3.87 3.13-7 7-7s7 " +
                "3.13 7 7-3.13 7-7 7c-1.93 0-3.68-.79-4.94-2.06l-1.42 1.42C8.27 19.99 " +
                "10.51 21 13 21c4.97 0 9-4.03 9-9s-4.03-9-9-9zm-1 5v5l4.28 2.54.72-1.21-" +
                "3.5-2.08V8H12z"
        )
    }

    private fun iconOf(name: String, d: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            // 必须显式给 fill（默认 null 不涂色=隐形图标），tint 在 Icon 层覆盖
            .addPath(
                PathParser().parsePathString(d).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .build()
}
