package com.maccms.mobile.ui.detail

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.maccms.mobile.data.Categories
import com.maccms.mobile.data.Episode
import com.maccms.mobile.data.PlaySource
import com.maccms.mobile.data.Vod
import com.maccms.mobile.data.VodCache
import com.maccms.mobile.data.VodParser
import com.maccms.mobile.data.VodRepository
import com.maccms.mobile.ui.components.EpisodeGrid
import com.maccms.mobile.ui.components.EpisodePickerSheet
import com.maccms.mobile.ui.components.ErrorState
import com.maccms.mobile.ui.components.LoadingState
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.AppSurface
import com.maccms.mobile.ui.theme.AppSurfaceHigh
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.ScoreGold
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import com.maccms.mobile.util.FavoriteStore
import com.maccms.mobile.util.PlaybackStore
import com.maccms.mobile.util.RichTextParser
import com.maccms.mobile.util.formatClock
import com.maccms.mobile.util.shortDate
import kotlinx.coroutines.launch

/**
 * 详情页（手机竖屏）：大图头部 + 播放/收藏 + 简介 + 线路与选集。
 * 「播放」不指定集数，由播放页按本机进度续播。
 */
@Composable
fun DetailScreen(
    vodId: Int,
    repository: VodRepository,
    onBack: () -> Unit,
    onPlay: (Vod) -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    var vod by remember { mutableStateOf(VodCache.get(vodId)) }
    var loading by remember { mutableStateOf(vod == null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableIntStateOf(0) }

    LaunchedEffect(vodId, retryToken) {
        if (vod == null) loading = true
        repository.getDetail(vodId).onSuccess {
            vod = it
            VodCache.put(it)
            loading = false
            error = null
        }.onFailure {
            if (vod == null) {
                error = it.message
                loading = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(AppBlack)) {
        when {
            loading && vod == null -> LoadingState()
            error != null && vod == null -> ErrorState(
                message = error ?: "",
                onRetry = {
                    error = null
                    loading = true
                    retryToken++
                }
            )
            vod != null -> DetailContent(
                vod = vod!!,
                onBack = onBack,
                onPlay = onPlay,
                onPlayEpisode = onPlayEpisode
            )
        }
    }
}

@Composable
private fun DetailContent(
    vod: Vod,
    onBack: () -> Unit,
    onPlay: (Vod) -> Unit,
    onPlayEpisode: (Vod, Episode) -> Unit
) {
    val context = LocalContext.current
    val sources = remember(vod.vod_play_from, vod.vod_play_url) {
        VodParser.parsePlaySources(vod.vod_play_from, vod.vod_play_url)
    }
    var lastEpName by remember(vod.vod_id) {
        mutableStateOf(PlaybackStore.lastEpisode(context, vod.vod_id))
    }
    var lastPosMs by remember(vod.vod_id) {
        mutableLongStateOf(
            lastEpName?.let { PlaybackStore.position(context, vod.vod_id, it) } ?: 0L
        )
    }
    var favorite by remember(vod.vod_id) { mutableStateOf(FavoriteStore.isFavorite(context, vod.vod_id)) }

    // 从播放页返回时刷新上次观看进度与收藏状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, vod.vod_id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                lastEpName = PlaybackStore.lastEpisode(context, vod.vod_id)
                lastPosMs = lastEpName?.let {
                    PlaybackStore.position(context, vod.vod_id, it)
                } ?: 0L
                favorite = FavoriteStore.isFavorite(context, vod.vod_id)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var selectedSource by remember(sources, lastEpName) {
        mutableIntStateOf(
            sources.indexOfFirst { src -> src.episodes.any { it.name == lastEpName } }
                .coerceAtLeast(0)
        )
    }
    var expanded by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    val currentSource = sources.getOrNull(selectedSource) ?: sources.firstOrNull()
    val episodes = currentSource?.episodes ?: emptyList()
    val previewEpisodes = episodes.take(12)
    val synopsis = remember(vod.vod_content) { RichTextParser.toPlainText(vod.vod_content) }
    val playLabel = when {
        lastEpName == null -> "播放"
        lastPosMs > 0 -> "继续观看 · ${lastEpName} ${formatClock(lastPosMs)}"
        else -> "播放 · $lastEpName"
    }

    fun toggleFavorite() {
        favorite = FavoriteStore.toggle(context, vod)
        Toast.makeText(
            context,
            if (favorite) "已加入收藏" else "已取消收藏",
            Toast.LENGTH_SHORT
        ).show()
    }

    LazyColumn(Modifier.fillMaxSize()) {
        // 头部大图
        item {
            Box(Modifier.fillMaxWidth().height(410.dp)) {
                AsyncImage(
                    model = vod.vod_pic_slide.ifBlank { vod.vod_pic },
                    contentDescription = vod.vod_name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color(0x66000000),
                            0.45f to Color(0x22000000),
                            1f to AppBlack
                        )
                    )
                )
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(10.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x99000000))
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 18.dp, end = 18.dp, bottom = 10.dp)
                ) {
                    Text(
                        vod.vod_name,
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 32.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(7.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (vod.displayScore.isNotBlank()) {
                            Text(
                                vod.displayScore,
                                color = ScoreGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            vod.metaLine.ifBlank { Categories.nameOf(vod.type_id) },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (vod.vod_remarks.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Text(
                            vod.vod_remarks,
                            color = TextPrimary,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .background(BrandRed.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // 操作按钮
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onPlay(vod) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        playLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(10.dp))
                IconButton(
                    onClick = { toggleFavorite() },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (favorite) BrandRed.copy(alpha = 0.22f) else AppSurfaceHigh)
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = if (favorite) "取消收藏" else "收藏",
                        tint = if (favorite) BrandRed else TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 上次观看进度
        if (lastEpName != null) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppSurface)
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(BrandRed))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        buildString {
                            append("上次看到 ")
                            append(lastEpName)
                            if (lastPosMs > 0) append(" · ${formatClock(lastPosMs)}")
                        },
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "继续播放",
                        color = BrandRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onPlay(vod) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // 简介与元信息
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("简介", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (synopsis.length > 80) {
                        Text(
                            if (expanded) "收起" else "展开",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    synopsis.ifBlank { "暂无简介" },
                    color = if (synopsis.isBlank()) TextSecondary else TextPrimary.copy(alpha = 0.82f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppSurface)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    MetaRow("分类", Categories.nameOf(vod.type_id))
                    if (vod.vod_director.isNotBlank()) MetaRow("导演", vod.vod_director)
                    if (vod.vod_actor.isNotBlank()) MetaRow("主演", vod.vod_actor)
                    if (vod.vod_area.isNotBlank()) MetaRow("地区", vod.vod_area)
                    if (vod.vod_lang.isNotBlank()) MetaRow("语言", vod.vod_lang)
                    if (vod.vod_year.isNotBlank()) MetaRow("年份", vod.vod_year)
                    if (vod.vod_time.isNotBlank()) MetaRow("更新", vod.vod_time.substringBefore(" "))
                }
            }
        }

        // 选集
        if (sources.isNotEmpty()) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选集", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "共 ${episodes.size} 集",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (episodes.size > previewEpisodes.size) {
                        Text(
                            "全部选集",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { showPicker = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            if (sources.size > 1) {
                item {
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sources.size) { index ->
                            SourceChip(
                                name = sources[index].from,
                                selected = index == selectedSource,
                                onClick = { selectedSource = index }
                            )
                        }
                    }
                }
            }
            item {
                EpisodeGrid(
                    episodes = previewEpisodes,
                    onEpisodeClick = { ep -> onPlayEpisode(vod, ep) },
                    currentEpisode = lastEpName,
                    lastEpisode = lastEpName,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (episodes.size > previewEpisodes.size) {
                item {
                    OutlinedButton(
                        onClick = { showPicker = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(9.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF34343C))
                    ) {
                        Text("查看全部 ${episodes.size} 集", color = TextPrimary, fontSize = 13.sp)
                    }
                }
            }
        } else {
            item {
                Text(
                    "暂无播放源",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        item { Spacer(Modifier.height(28.dp)) }
    }

    if (showPicker) {
        EpisodePickerSheet(
            sources = sources,
            initialSourceIndex = selectedSource,
            currentEpisode = lastEpName,
            onDismiss = { showPicker = false },
            onEpisodeSelected = { _, ep ->
                showPicker = false
                onPlayEpisode(vod, ep)
            }
        )
    }
}

@Composable
private fun SourceChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) BrandRed else AppSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            name,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.width(44.dp))
        Text(
            value,
            color = TextPrimary.copy(alpha = 0.88f),
            fontSize = 12.sp,
            lineHeight = 17.sp,
            maxLines = if (label == "主演") 3 else 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
