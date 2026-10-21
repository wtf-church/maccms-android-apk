package com.maccms.mobile.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.maccms.mobile.data.Episode
import com.maccms.mobile.data.PlaySource
import com.maccms.mobile.data.Vod
import com.maccms.mobile.data.VodCache
import com.maccms.mobile.data.VodParser
import com.maccms.mobile.data.VodRepository
import com.maccms.mobile.ui.components.EpisodePickerSheet
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import com.maccms.mobile.util.BaseUrlManager
import com.maccms.mobile.util.PlaybackStore
import com.maccms.mobile.util.WatchHistoryStore
import com.maccms.mobile.util.formatClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

private const val SEEK_STEP_MS = 10_000L
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * 手机端全屏播放页（横屏）。
 *
 * 触控交互：
 * - 单击：显隐控件；双击左/右侧：快退/快进 10 秒
 * - 横滑：拖动进度；左侧竖滑：亮度；右侧竖滑：音量
 * - 底部：播放/暂停、上一集/下一集、倍速、选集
 * - 一集播完自动连播；进度自动保存并同步观看历史
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    vodId: Int,
    episodeName: String,
    repository: VodRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val player = remember {
        val factory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(
            mapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36",
                "Referer" to BaseUrlManager.getBaseUrl(context) + "/"
            )
        )
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    var vod by remember { mutableStateOf(VodCache.get(vodId)) }
    var sources by remember { mutableStateOf<List<PlaySource>>(emptyList()) }
    var currentEpisode by remember { mutableStateOf<Episode?>(null) }
    var currentSourceIndex by remember { mutableIntStateOf(0) }
    var playUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var urlIndex by remember { mutableIntStateOf(0) }

    var loading by remember { mutableStateOf(true) }
    var switching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    var isPlaying by remember { mutableStateOf(false) }
    var isBuffering by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var loadingMedia by remember { mutableStateOf(false) }

    var controlsVisible by remember { mutableStateOf(true) }
    var showEpisodes by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableLongStateOf(0L) }
    var simpleHint by remember { mutableStateOf<String?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }

    fun showControlsTemporarily() {
        controlsVisible = true
    }

    fun saveProgress() {
        val ep = currentEpisode ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        PlaybackStore.save(context, vodId, ep.name, pos, player.duration)
        vod?.let { WatchHistoryStore.record(context, it, ep.name, pos, player.duration) }
    }

    suspend fun expandUrls(raw: List<String>): List<String> {
        val cleaned = raw.filter { it.isNotBlank() }.distinct()
        // m3u8 优先，降低防盗链/直链失效概率
        val direct = cleaned.filter { VodParser.isVideoUrl(it) }
            .sortedBy { if (it.contains(".m3u8") || it.contains(".m3u")) 0 else 1 }
        if (direct.isNotEmpty()) return direct
        return withContext(Dispatchers.IO) {
            cleaned.map { resolveHtmlPlayUrl(it) ?: it }
        }
    }

    suspend fun resolveEpisodeUrls(base: Vod, name: String, source: PlaySource?): List<String> {
        val raw = mutableListOf<String>()
        source?.episodes?.filter { it.name == name }?.forEach { raw += it.url }
        VodParser.urlsByName(base.vod_play_from, base.vod_play_url, name)
            .forEach { if (it !in raw) raw += it }
        return expandUrls(raw)
    }

    fun loadUrl(index: Int, startPosMs: Long) {
        val url = playUrls.getOrNull(index) ?: return
        loadingMedia = true
        player.setMediaItem(MediaItem.fromUri(url), startPosMs.coerceAtLeast(0L))
        player.prepare()
        player.playWhenReady = true
    }

    fun playEpisode(source: PlaySource, ep: Episode) {
        val base = vod ?: return
        saveProgress()
        showEpisodes = false
        switching = true
        error = null
        showControlsTemporarily()
        scope.launch {
            val resume = PlaybackStore.position(context, vodId, ep.name)
            val urls = resolveEpisodeUrls(base, ep.name, source)
                .ifEmpty { listOfNotNull(ep.url.takeIf { it.isNotBlank() }) }
            if (urls.isEmpty()) {
                switching = false
                error = "该集暂无可用播放地址"
                return@launch
            }
            currentEpisode = ep
            currentSourceIndex = sources.indexOfFirst { it === source }.let { if (it >= 0) it else 0 }
            playUrls = urls
            urlIndex = 0
            switching = false
            loadUrl(0, resume)
            WatchHistoryStore.record(context, base, ep.name, resume, 0L)
        }
    }

    fun playRelative(delta: Int) {
        val src = sources.getOrNull(currentSourceIndex) ?: return
        val idx = src.episodes.indexOfFirst { it.name == currentEpisode?.name }
        if (idx < 0) return
        val target = src.episodes.getOrNull(idx + delta) ?: return
        playEpisode(src, target)
    }

    fun seekBy(deltaMs: Long) {
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L).let {
            if (player.duration > 0) it.coerceAtMost(player.duration) else it
        }
        player.seekTo(target)
        positionMs = target
        showControlsTemporarily()
        simpleHint = if (deltaMs > 0) "快进 10 秒" else "快退 10 秒"
    }

    fun togglePlay() {
        if (player.isPlaying) {
            player.pause()
            saveProgress()
            controlsVisible = true
        } else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
            loadingMedia = false
            controlsVisible = true
        }
    }

    // 初始加载：详情 → 线路/分集 → 候选地址（点选集 > 上次观看 > 第一集）
    LaunchedEffect(vodId, episodeName, retryToken) {
        loading = true
        error = null
        val cached = VodCache.get(vodId)
        var base: Vod? = cached
        if (base == null || base.vod_play_url.isBlank()) {
            repository.getDetail(vodId).onSuccess {
                base = it
                VodCache.put(it)
                vod = it
            }.onFailure {
                error = "获取影片详情失败：${it.message ?: "网络异常"}"
                loading = false
                return@LaunchedEffect
            }
            // 有缓存时也后台刷新，拿最新的播放地址
        } else {
            vod = base
            repository.getDetail(vodId).onSuccess {
                if (it.vod_play_url.isNotBlank()) {
                    base = it
                    vod = it
                    VodCache.put(it)
                }
            }
        }
        val b = base ?: run {
            error = "影片不存在"
            loading = false
            return@LaunchedEffect
        }
        val srcs = VodParser.parsePlaySources(b.vod_play_from, b.vod_play_url)
        sources = srcs
        if (srcs.isEmpty()) {
            error = "该影片暂无播放源"
            loading = false
            return@LaunchedEffect
        }
        val targetName = episodeName.ifBlank {
            PlaybackStore.lastEpisode(context, vodId) ?: srcs.firstOrNull()?.firstEpisode?.name.orEmpty()
        }
        val srcIdx = srcs.indexOfFirst { s -> s.episodes.any { it.name == targetName } }
            .let { if (it >= 0) it else 0 }
        val ep = srcs.getOrNull(srcIdx)?.episodes?.firstOrNull { it.name == targetName }
            ?: srcs.firstOrNull()?.firstEpisode
        if (ep == null) {
            error = "未找到可播放的分集"
            loading = false
            return@LaunchedEffect
        }
        currentEpisode = ep
        currentSourceIndex = srcIdx
        val resume = PlaybackStore.position(context, vodId, ep.name)
        val urls = resolveEpisodeUrls(b, ep.name, srcs.getOrNull(srcIdx))
            .ifEmpty { listOfNotNull(ep.url.takeIf { it.isNotBlank() }) }
        if (urls.isEmpty()) {
            error = "没有可用的播放地址"
            loading = false
            return@LaunchedEffect
        }
        playUrls = urls
        urlIndex = 0
        loading = false
        loadUrl(0, resume)
        WatchHistoryStore.record(context, b, ep.name, resume, 0L)
    }

    // 播放器回调：播放状态、错误兜底、自动连播
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (playing) loadingMedia = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) loadingMedia = false
                if (state == Player.STATE_ENDED && !loadingMedia) {
                    val src = sources.getOrNull(currentSourceIndex)
                    val idx = src?.episodes?.indexOfFirst { it.name == currentEpisode?.name } ?: -1
                    val next = if (idx >= 0) src?.episodes?.getOrNull(idx + 1) else null
                    if (next != null && src != null) {
                        playEpisode(src, next)
                    } else {
                        controlsVisible = true
                    }
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                loadingMedia = false
                if (urlIndex < playUrls.lastIndex) {
                    val retryPos = player.currentPosition.coerceAtLeast(0L)
                    urlIndex += 1
                    loadUrl(urlIndex, retryPos)
                } else {
                    error = "播放失败：${playbackError.message ?: "未知错误"}"
                    controlsVisible = true
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 进度轮询 + 每 5 秒写入本机进度/观看历史
    LaunchedEffect(Unit) {
        var ticks = 0
        while (isActive) {
            delay(500)
            if (!scrubbing) positionMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = if (player.duration > 0) player.duration else 0L
            if (player.isPlaying) {
                ticks++
                if (ticks >= 10) {
                    ticks = 0
                    saveProgress()
                }
            }
        }
    }

    // 控件自动隐藏
    LaunchedEffect(controlsVisible, isPlaying, showEpisodes) {
        if (controlsVisible && isPlaying && !showEpisodes) {
            delay(3_500)
            controlsVisible = false
        }
    }

    LaunchedEffect(simpleHint) {
        if (simpleHint != null) {
            delay(900)
            simpleHint = null
        }
    }

    // 横屏 + 沉浸式 + 常亮
    DisposableEffect(Unit) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        val originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = originalOrientation
            saveProgress()
            player.release()
        }
    }

    // 退到后台时暂停并保存进度
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                if (player.isPlaying) player.pause()
                saveProgress()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val title = buildString {
        append(vod?.vod_name.orEmpty())
        currentEpisode?.name?.takeIf { it.isNotBlank() }?.let {
            if (isNotEmpty()) append(" · ")
            append(it)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        // 手势层（位于控件之下）
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (controlsVisible && isPlaying) controlsVisible = false
                            else controlsVisible = true
                        },
                        onDoubleTap = { offset: Offset ->
                            seekBy(if (offset.x < size.width / 2f) -SEEK_STEP_MS else SEEK_STEP_MS)
                        }
                    )
                }
                .pointerInput(Unit) {
                    var startPos = 0L
                    var accumulated = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            startPos = player.currentPosition.coerceAtLeast(0L)
                            accumulated = 0f
                            scrubbing = true
                            scrubPosition = startPos
                            seekPreview = startPos
                            controlsVisible = true
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            val deltaMs = (accumulated / size.width * 90_000f).toLong()
                            val target = (startPos + deltaMs).coerceAtLeast(0L).let {
                                if (player.duration > 0) it.coerceAtMost(player.duration) else it
                            }
                            scrubPosition = target
                            seekPreview = target
                        },
                        onDragEnd = {
                            player.seekTo(scrubPosition)
                            positionMs = scrubPosition
                            scrubbing = false
                            seekPreview = null
                        },
                        onDragCancel = {
                            scrubbing = false
                            seekPreview = null
                        }
                    )
                }
                .pointerInput(Unit) {
                    var startVolume = 0
                    var startBrightness = 0.5f
                    var accumulated = 0f
                    var isVolume = true
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                            startBrightness = activity.window.attributes.screenBrightness
                                .takeIf { it >= 0f } ?: 0.5f
                            accumulated = 0f
                            isVolume = offset.x >= size.width / 2f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulated += dragAmount
                            val delta = -accumulated / size.height
                            if (isVolume) {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    .coerceAtLeast(1)
                                val target = (startVolume + delta * max)
                                    .roundToInt().coerceIn(0, max)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                simpleHint = "音量 ${(target * 100 / max)}%"
                            } else {
                                val target = (startBrightness + delta).coerceIn(0.02f, 1f)
                                val attrs = activity.window.attributes
                                attrs.screenBrightness = target
                                activity.window.attributes = attrs
                                simpleHint = "亮度 ${(target * 100).roundToInt()}%"
                            }
                        },
                        onDragEnd = {},
                        onDragCancel = {}
                    )
                }
        )

        // 缓冲 / 加载 / 错误
        when {
            loading || switching -> CenterStatus(if (loading) "正在获取播放地址…" else "正在切换集数…")
            error != null -> CenterError(
                message = error ?: "",
                onRetry = {
                    error = null
                    retryToken++
                },
                onBack = { saveProgress(); onBack() }
            )
            !controlsVisible && isBuffering -> CenterStatus("正在缓冲…")
        }

        // 手势提示
        simpleHint?.let { hint ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(hint, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        seekPreview?.let { preview ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${formatClock(preview)} / ${formatClock(durationMs)}",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (preview >= positionMs) "快进" else "快退",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 播放控件
        AnimatedVisibility(
            visible = controlsVisible || !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                // 顶部渐变 + 标题
                Row(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC000000), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { saveProgress(); onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            tint = TextPrimary
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (currentEpisode != null) {
                            Text(
                                "${sources.getOrNull(currentSourceIndex)?.from.orEmpty()} · ${currentEpisode?.name.orEmpty()}",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 底部渐变 + 进度 + 按钮
                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xE6000000))
                            )
                        )
                        .navigationBarsPadding()
                        .padding(start = 18.dp, end = 18.dp, top = 34.dp, bottom = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatClock(if (scrubbing) scrubPosition else positionMs),
                            color = TextPrimary,
                            fontSize = 11.sp
                        )
                        Slider(
                            value = (if (scrubbing) scrubPosition else positionMs).toFloat(),
                            onValueChange = {
                                scrubbing = true
                                scrubPosition = it.toLong()
                            },
                            onValueChangeFinished = {
                                player.seekTo(scrubPosition)
                                positionMs = scrubPosition
                                scrubbing = false
                                showControlsTemporarily()
                            },
                            valueRange = 0f..(durationMs.coerceAtLeast(1L)).toFloat(),
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = BrandRed,
                                activeTrackColor = BrandRed,
                                inactiveTrackColor = Color(0x66FFFFFF)
                            )
                        )
                        Text(formatClock(durationMs), color = TextPrimary, fontSize = 11.sp)
                    }
                    Box(Modifier.fillMaxWidth().height(52.dp)) {
                        Row(
                            Modifier.align(Alignment.CenterStart),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val next = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.size]
                                        speed = next
                                        player.setPlaybackSpeed(next)
                                        simpleHint = "倍速 ${next}x"
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    if (speed == speed.toInt().toFloat()) "${speed.toInt()}.0x" else "${speed}x",
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Row(
                            Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { playRelative(-1) },
                                enabled = canPlayRelative(sources, currentSourceIndex, currentEpisode, -1)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipPrevious,
                                    contentDescription = "上一集",
                                    tint = TextPrimary.copy(
                                        alpha = if (canPlayRelative(sources, currentSourceIndex, currentEpisode, -1)) 1f else 0.35f
                                    )
                                )
                            }
                            IconButton(
                                onClick = { togglePlay() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0x33FFFFFF))
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "暂停" else "播放",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            IconButton(
                                onClick = { playRelative(1) },
                                enabled = canPlayRelative(sources, currentSourceIndex, currentEpisode, 1)
                            ) {
                                Icon(
                                    Icons.Rounded.SkipNext,
                                    contentDescription = "下一集",
                                    tint = TextPrimary.copy(
                                        alpha = if (canPlayRelative(sources, currentSourceIndex, currentEpisode, 1)) 1f else 0.35f
                                    )
                                )
                            }
                        }
                        Row(
                            Modifier.align(Alignment.CenterEnd),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        if (sources.isNotEmpty()) {
                                            showEpisodes = true
                                            controlsVisible = false
                                        }
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                                    contentDescription = "选集",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("选集", color = TextPrimary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        if (showEpisodes && sources.isNotEmpty()) {
            EpisodePickerSheet(
                sources = sources,
                initialSourceIndex = currentSourceIndex,
                currentEpisode = currentEpisode?.name,
                onDismiss = {
                    showEpisodes = false
                    controlsVisible = true
                },
                onEpisodeSelected = { source, ep -> playEpisode(source, ep) }
            )
        }
    }
}

