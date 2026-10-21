package com.maccms.mobile.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.maccms.mobile.data.Vod
import com.maccms.mobile.ui.components.EmptyState
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.AppSurface
import com.maccms.mobile.ui.theme.AppSurfaceHigh
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.ProgressTrack
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import com.maccms.mobile.util.FavoriteEntry
import com.maccms.mobile.util.FavoriteStore
import com.maccms.mobile.util.WatchHistoryEntry
import com.maccms.mobile.util.WatchHistoryStore
import com.maccms.mobile.util.formatClock
import com.maccms.mobile.util.relativeTime
import com.maccms.mobile.util.shortDate

/**
 * 我的：收藏 / 观看历史双 Tab + 设置入口。
 * 收藏行点击进详情；历史行点击进详情，右侧播放按钮直接续播。
 */
@Composable
fun LibraryScreen(
    onOpenDetail: (Vod) -> Unit,
    onPlayVod: (Vod) -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(FavoriteStore.list(context)) }
    var history by remember { mutableStateOf(WatchHistoryStore.list(context)) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    fun refresh() {
        favorites = FavoriteStore.list(context)
        history = WatchHistoryStore.list(context)
    }

    // 从详情/播放返回、切换 Tab 时刷新列表
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(tab) { refresh() }

    Column(Modifier.fillMaxSize().background(AppBlack).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("我的", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = TextPrimary)
            }
        }

        // 账户卡片
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AppSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(BrandRed),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Maccms 影视", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("发现好剧 · 记住每一次观看", color = TextSecondary, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${favorites.size}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("收藏", color = TextSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.width(18.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${history.size}", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("历史", color = TextSecondary, fontSize = 11.sp)
            }
        }

        // 双 Tab
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSurface)
                .padding(3.dp)
        ) {
            LibraryTab("我的收藏", tab == 0, Modifier.weight(1f)) { tab = 0 }
            LibraryTab("观看历史", tab == 1, Modifier.weight(1f)) { tab = 1 }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) {
                FavoritesList(
                    favorites = favorites,
                    onOpen = { onOpenDetail(it.toVod()) },
                    onRemove = { entry ->
                        FavoriteStore.remove(context, entry.vodId)
                        favorites = FavoriteStore.list(context)
                    }
                )
            } else {
                HistoryList(
                    history = history,
                    onOpen = { onOpenDetail(it.toVod()) },
                    onPlay = { onPlayVod(it.toVod()) },
                    onRemove = { entry ->
                        WatchHistoryStore.remove(context, entry.vodId)
                        history = WatchHistoryStore.list(context)
                    },
                    onClearAll = { showClearHistoryDialog = true }
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF222228)))
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenSettings)
                .padding(horizontal = 20.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("设置", color = TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("服务器地址 · 缓存", color = TextSecondary, fontSize = 12.sp)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            containerColor = AppSurfaceHigh,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("清空观看历史？") },
            text = { Text("所有观看记录与进度都会被删除，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    WatchHistoryStore.clear(context)
                    history = emptyList()
                    showClearHistoryDialog = false
                }) { Text("清空", color = BrandRed) }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("取消", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun LibraryTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AppSurfaceHigh else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun FavoritesList(
    favorites: List<FavoriteEntry>,
    onOpen: (FavoriteEntry) -> Unit,
    onRemove: (FavoriteEntry) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyState(
            title = "还没有收藏",
            subtitle = "进入影片详情页，点击「收藏」即可在这里找到"
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(favorites, key = { it.vodId }) { entry ->
            LibraryRow(
                title = entry.name.ifBlank { "影片 ${entry.vodId}" },
                subtitle = buildString {
                    listOf(entry.year, entry.remarks).filter { it.isNotBlank() }
                        .forEach { append(it).append(" · ") }
                    append("收藏于 ${shortDate(entry.addedAt)}")
                },
                pic = entry.pic,
                onClick = { onOpen(entry) },
                trailing = {
                    IconButton(onClick = { onRemove(entry) }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "取消收藏",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun HistoryList(
    history: List<WatchHistoryEntry>,
    onOpen: (WatchHistoryEntry) -> Unit,
    onPlay: (WatchHistoryEntry) -> Unit,
    onRemove: (WatchHistoryEntry) -> Unit,
    onClearAll: () -> Unit
) {
    if (history.isEmpty()) {
        EmptyState(
            title = "还没有观看记录",
            subtitle = "去首页挑一部，播放后会自动记录进度"
        )
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("共 ${history.size} 部", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(
                "清空历史",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 2.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(history, key = { it.vodId }) { entry ->
                LibraryRow(
                    title = entry.name.ifBlank { "影片 ${entry.vodId}" },
                    subtitle = buildString {
                        if (entry.episode.isNotBlank()) {
                            append("看到 ${entry.episode}")
                            if (entry.positionMs > 0) {
                                append(" · ").append(formatClock(entry.positionMs))
                            }
                            append(" · ")
                        }
                        append(relativeTime(entry.updatedAt))
                    },
                    pic = entry.pic,
                    progress = entry.progress,
                    onClick = { onOpen(entry) },
                    trailing = {
                        IconButton(onClick = { onPlay(entry) }) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = "继续播放",
                                tint = BrandRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { onRemove(entry) }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除记录",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    pic: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
    progress: Float = 0f
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppSurface)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(54.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AppSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = pic,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (pic.isBlank()) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (progress > 0f) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(ProgressTrack)
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(BrandRed)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
    }
}
