package com.maccms.mobile.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.maccms.mobile.util.SearchHistoryStore
import kotlinx.coroutines.launch

/** 搜索状态提升到 App 层，切换 Tab 后结果仍保留 */
class SearchState {
    var keyword by mutableStateOf("")
    var results by mutableStateOf<List<Vod>>(emptyList())
    var searched by mutableStateOf(false)
    var loading by mutableStateOf(false)
    var loadingMore by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var total by mutableIntStateOf(0)
    var maxPage by mutableIntStateOf(1)
    var nextPage by mutableIntStateOf(2)
    var searchSeq by mutableIntStateOf(0)
    var gridState: LazyGridState = LazyGridState()
}

@Composable
fun SearchScreen(
    repository: VodRepository,
    onVodClick: (Vod) -> Unit,
    state: SearchState
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var history by remember { mutableStateOf(SearchHistoryStore.get(context)) }

    fun runSearch(raw: String) {
        val kw = raw.trim()
        if (kw.isEmpty()) return
        state.keyword = kw
        state.searched = true
        state.loading = true
        state.error = null
        state.results = emptyList()
        state.total = 0
        state.maxPage = 1
        state.nextPage = 2
        history = SearchHistoryStore.add(context, kw)
        keyboard?.hide()
        val seq = ++state.searchSeq
        scope.launch {
            // 结果网格尚未组合时 scrollToItem 会挂起；独立协程滚动，不阻塞搜索请求
            launch { runCatching { state.gridState.scrollToItem(0) } }
            repository.searchPage(kw, 1).onSuccess { resp ->
                if (seq == state.searchSeq) {
                    state.results = resp.list
                    state.total = resp.total
                    state.maxPage = resp.pagecount.coerceAtLeast(1)
                    state.nextPage = 2
                    state.loading = false
                }
            }.onFailure {
                if (seq == state.searchSeq) {
                    state.error = it.message
                    state.loading = false
                }
            }
        }
    }

    // 上一次搜索进行中切走页面会取消协程，回到搜索页时自动重试
    LaunchedEffect(Unit) {
        if (state.loading && state.keyword.isNotBlank()) runSearch(state.keyword)
    }

    // 结果触底加载下一页
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = state.gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            state.searched && !state.loading && !state.loadingMore &&
                state.results.isNotEmpty() &&
                last >= state.results.size - 7 &&
                state.nextPage <= state.maxPage
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (!shouldLoadMore) return@LaunchedEffect
        val kw = state.keyword
        val page = state.nextPage
        val seq = state.searchSeq
        state.loadingMore = true
        repository.searchPage(kw, page).onSuccess { resp ->
            if (seq == state.searchSeq && state.keyword == kw) {
                state.results = (state.results + resp.list).distinctBy { it.vod_id }
                state.maxPage = resp.pagecount.coerceAtLeast(state.maxPage)
                state.nextPage = page + 1
            }
        }
        state.loadingMore = false
    }

    Column(Modifier.fillMaxSize().background(AppBlack).statusBarsPadding()) {
        Text(
            "搜索",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp)
        )
        OutlinedTextField(
            value = state.keyword,
            onValueChange = { value ->
                state.keyword = value
                if (state.searched) {
                    state.searchSeq++
                    state.searched = false
                    state.loading = false
                    state.results = emptyList()
                    state.error = null
                }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("搜索片名关键词…", color = TextSecondary, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = TextSecondary) },
            trailingIcon = {
                if (state.keyword.isNotEmpty()) {
                    IconButton(onClick = {
                        state.keyword = ""
                        state.searchSeq++
                        state.searched = false
                        state.loading = false
                        state.results = emptyList()
                        state.error = null
                    }) {
                        Icon(Icons.Outlined.Close, contentDescription = "清空", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppSurfaceHigh,
                unfocusedContainerColor = AppSurfaceHigh,
                focusedBorderColor = BrandRed,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = BrandRed
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch(state.keyword) })
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingState()
                state.error != null -> ErrorState(
                    message = state.error ?: "",
                    onRetry = { runSearch(state.keyword) }
                )
                !state.searched -> SearchHistoryPanel(
                    history = history,
                    onPick = { runSearch(it) },
                    onClear = {
                        SearchHistoryStore.clear(context)
                        history = emptyList()
                    }
                )
                state.results.isEmpty() -> EmptyState(
                    title = "没有找到「${state.keyword}」",
                    subtitle = "换个关键词试试吧"
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Text(
                        "找到 ${state.total} 部相关影片",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = state.gridState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(state.results, key = { it.vod_id }) { vod ->
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
}

private val HOT_KEYWORDS = listOf("星际", "长安", "海贼", "舌尖")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryPanel(
    history: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        if (history.isEmpty()) {
            Text(
                "输入片名开始搜索",
                color = TextSecondary,
                fontSize = 14.sp
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "搜索历史",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text("清空", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.forEach { keyword ->
                    SearchKeywordChip(keyword) { onPick(keyword) }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "热门搜索",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HOT_KEYWORDS.forEach { keyword ->
                SearchKeywordChip(keyword) { onPick(keyword) }
            }
        }
    }
}

@Composable
private fun SearchKeywordChip(keyword: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(AppSurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(keyword, color = TextPrimary, fontSize = 13.sp)
    }
}
