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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.dream.tv.AppViewModel
import com.dream.tv.data.CmsClient
import com.dream.tv.data.SourcesRepo
import com.dream.tv.data.SourceSwitcher
import com.dream.tv.data.VideoDetail
import com.dream.tv.player.AdFilterDataSource
import com.dream.tv.ui.glass.DtIcons
import com.dream.tv.ui.glass.GlassSurface
import com.dream.tv.ui.glass.glassClick
import com.dream.tv.ui.theme.LocalGlassColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** 画面比例模式（对齐主流播放器「画面比例」菜单） */
enum class AspectMode(val label: String) {
    FIT("适应"), CROP("填充"), R16_9("16:9"), R4_3("4:3"), STRETCH("拉伸");
}

/**
 * 播放页 —— 对标主流播放器：
 * 等比缩放画面（适应/填充/16:9/4:3/拉伸）· 全手势 · 半屏网格选集 · 换源 · 倍速 · 锁定
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
    onSwitchSource: (String, String, Long) -> Unit,
) {
    val glass = LocalGlassColors.current
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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
        val adFilterOn = kotlinx.coroutines.runBlocking {
            (context.applicationContext as com.dream.tv.DreamApp).settings.adFilter()
        }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
                    AdFilterDataSource.Factory(adFilterOn)
                )
            )
            .build()
            .apply { playWhenReady = true }
    }
    DisposableEffect(Unit) { onDispose { player.release() } }

    // ---------- 画面比例：onVideoSizeChanged 等比缩放 ----------
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    var aspectMode by remember { mutableStateOf(AspectMode.FIT) }
    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0 && videoSize.width > 0) {
                    val pix = videoSize.pixelWidthHeightRatio
                    if (pix > 0f) videoAspect = videoSize.width * pix / videoSize.height
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

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

    // 换集
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

    // 进度记忆：5s 节流 + 退出保存
    LaunchedEffect(detail, episodeIndex) {
        val d = detail ?: return@LaunchedEffect
        while (true) {
            delay(5_000)
            if (player.isPlaying && player.currentPosition > 0) {
                app.store.library.saveProgress(d, episodeIndex, player.currentPosition, player.duration.coerceAtLeast(0))
            }
        }
    }
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
                android.util.Log.e("DreamTV", "player error", error)
                playbackError = error.errorCodeName
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                // 自动连播：播完自动下一集
                if (playbackState == Player.STATE_ENDED && episodeIndex + 1 < episodes.size) {
                    onPlayEpisode(sourceId, vodId, episodeIndex + 1)
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // ---------- 状态 ----------
    var controlsVisible by remember { mutableStateOf(true) }
    var drawer by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }
    var aspectMenu by remember { mutableStateOf(false) }
    var sourceSheet by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    // ---------- 片头片尾 ----------
    var introMs by remember { mutableLongStateOf(0L) }
    var outroMs by remember { mutableLongStateOf(0L) }
    var ioPanel by remember { mutableStateOf(false) }
    var showSkipIntro by remember { mutableStateOf(false) }
    var outroSkipped by remember { mutableStateOf(false) }
    LaunchedEffect(detail) {
        val d = detail
        if (d != null) {
            val io = app.store.settings.introOutro(d.showIdentifier)
            introMs = io.first; outroMs = io.second
        }
        outroSkipped = false
    }

    LaunchedEffect(Unit) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.takeIf { it > 0 } ?: 0
            isPlaying = player.isPlaying
            speed = player.playbackParameters.speed
            showSkipIntro = introMs > 0 && positionMs in 300 until introMs - 300
            // 片尾自动跳：seek 到结尾触发 ENDED → 自动连播
            if (outroMs > 0 && durationMs > 0 && !outroSkipped &&
                positionMs > durationMs - outroMs && positionMs < durationMs - 400
            ) {
                outroSkipped = true
                player.seekTo(durationMs)
            }
            delay(400)
        }
    }

    LaunchedEffect(controlsVisible, isPlaying, locked) {
        if (controlsVisible && (isPlaying || locked)) {
            delay(3_500)
            controlsVisible = false
        }
    }

    // ---------- 手势状态 ----------
    var seekActive by remember { mutableStateOf(false) }
    var seekDeltaMs by remember { mutableLongStateOf(0L) }
    var seekTargetMs by remember { mutableLongStateOf(0L) }
    var seekForward by remember { mutableStateOf(true) }
    var speedBoost by remember { mutableStateOf(false) }
    var brightActive by remember { mutableStateOf(false) }
    var brightValue by remember { mutableFloatStateOf(0.5f) }
    var volActive by remember { mutableStateOf(false) }
    var volValue by remember { mutableFloatStateOf(0f) }
    var flashHint by remember { mutableStateOf<String?>(null) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    fun seekBy(deltaMs: Long) {
        if (durationMs <= 0) return
        val target = (player.currentPosition + deltaMs).coerceIn(0, durationMs)
        player.seekTo(target)
        flashHint = (if (deltaMs > 0) "快进 " else "快退 ") + "${abs(deltaMs) / 1000}s"
    }
    LaunchedEffect(flashHint) {
        if (flashHint != null) { delay(700); flashHint = null }
    }
    fun togglePlay() = if (player.isPlaying) player.pause() else player.play()

    // ---------- 根布局 ----------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 视频画面：按比例等比缩放
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val W = constraints.maxWidth.toFloat()
            val H = constraints.maxHeight.toFloat()
            val target = when (aspectMode) {
                AspectMode.FIT, AspectMode.CROP -> videoAspect
                AspectMode.R16_9 -> 16f / 9f
                AspectMode.R4_3 -> 4f / 3f
                AspectMode.STRETCH -> W / H
            }
            val place: Modifier = when (aspectMode) {
                AspectMode.STRETCH -> Modifier.fillMaxSize()
                AspectMode.CROP -> {
                    if (W / H > target) {
                        Modifier.size(with(density) { W.toInt().toDp() }, with(density) { (W / target).toInt().toDp() })
                    } else {
                        Modifier.size(with(density) { (H * target).toInt().toDp() }, with(density) { H.toInt().toDp() })
                    }
                }
                else -> {
                    if (W / H > target) {
                        Modifier.size(with(density) { (H * target).toInt().toDp() }, with(density) { H.toInt().toDp() })
                    } else {
                        Modifier.size(with(density) { W.toInt().toDp() }, with(density) { (W / target).toInt().toDp() })
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .clipToBounds(),
                contentAlignment = Alignment.Center,
            ) {
                AndroidView(
                    factory = { ctx -> SurfaceView(ctx).also { player.setVideoSurfaceView(it) } },
                    modifier = place,
                )
            }
        }

        // 手势层
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
                                player.setPlaybackSpeed(2f); speedBoost = true
                            }
                        },
                        onPress = {
                            try { awaitRelease() } finally {
                                if (speedBoost) { player.setPlaybackSpeed(1f); speedBoost = false }
                            }
                        },
                    )
                }
                .pointerInput(locked, durationMs > 0) {
                    var mode = 0
                    var startX = 0f; var startY = 0f
                    var startPos = 0L; var startBright = 0f; var startVol = 0
                    detectDragGestures(
                        onDragStart = { offset ->
                            mode = 0; startX = offset.x; startY = offset.y
                            startPos = player.currentPosition
                            startBright = (context as? Activity)?.window?.attributes?.screenBrightness ?: 0.5f
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
                                    seekDeltaMs = ((change.position.x - startX) / size.width * 90_000).toLong()
                                    seekForward = seekDeltaMs >= 0
                                    seekTargetMs = (startPos + seekDeltaMs).coerceIn(0, durationMs)
                                }
                                2 -> {
                                    brightActive = true
                                    brightValue = (startBright + (startY - change.position.y) / size.height).coerceIn(0.01f, 1f)
                                    (context as? Activity)?.window?.attributes =
                                        (context as Activity).window.attributes.also { it.screenBrightness = brightValue }
                                }
                                3 -> {
                                    volActive = true
                                    val t = (startVol + ((startY - change.position.y) / size.height * maxVolume).roundToInt())
                                        .coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, t, 0)
                                    volValue = t.toFloat() / maxVolume
                                }
                            }
                        },
                        onDragEnd = {
                            if (mode == 1 && seekActive) player.seekTo(seekTargetMs)
                            seekActive = false; brightActive = false; volActive = false; mode = 0
                        },
                        onDragCancel = { seekActive = false; brightActive = false; volActive = false; mode = 0 },
                    )
                }
        )

        // 加载/错误卡
        if (detail == null && loadError == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = glass.accent)
            }
        }
        loadError?.let { msg ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.padding(30.dp), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("加载失败", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(msg, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("返回", color = glass.accent, modifier = Modifier.glassClick(onClick = onBack))
                    }
                }
            }
        }
        playbackError?.let { err ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassSurface(Modifier.padding(30.dp), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("播放出错", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(err, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Text("重试", color = glass.accent,
                                modifier = Modifier.glassClick {
                                    playbackError = null
                                    episode?.let {
                                        player.setMediaItem(MediaItem.fromUri(it.url))
                                        player.prepare()
                                    }
                                }.padding(6.dp))
                            Spacer(Modifier.width(14.dp))
                            Text("换源", color = glass.accent,
                                modifier = Modifier.glassClick { sourceSheet = true }.padding(6.dp))
                        }
                    }
                }
            }
        }

        // 中央手势气泡
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedVisibility(visible = seekActive, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (seekForward) "▶▶ 快进" else "◀◀ 快退", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Text("${abs(seekDeltaMs) / 1000}s", color = glass.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text("${format(seekTargetMs - seekDeltaMs)} → ${format(seekTargetMs)}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
                AnimatedVisibility(visible = speedBoost, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▶▶", color = glass.accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(6.dp))
                            Text("2x 倍速中", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                AnimatedVisibility(visible = flashHint != null, enter = fadeIn(), exit = fadeOut()) {
                    GestureBubble {
                        Text(flashHint.orEmpty(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // 亮度/音量指示
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visible = brightActive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterStart)) {
                VerticalMeter(icon = { Icon(DtIcons.BrightnessHigh, null, tint = Color.White, modifier = Modifier.size(16.dp)) }, value = brightValue)
            }
            AnimatedVisibility(visible = volActive, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterEnd)) {
                VerticalMeter(icon = { Icon(DtIcons.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp)) }, value = volValue)
            }
        }

        // 跳过片头 chip
        AnimatedVisibility(
            visible = showSkipIntro && !locked,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd),
        ) {
            Box(
                Modifier
                    .padding(top = 76.dp, end = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .glassClick { player.seekTo(introMs) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("跳过片头 ▸", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // 片头尾设置面板
        if (ioPanel) {
            GlassSurface(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 76.dp)
                    .width(250.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    val d = detail
                    Text(
                        "片头 ${introMs / 1000}s · 片尾 ${outroMs / 1000}s",
                        color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    IoAction("片头结束 = 当前位置") {
                        if (d != null) {
                            introMs = player.currentPosition
                            scope.launch { app.store.settings.setIntro(d.showIdentifier, introMs) }
                        }
                    }
                    IoAction("片尾开始 = 当前位置") {
                        if (d != null) {
                            outroMs = (durationMs - player.currentPosition).coerceAtLeast(0)
                            scope.launch { app.store.settings.setOutro(d.showIdentifier, outroMs) }
                        }
                    }
                    IoAction("清除本剧设置") {
                        if (d != null) {
                            scope.launch { app.store.settings.clearIntroOutro(d.showIdentifier) }
                            introMs = 0; outroMs = 0
                        }
                    }
                }
            }
        }

        // 锁定模式
        if (locked) {
            AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)) {
                Box(
                    Modifier
                        .padding(top = 24.dp, end = 16.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .glassClick { locked = false; controlsVisible = true },
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
                // 顶栏
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "返回",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp).padding(7.dp).glassClick(onClick = onBack),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            detail?.vod_name ?: "加载中",
                            color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        episode?.let {
                            Text(
                                "${it.name} · ${SourcesRepo.byId(app.store, sourceId)?.name.orEmpty()}",
                                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    TopPill("换源") { sourceSheet = true }
                    Spacer(Modifier.width(10.dp))
                    TopPill(aspectMode.label) { aspectMenu = !aspectMenu }
                    Spacer(Modifier.width(10.dp))
                    TopPill("片头尾") { ioPanel = !ioPanel }
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        DtIcons.Lock, "锁定", tint = Color.White,
                        modifier = Modifier.size(40.dp).padding(9.dp).glassClick {
                            locked = true; drawer = false; speedMenu = false; aspectMenu = false
                        },
                    )
                }

                Spacer(Modifier.weight(1f))

                // 底栏
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(format(positionMs), color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                        Slider(
                            value = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                            onValueChange = { frac -> if (durationMs > 0) player.seekTo((frac * durationMs).toLong()) },
                            colors = SliderDefaults.colors(
                                thumbColor = glass.accent,
                                activeTrackColor = glass.accent,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(26.dp),
                        )
                        Text(format(durationMs), color = Color.White.copy(alpha = 0.9f), fontSize = 11.sp)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPlaying) DtIcons.Pause else Icons.Filled.PlayArrow, "播放/暂停",
                            tint = Color.White, modifier = Modifier.size(34.dp).glassClick { togglePlay() },
                        )
                        Spacer(Modifier.width(18.dp))
                        Icon(DtIcons.Replay, "后退10秒", tint = Color.White,
                            modifier = Modifier.size(34.dp).padding(4.dp).glassClick { seekBy(-10_000) })
                        Spacer(Modifier.width(14.dp))
                        Icon(DtIcons.Forward, "快进10秒", tint = Color.White,
                            modifier = Modifier.size(34.dp).padding(4.dp).glassClick { seekBy(10_000) })
                        Spacer(Modifier.width(14.dp))
                        if (episodeIndex > 0) {
                            Icon(DtIcons.SkipPrevious, "上一集", tint = Color.White,
                                modifier = Modifier.size(34.dp).padding(4.dp).glassClick { onPlayEpisode(sourceId, vodId, episodeIndex - 1) })
                            Spacer(Modifier.width(14.dp))
                        }
                        if (episodeIndex + 1 < episodes.size) {
                            Icon(DtIcons.SkipNext, "下一集", tint = Color.White,
                                modifier = Modifier.size(34.dp).padding(4.dp).glassClick { onPlayEpisode(sourceId, vodId, episodeIndex + 1) })
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f))
                                .glassClick { speedMenu = !speedMenu }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(if (speed == 1f) "倍速" else "${speed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("选集", color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.glassClick { drawer = !drawer })
                    }
                }
            }

            // 倍速菜单
            SheetMenus(
                visible = speedMenu,
                title = "播放倍速",
                modifier = Modifier.align(Alignment.BottomEnd),
                content = {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { s ->
                        val isSel = abs(speed - s) < 0.01f
                        Text(
                            if (s == 1f) "1.0x 常速" else "${s}x",
                            color = if (isSel) glass.accent else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().glassClick {
                                player.setPlaybackSpeed(s); speedMenu = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                },
            )

            // 画面比例菜单
            SheetMenus(
                visible = aspectMenu,
                title = "画面比例",
                modifier = Modifier.align(Alignment.TopEnd),
                content = {
                    AspectMode.entries.forEach { m ->
                        val isSel = aspectMode == m
                        Text(
                            m.label,
                            color = if (isSel) glass.accent else Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().glassClick {
                                aspectMode = m; aspectMenu = false
                            }.padding(horizontal = 14.dp, vertical = 9.dp),
                        )
                    }
                },
            )

            // 选集抽屉（半屏网格）
            AnimatedVisibility(
                visible = drawer,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                GlassSurface(
                    Modifier.fillMaxWidth().fillMaxHeight(0.5f),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                ) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Text(
                            "选集（${episodes.size}）",
                            color = glass.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(6),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(episodes) { idx, ep ->
                                val isCur = idx == episodeIndex
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isCur) glass.accent.copy(alpha = 0.28f)
                                            else glass.textPrimary.copy(alpha = 0.06f)
                                        )
                                        .glassClick {
                                            drawer = false
                                            if (idx != episodeIndex) onPlayEpisode(sourceId, vodId, idx)
                                        }
                                        .padding(vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        ep.name.take(6),
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

        // 换源面板
        if (sourceSheet) {
            com.dream.tv.ui.common.SourceSwitchSheet(
                context = context,
                title = detail?.vod_name.orEmpty(),
                currentSourceId = sourceId,
                onDismiss = { sourceSheet = false },
                onPick = { s, id ->
                    sourceSheet = false
                    onSwitchSource(s, id, player.currentPosition.coerceAtLeast(0))
                },
            )
        }
    }
}

/** 片头尾面板动作行 */
@Composable
private fun IoAction(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.White, fontSize = 12.sp,
        modifier = Modifier
            .fillMaxWidth()
            .glassClick(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

/** 顶栏文字胶囊按钮 */
@Composable
private fun TopPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .glassClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 小型弹出菜单（倍速/比例） */
@Composable
private fun SheetMenus(
    visible: Boolean,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 4 },
        exit = fadeOut() + slideOutVertically { it / 4 },
        modifier = modifier,
    ) {
        GlassSurface(Modifier.padding(14.dp).width(190.dp), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.padding(vertical = 6.dp)) {
                Text(
                    title,
                    color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
                content()
            }
        }
    }
}

/** 中央手势气泡 */
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

/** 亮度/音量侧边竖条 */
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
