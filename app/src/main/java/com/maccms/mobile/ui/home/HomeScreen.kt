package com.maccms.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.maccms.mobile.data.Categories
import com.maccms.mobile.data.Vod
import com.maccms.mobile.data.VodRepository
import com.maccms.mobile.ui.components.ContinueWatchingCard
import com.maccms.mobile.ui.components.ErrorState
import com.maccms.mobile.ui.components.LoadingState
import com.maccms.mobile.ui.components.PosterCard
import com.maccms.mobile.ui.components.SectionHeader
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.ScoreGold
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import com.maccms.mobile.util.RichTextParser
import com.maccms.mobile.util.WatchHistoryEntry
import com.maccms.mobile.util.WatchHistoryStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/** 首页状态提升到 App 层：从详情/播放返回时保留内容与滚动位置，避免重复请求 */
class HomeState {
    var allVods by mutableStateOf<List<Vod>>(emptyList())
    var rows by mutableStateOf<List<Pair<Pair<Int, String>, List<Vod>>>>(emptyList())
    var loading by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loadedOnce by mutableStateOf(false)
    var listState: LazyListState = LazyListState()
}

/**
 * 首页：Hero 推荐 + 继续观看 + 精选分类横排。
 * 手机端改为纵向滚动、底部 Tab 导航，所有入口均可用触控操作。
 */
@Composable
fun HomeScreen(
    repository: VodRepository,
    state: HomeState,
    onVodClick: (Vod) -> Unit,
    onPlayVod: (Vod) -> Unit,
    onCategoryMore: (Int) -> Unit,
    onOpenSearch: () -> Unit
) {
    val context = LocalContext.current
    var history by remember { mutableStateOf(WatchHistoryStore.list(context).take(12)) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            if (state.loading || state.refreshing) return@launch
            if (state.loadedOnce) state.refreshing = true else state.loading = true
            state.error = null
            try {
            repository.getHomeList().onSuccess { list ->
                state.allVods = list
                // 各精选分类并发拉取，单行失败不影响其它行
                supervisorScope {
                    val loaded = Categories.HOME_IDS.map { id ->
                        async {
                            id to repository.getListByType(id).getOrElse { emptyList() }.take(12)
                        }
                    }.awaitAll()
                    state.rows = loaded
                        .map { (id, list) -> (id to Categories.nameOf(id)) to list }
                        .filter { it.second.isNotEmpty() }
                }
                // 分类行全部就绪后才标记完成，避免中途切页留下半截首页
                state.loadedOnce = true
            }.onFailure {
                state.error = it.message
            }
            } finally {
                // 切页导致协程取消时也要复位，避免下次进入时被 loading 卡住
                state.loading = false
                state.refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        history = WatchHistoryStore.list(context).take(12)
        if (!state.loadedOnce) load()
    }

    // 从详情/播放返回时刷新「继续观看」
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                history = WatchHistoryStore.list(context).take(12)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().background(AppBlack).statusBarsPadding()) {
        HomeTopBar(
            refreshing = state.refreshing,
            onSearch = onOpenSearch,
            onRefresh = { load() }
        )
        when {
            (state.loading || (!state.loadedOnce && state.error == null)) &&
                state.allVods.isEmpty() -> LoadingState(Modifier.weight(1f))
            state.error != null && state.allVods.isEmpty() -> ErrorState(
                message = state.error ?: "",
                modifier = Modifier.weight(1f),
                onRetry = { load() }
            )
            else -> LazyColumn(
                state = state.listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                val hero = state.allVods.firstOrNull()
                if (hero != null) {
                    item(key = "hero") {
                        HeroBanner(
                            vod = hero,
                            continueEntry = history.firstOrNull { it.vodId == hero.vod_id },
                            onPlay = { onPlayVod(hero) },
                            onDetail = { onVodClick(hero) }
                        )
                    }
                }
                if (history.isNotEmpty()) {
                    item(key = "continue_header") {
                        SectionHeader("继续观看", modifier = Modifier.padding(top = 10.dp))
                    }
                    item(key = "continue_row") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(history, key = { it.vodId }) { entry ->
                                ContinueWatchingCard(entry = entry, onClick = { onPlayVod(entry.toVod()) })
                            }
                        }
                    }
                }
                state.rows.forEach { (info, list) ->
                    val (id, name) = info
                    item(key = "row_header_$id") {
                        SectionHeader(
                            title = name,
                            action = "更多",
                            onAction = { onCategoryMore(id) },
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    item(key = "row_$id") {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(list, key = { "${id}_${it.vod_id}" }) { vod ->
                                PosterCard(
                                    vod = vod,
                                    onClick = { onVodClick(vod) },
                                    width = 108.dp,
                                    titleLines = 1
                                )
                            }
                        }
                    }
                }
                item(key = "footer") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "已经到底啦 · 数据来自 maccms",
                            color = TextSecondary.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(
    refreshing: Boolean,
    onSearch: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("MAC", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("CMS", color = BrandRed, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(6.dp))
            Text(
                "影视",
                color = TextSecondary,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(Icons.Outlined.Search, contentDescription = "搜索", tint = TextPrimary)
        }
        IconButton(onClick = onRefresh, enabled = !refreshing) {
            if (refreshing) {
                CircularProgressIndicator(
                    color = BrandRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = TextPrimary)
            }
        }
    }
}

@Composable
private fun HeroBanner(
    vod: Vod,
    continueEntry: WatchHistoryEntry?,
    onPlay: () -> Unit,
    onDetail: () -> Unit
) {
    Box(Modifier.fillMaxWidth().height(432.dp)) {
        AsyncImage(
            model = vod.vod_pic_slide.ifBlank { vod.vod_pic },
            contentDescription = vod.vod_name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0x99000000),
                        0.22f to Color(0x11000000),
                        0.55f to Color(0x66000000),
                        1f to AppBlack
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 18.dp, end = 18.dp, bottom = 18.dp)
        ) {
            if (vod.vod_remarks.isNotBlank()) {
                Text(
                    vod.vod_remarks,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .background(BrandRed.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
                Spacer(Modifier.height(8.dp))
            }
            Text(
                vod.vod_name,
                color = TextPrimary,
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 32.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vod.displayScore.isNotBlank()) {
                    Text(vod.displayScore, color = ScoreGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    listOf(vod.vod_year, vod.vod_area, vod.type_name ?: "")
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val synopsis = RichTextParser.toPlainText(vod.vod_content)
            if (synopsis.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    synopsis,
                    color = TextPrimary.copy(alpha = 0.78f),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPlay,
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    )
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (continueEntry != null) {
                            if (continueEntry.episode.isNotBlank()) "继续 · ${continueEntry.episode}" else "继续观看"
                        } else "播放",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = onDetail,
                    modifier = Modifier.height(46.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x66FFFFFF)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0x66000000),
                        contentColor = TextPrimary
                    )
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("详情", fontSize = 14.sp)
                }
            }
        }
    }
}
