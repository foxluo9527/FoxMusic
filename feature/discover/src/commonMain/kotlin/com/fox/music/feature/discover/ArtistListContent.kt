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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.component.AlphabetIndexBar
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.SectionLetterHeader
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
) {
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("全部歌手") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        contentDescription = "返回",
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        when {
            state.isLoading -> LoadingIndicator(useLottie = false)
            state.error != null -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize(),
                    message = state.error ?: "加载失败",
                    onRetry = { onIntent(ArtistListIntent.Load) },
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
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .padding(end = 28.dp),
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
                                ArtistListItem(
                                    artist = artist,
                                    onClick = { onArtistClick(artist.id) },
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
