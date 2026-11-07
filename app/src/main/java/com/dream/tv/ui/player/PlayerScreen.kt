package com.dream.tv.ui.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.dream.tv.AppViewModel
import com.dream.tv.data.CmsClient
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.VideoDetail
import com.dream.tv.ui.glass.DtIcons
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 播放页 —— 对标主流播放器交互：
 * · 单击：显隐控制层          · 双击两侧：±10s / 双击中间：播放暂停
 * · 长按：2x 倍速（松开恢复）  · 横滑：快进快退（气泡预览 from→to）
 * · 左半屏竖滑：亮度          · 右半屏竖滑：音量
 * · 锁定：只留解锁钮          · 倍速菜单 0.5x–3x
 */
@Composable
fun PlayerScreen(
    app: AppViewModel,
    sourceId: String,
    vodId: String,
    startEpisode: Int,
    resumeMs: Long,
    onBack: () -> Unit,
    onPlayEpisode: (String, String, Int) -> Unit,
) {
    val glass = LocalGlassColors.current
    val context = LocalContext.current
    var detail by remember { mutableStateOf<VideoDetail?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var episodeIndex by remember(sourceId, vodId) { mutableStateOf(startEpisode) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(sourceId, vodId) {
        playbackError = null
        try {
            val src = requireNotNull(SourcesRepo.byId(app.store, sourceId)) { "未知源" }
            detail = CmsClient.detail(src, vodId)
        } catch (e: Exception) {
            loadError = e.message ?: "加载失败"
        }
    }

    val episodes = detail?.episodes.orEmpty()
    val episode = episodes.getOrNull(episodeIndex)

    val player = remember {
        ExoPlayer.Builder(context).build().apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // 系统栏 / 横屏 / 常亮
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = window?.let {
            WindowInsetsControllerCompat(it, it.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // 换集：设 MediaItem + prepare（续播见下方 LaunchedEffect）
    DisposableEffect(detail, episodeIndex) {
        val ep = episode
        if (ep != null && ep.url.isNotBlank()) {
            playbackError = null
            player.setMediaItem(MediaItem.fromUri(ep.url))
            player.prepare()
        }
        onDispose { }
    }

    // 续播
    LaunchedEffect(detail, episodeIndex) {
        val d = detail ?: return@LaunchedEffect
        if (resumeMs > 0L) {
            player.seekTo(resumeMs)
        } else {
            val h = app.store.library.historyOf(d.showIdentifier)
            if (h != null && h.episodeIndex == episodeIndex &&
                h.playbackPosition > 10_000 && h.duration > 0 &&
                h.playbackPosition < h.duration - 30_000
            ) {
                player.seekTo(h.playbackPosition)
            }
        }
    }

    // 进度记忆：5s 节流
    LaunchedEffect(detail, episodeIndex) {
        val d = detail ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (player.isPlaying && player.currentPosition > 0) {
                app.store.library.saveProgress(d, episodeIndex, player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }

    // 退出/换集瞬间保存最终进度
    DisposableEffect(detail, episodeIndex) {
        onDispose {
            val d = detail ?: return@onDispose
            if (player.currentPosition > 3_000) {
                kotlinx.coroutines.MainScope().launch {
                    app.store.library.saveProgress(
                        d, episodeIndex,
                        player.currentPosition, player.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    DisposableEffect(detail, episodeIndex) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                playbackError = error.errorCodeName
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ---------- 状态 ----------
    var controlsVisible by remember { mutableStateOf(true) }
    var drawer by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.takeIf { it > 0 } ?: 0
            isPlaying = player.isPlaying
            speed = player.playbackParameters.speed
            delay(400)
        }
    }

    // 控制层自动隐藏
    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && (isPlaying || locked)) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // ---------- 手势状态（气泡） ----------
    var seekActive by remember { mutableStateOf(false) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekForward by remember { mutableStateOf(true) }
    var speedBoost by remember { mutableStateOf(false) }
    var brightActive by remember { mutableStateOf(false) }
    var brightValue by remember { mutableFloatStateOf(0.5f) }
    var volActive by remember { mutableStateOf(false) }
    var volValue by remember { mutableFloatStateOf(0f) }
    var flashHint by remember { mutableStateOf<String?>(null) }   // 双击 ±10s 提示

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    fun seekBy(deltaMs: Long) {
        if (durationMs <= 0) return
        val target = (player.currentPosition + deltaMs).coerceIn(0, durationMs)
        player.seekTo(target)
        flashHint = (if (deltaMs > 0) "快进 " else "快退 ") + "${abs(deltaMs) / 1000}s"
    }

    LaunchedEffect(flashHint) {
        if (flashHint != null) {
            delay(700)
            flashHint = null
        }
    }

    fun togglePlay() {
        if (player.isPlaying) player.pause() else player.play()
    }

    // ---------- 根布局 ----------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
            modifier = Modifier.fillMaxSize(),
        )

        // 手势层（控制条按钮消费掉自己的事件，其余落到这里）
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(locked) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (locked) return@detectTapGestures
                            when {
                                offset.x < size.width / 3 -> seekBy(-10_000)
                                offset.x > size.width * 2 / 3 -> seekBy(10_000)
                                else -> togglePlay()
                            }
                        },
                        onLongPress = {
                            if (!locked && player.isPlaying) {
                                player.setPlaybackSpeed(2f)
                                speedBoost = true
                            }
                        },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                if (speedBoost) {
                                    player.setPlaybackSpeed(1f)
                                    speedBoost = false
                                }
                            }
                        },
                    )
                }
                .pointerInput(locked, durationMs > 0) {
                    // 拖动：横滑=快进退 / 左竖滑=亮度 / 右竖滑=音量
                    var mode = 0 // 0未定 1seek 2亮度 3音量
                    var startX = 0f
                    var startY = 0f
                    var startPos = 0L
                    var startBright = 0f
                    var startVol = 0
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = 0
                            startX = offset.x
                            startY = offset.y
                            startPos = player.currentPosition
                            startBright = (context as? Activity)?.window?.attributes?.screenBrightness
                                ?: 0.5f
                            startVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            if (locked) return@detectDragGestures
                            if (mode == 0) {
                                val dx = change.position.x - startX
                                val dy = change.position.y - startY
                                if (abs(dx) > 28 || abs(dy) > 28) {
                                    mode = when {
                                        abs(dx) > abs(dy) -> 1
                                        startX < size.width / 2f -> 2
                                        else -> 3
                                    }
                                }
                            }
                            when (mode) {
                                1 -> if (durationMs > 0) {
                                    if (!seekActive) startPos = player.currentPosition
                                    seekActive = true
                                    seekDeltaMs =
                                        ((change.position.x - startX) / size.width * 90_000).toLong()
                                    seekForward = seekDeltaMs >= 0
                                    seekTargetMs = (startPos + seekDeltaMs).coerceIn(0, durationMs)
                                }

                                2 -> {
                                    brightActive = true
                                    brightValue =
                                        (startBright + (startY - change.position.y) / size.height)
                                            .coerceIn(0.01f, 1f)
                                    (context as? Activity)?.window?.attributes =
                                        (context as Activity).window.attributes
                                            .also { it.screenBrightness = brightValue }
                                }

                                3 -> {
                                    volActive = true
                                    val target = (startVol + ((startY - change.position.y) / size.height *
                                        maxVolume).roundToInt()).coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                    volValue = target.toFloat() / maxVolume
                                }
                            }
                        },
                        onDragEnd = {
                            if (mode == 1 && seekActive) player.seekTo(seekTargetMs)
                            seekActive = false
                            brightActive = false
                            volActive = false
                            mode = 0
                        },
                        onDragCancel = {
                            seekActive = false
                            brightActive = false
                            volActive = false
                            mode = 0
                        },
                    )
                }
        )

        // 加载中
        if (detail == null && loadError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = glass.accent)
            }
        }
        loadError?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.padding(30.dp), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("加载失败", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(msg, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "返回", color = glass.accent,
                            modifier = Modifier.glassClick(onClick = onBack),
                        )
                    }
                }
            }
        }
        playbackError?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.padding(30.dp), shape = RoundedCornerShape(20.dp)) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("播放出错", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(err, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Text(
                                "重试",
                                color = glass.accent,
                                modifier = Modifier
                                    .glassClick {
                                        playbackError = null
                                        episode?.let {
                                            player.setMediaItem(MediaItem.fromUri(it.url))
                                            player.prepare()
                                        }
                                    }
                                    .padding(6.dp),
                            )
                            if (episodeIndex + 1 < episodes.size) {
                                Text(
                                    "下一集",
                                    color = glass.accent,
                                    modifier = Modifier
                                        .glassClick { onPlayEpisode(sourceId, vodId, episodeIndex + 1) }
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------- 中央手势气泡 ----------
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = seekActive, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (seekForward) "▶▶ 快进" else "◀◀ 快退",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${abs(seekDeltaMs) / 1000}s",
                                color = glass.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${format(seekTargetMs - seekDeltaMs)} → ${format(seekTargetMs)}",
                            color = Color.White.copy(alpha = 0.75f),
                            fontSize = 11.sp,
                        )
                    }
                }
                AnimatedVisibility(visible = speedBoost, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "▶▶", color = glass.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "2x 倍速中", color = Color.White, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                AnimatedVisibility(visible = flashHint != null, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Text(
                            flashHint.orEmpty(),
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // 亮度 / 音量侧边指示
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = brightActive,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                VerticalMeter(
                    icon = {
                        Icon(DtIcons.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    },
                    value = brightValue,
                )
            }
            AnimatedVisibility(
                visible = volActive,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                VerticalMeter(
                    icon = {
                        Icon(DtIcons.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    },
                    value = volValue,
                )
            }
        }

        // ---------- 锁定模式：只留解锁钮 ----------
        if (locked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(), exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                Box(
                    Modifier
                        .padding(top = 24.dp, end = 16.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .glassClick {
                            locked = false
                            controlsVisible = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(DtIcons.LockOpen, "解锁", tint = Color.White, modifier = Modifier.size(19.dp))
                }
            }
        }

        // ---------- 控制层 ----------
        AnimatedVisibility(
            visible = controlsVisible && !locked,
            enter = fadeIn() + slideInVertically { it / 10 },
            exit = fadeOut() + slideOutVertically { it / 10 },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxSize()) {
                // 顶栏（渐变 scrim）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "返回",
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(7.dp)
                            .glassClick(onClick = onBack),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail?.vod_name ?: "加载中",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        episode?.let {
                            Text(
                                "${it.name} · ${SourcesRepo.byId(app.store, sourceId)?.name.orEmpty()}",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Icon(
                        DtIcons.Lock, "锁定",
                        tint = Color.White,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(9.dp)
                            .glassClick {
                                locked = true
                                drawer = false
                                speedMenu = false
                            },
                    )
                    Spacer(Modifier.width(14.dp))
                }

                Spacer(Modifier.weight(1f))

                // 底栏（渐变 scrim）
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            format(positionMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                        )
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac ->
                                if (durationMs > 0) player.seekTo((frac * durationMs).toLong())
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = glass.accent,
                                activeTrackColor = glass.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .height(26.dp),
                        )
                        Text(
                            format(durationMs),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isPlaying) DtIcons.Pause else Icons.Filled.PlayArrow,
                            "播放/暂停",
                            tint = Color.White,
                            modifier = Modifier
                                .size(34.dp)
                                .glassClick { togglePlay() },
                        )
                        Spacer(Modifier.width(18.dp))
                        Icon(
                            DtIcons.Replay, "后退10秒",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .glassClick { seekBy(-10_000) },
                        )
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            DtIcons.Forward, "快进10秒",
                            tint = Color.White,
                            modifier = Modifier
                                .size(26.dp)
                                .glassClick { seekBy(10_000) },
                        )
                        Spacer(Modifier.width(14.dp))
                        if (episodeIndex > 0) {
                            Icon(
                                DtIcons.SkipPrevious, "上一集",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(34.dp)
                                    .padding(4.dp)
                                    .glassClick { onPlayEpisode(sourceId, vodId, episodeIndex - 1) },
                            )
                            Spacer(Modifier.width(14.dp))
                        }
                        if (episodeIndex + 1 < episodes.size) {
                            Icon(
                                DtIcons.SkipNext, "下一集",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(34.dp)
                                    .padding(4.dp)
                                    .glassClick { onPlayEpisode(sourceId, vodId, episodeIndex + 1) },
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .glassClick { speedMenu = !speedMenu }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                if (speed == 1f) "倍速" else "${speed}x",
                                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "选集",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.glassClick { drawer = !drawer },
                        )
                    }
                }
            }

            // 倍速菜单
            AnimatedVisibility(
                visible = speedMenu,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut() + slideOutVertically { it / 4 },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                GlassSurface(
                    Modifier
                        .padding(end = 14.dp, bottom = 76.dp)
                        .width(210.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(
                            "播放倍速",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        )
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                            val isSel = abs(speed - s) < 0.01f
                            Text(
                                if (s == 1f) "1.0x 常速" else "${s}x",
                                color = if (isSel) glass.accent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassClick {
                                        player.setPlaybackSpeed(s)
                                        speedMenu = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            // 选集抽屉
            AnimatedVisibility(
                visible = drawer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassSurface(
                    Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(10.dp),
                    shape = RoundedCornerShape(22.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            "选集（${episodes.size}）",
                            color = glass.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(episodes) { idx, ep ->
                                val isCur = idx == episodeIndex
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isCur) glass.accent.copy(alpha = 0.25f)
                                            else glass.textPrimary.copy(alpha = 0.05f)
                                        )
                                        .glassClick {
                                            drawer = false
                                            if (idx != episodeIndex) onPlayEpisode(sourceId, vodId, idx)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        ep.name,
                                        color = if (isCur) glass.accent else glass.textPrimary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 中央手势气泡（黑 55% 胶囊） */
@Composable
private fun GestureBubble(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

/** 亮度/音量侧边竖条指示 */
@Composable
private fun VerticalMeter(icon: @Composable () -> Unit, value: Float) {
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .width(4.dp)
                .height(110.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0f, 1f))
                    .align(Alignment.BottomCenter)
                    .background(Color.White)
            )
        }
    }
}

private fun format(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