private fun canPlayRelative(
    sources: List<PlaySource>,
    sourceIndex: Int,
    episode: Episode?,
    delta: Int
): Boolean {
    val src = sources.getOrNull(sourceIndex) ?: return false
    val idx = src.episodes.indexOfFirst { it.name == episode?.name }
    return idx >= 0 && src.episodes.getOrNull(idx + delta) != null
}

@Composable
private fun FullScreenCenter(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CenterStatus(text: String) {
    FullScreenCenter {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = BrandRed, strokeWidth = 3.dp)
            Spacer(Modifier.height(12.dp))
            Text(text, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CenterError(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    FullScreenCenter {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Text("无法播放", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = TextSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = BrandRed)
                ) { Text("重试", color = Color.White) }
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A31))
                ) { Text("返回", color = TextPrimary) }
            }
        }
    }
}

private fun Context.findActivity(): Activity {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    throw IllegalStateException("需要 Activity 上下文")
}

/** HTML 分享页解析：抓取页面并提取真实 m3u8 地址 */
private suspend fun resolveHtmlPlayUrl(pageUrl: String): String? = withContext(Dispatchers.IO) {
    runCatching {
        val conn = URL(pageUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        )
        val html = conn.inputStream.bufferedReader().use { it.readText() }
        VodParser.m3u8FromHtml(html, pageUrl)
    }.getOrNull()
}
