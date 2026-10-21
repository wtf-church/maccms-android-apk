package com.maccms.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.mobile.data.Episode
import com.maccms.mobile.data.PlaySource
import com.maccms.mobile.ui.theme.AppSurface
import com.maccms.mobile.ui.theme.AppSurfaceHigh
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary

/** 详情页的精选选集网格（仅传少量剧集，完整列表用 EpisodePickerSheet） */
@Composable
fun EpisodeGrid(
    episodes: List<Episode>,
    onEpisodeClick: (Episode) -> Unit,
    modifier: Modifier = Modifier,
    currentEpisode: String? = null,
    lastEpisode: String? = null,
    columns: Int = 4
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        episodes.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ep ->
                    EpisodeCell(
                        episode = ep,
                        selected = ep.name == currentEpisode,
                        lastWatched = ep.name == lastEpisode,
                        onClick = { onEpisodeClick(ep) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun EpisodeCell(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    lastWatched: Boolean = false,
    height: Int = 42
) {
    Box(
        modifier
            .height(height.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> BrandRed
                    lastWatched -> Color(0xFF33161A)
                    else -> AppSurfaceHigh
                }
            )
            .border(
                1.dp,
                when {
                    selected -> BrandRed
                    lastWatched -> BrandRed.copy(alpha = 0.8f)
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            episode.name,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        if (lastWatched && !selected) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(width = 14.dp, height = 2.dp)
                    .background(BrandRed, RoundedCornerShape(1.dp))
            )
        }
    }
}

/**
 * 全集选集弹层：线路切换 + 网格。
 * 手机端从详情页「查看全部」或播放页「选集」打开。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodePickerSheet(
    sources: List<PlaySource>,
    initialSourceIndex: Int,
    currentEpisode: String?,
    onDismiss: () -> Unit,
    onEpisodeSelected: (PlaySource, Episode) -> Unit
) {
    if (sources.isEmpty()) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sourceIndex by remember(initialSourceIndex, sources) {
        mutableIntStateOf(initialSourceIndex.coerceIn(0, sources.lastIndex))
    }
    val source = sources.getOrNull(sourceIndex) ?: sources.first()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppSurface,
        contentColor = TextPrimary,
        dragHandle = null
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.72f)) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选集", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "共 ${source.episodes.size} 集",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "点击播放",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
            if (sources.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sources.size) { index ->
                        val s = sources[index]
                        FilterChip(
                            selected = index == sourceIndex,
                            onClick = { sourceIndex = index },
                            label = {
                                Text(
                                    s.from,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BrandRed,
                                selectedLabelColor = Color.White,
                                containerColor = AppSurfaceHigh,
                                labelColor = TextSecondary
                            ),
                            border = null
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(source.episodes) { ep ->
                    EpisodeCell(
                        episode = ep,
                        selected = ep.name == currentEpisode,
                        lastWatched = false,
                        onClick = { onEpisodeSelected(source, ep) },
                        height = 44
                    )
                }
            }
        }
    }
}
