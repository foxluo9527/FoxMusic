package com.fox.music.feature.discover

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.component.AlphabetIndexBar
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SectionLetterHeader
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 全部歌手页纯界面：state + onIntent 驱动，字母索引滚动逻辑完全跨端。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistListContent(
    state: ArtistListState,
    onIntent: (ArtistListIntent) -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: (Long) -> Unit = {},
    onBackClick: () -> Unit = {},
    // 进详情页会离开组合，滚动位置靠外部（ViewModel）存档才能「原样返回」
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    onScrollPositionChange: (index: Int, offset: Int) -> Unit = { _, _ -> },
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialFirstVisibleItemIndex,
        initialFirstVisibleItemScrollOffset = initialFirstVisibleItemScrollOffset,
    )
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) -> onScrollPositionChange(index, offset) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        FoxTopBar(
            title = "全部歌手",
            showBackButton = true,
            onBackClick = onBackClick,
        )

        when {
            state.isLoading -> LoadingIndicator(useLottie = false)
            state.error != null -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = state.error ?: "加载失败",
                    onRetry = { onIntent(ArtistListIntent.Refresh) },
                )
            }

            state.sections.isEmpty() -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = "暂无歌手",
                    showIcon = false,
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        state.sections.forEach { section ->
                            stickyHeader(key = "header_${section.letter}") {
                                SectionLetterHeader(
                                    letter = section.letter,
                                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                                )
                            }
                            items(
                                items = section.artists,
                                key = { it.id },
                            ) { artist ->
                                // key 稳定 + animateItem：缓存快照换成网络结果时，
                                // 新增/移动的条目是平滑位移，而不是整屏闪一下
                                ArtistListItem(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.id) },
                                    modifier = Modifier.animateItem().padding(horizontal = 15.dp),
                                )
                            }
                        }
                    }

                    AlphabetIndexBar(
                        letters = state.availableLetters,
                        onLetterSelected = { letter ->
                            state.indexMap[letter]?.let { index ->
                                scope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}
