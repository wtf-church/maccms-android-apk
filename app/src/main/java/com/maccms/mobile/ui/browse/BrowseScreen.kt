package com.maccms.mobile.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maccms.mobile.data.Categories
import com.maccms.mobile.data.Vod
import com.maccms.mobile.data.VodRepository
import com.maccms.mobile.ui.components.EmptyState
import com.maccms.mobile.ui.components.ErrorState
import com.maccms.mobile.ui.components.InlineLoading
import com.maccms.mobile.ui.components.LoadingState
import com.maccms.mobile.ui.components.PosterCard
import com.maccms.mobile.ui.theme.AppBlack
import com.maccms.mobile.ui.theme.AppSurfaceHigh
import com.maccms.mobile.ui.theme.BrandRed
import com.maccms.mobile.ui.theme.TextPrimary
import com.maccms.mobile.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * 浏览页状态提升到 NavHost 外层：切换底部 Tab / 进详情再返回时，
 * 分类选择、已加载列表与滚动位置全部保留。
 */
class BrowseState {
    var selectedType by mutableIntStateOf(Categories.ALL.first().first)
    var vods by mutableStateOf<List<Vod>>(emptyList())
    var loadedType by mutableIntStateOf(0)
    var loadedReloadToken by mutableIntStateOf(-1)
    var reloadToken by mutableIntStateOf(0)
    var maxPage by mutableIntStateOf(1)
    var nextPage by mutableIntStateOf(2)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var gridState: LazyGridState = LazyGridState()

    fun selectType(id: Int) {
        if (selectedType != id) {
            selectedType = id
            reloadToken++
        }
    }

    fun reload() { reloadToken++ }
}

@Composable
fun BrowseScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit,
    state: BrowseState
) {
    val scope = rememberCoroutineScope()
    val chipListState = rememberLazyListState()

    fun loadFirstPage(typeId: Int, token: Int) {
        scope.launch {
            state.loading = true
            state.error = null
            state.vods = emptyList()
            state.nextPage = 2
            state.maxPage = 1
            // 列表此时尚未组合，scrollToItem 会挂起到首次布局；放到独立协程里，避免阻塞请求
            scope.launch { runCatching { state.gridState.scrollToItem(0) } }
            repository.getListByTypePage(typeId, 1).onSuccess { resp ->
                if (state.selectedType == typeId && state.reloadToken == token) {
                    state.vods = resp.list
                    state.maxPage = resp.pagecount.coerceAtLeast(1)
                    state.nextPage = 2
                    state.loadedType = typeId
                    state.loadedReloadToken = token
                }
                state.loading = false
            }.onFailure {
                if (state.selectedType == typeId && state.reloadToken == token) {
                    state.error = it.message
                    state.loadedType = typeId
                    state.loadedReloadToken = token
                }
                state.loading = false
            }
        }
    }

    // 只在分类/刷新令牌变化时加载；返回浏览页时复用已加载数据
    LaunchedEffect(state.selectedType, state.reloadToken) {
        val upToDate = state.loadedType == state.selectedType &&
            state.loadedReloadToken == state.reloadToken &&
            state.vods.isNotEmpty()
        if (!upToDate) loadFirstPage(state.selectedType, state.reloadToken)
    }

    // 分类切换后把选中的分类条滚到可见区域
    LaunchedEffect(state.selectedType) {
        val index = Categories.ALL.indexOfFirst { it.first == state.selectedType }
        if (index >= 0) chipListState.animateScrollToItem(index)
    }

    // 触底预取：derivedStateOf 会随滚动位置变化重新计算
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = state.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.vods.isNotEmpty() &&
                last >= state.vods.size - 7 &&
                state.nextPage <= state.maxPage &&
                !state.loading && !state.loadingMore
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (!shouldLoadMore) return@LaunchedEffect
        val type = state.selectedType
        val page = state.nextPage
        state.loadingMore = true
        repository.getListByTypePage(type, page).onSuccess { resp ->
            if (state.selectedType == type) {
                state.vods = (state.vods + resp.list).distinctBy { it.vod_id }
                state.maxPage = resp.pagecount.coerceAtLeast(state.maxPage)
                state.nextPage = page + 1
            }
        }
        state.loadingMore = false
    }

    Column(Modifier.fillMaxSize().background(AppBlack).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("浏览", color = TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                Categories.nameOf(state.selectedType),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { state.reload() }) {
                Icon(Icons.Outlined.Refresh, contentDescription = "刷新", tint = TextPrimary)
            }
        }

        LazyRow(
            state = chipListState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(Categories.ALL.size) { index ->
                val (id, name) = Categories.ALL[index]
                CategoryChip(
                    name = name,
                    selected = id == state.selectedType,
                    onClick = { state.selectType(id) }
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error ?: "",
                    onRetry = { state.reload() }
                )
                state.vods.isEmpty() -> EmptyState(
                    title = "该分类暂无内容",
                    subtitle = "换个分类看看吧"
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = state.gridState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.vods, key = { it.vod_id }) { vod ->
                        PosterCard(vod = vod, onClick = { onVodClick(vod) })
                    }
                    if (state.loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) { InlineLoading() }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryChip(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) BrandRed else AppSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
