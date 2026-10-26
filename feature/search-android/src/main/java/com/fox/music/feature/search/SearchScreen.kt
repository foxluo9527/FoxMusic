package com.fox.music.feature.search

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlaybackSourceKind
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostNoteGridCard

fun searchRoute(
    tab: SearchResultTab? = null,
    query: String? = null,
    tag: String? = null,
    sort: String? = null,
): String {
    val encodedQuery = Uri.encode(query.orEmpty())
    val encodedTag = Uri.encode(tag.orEmpty())
    val encodedSort = Uri.encode(sort.orEmpty())
    return "search?tab=${tab?.name.orEmpty()}&query=$encodedQuery&tag=$encodedTag&sort=$encodedSort"
}

/**
 * 搜索页 Android 外壳。
 *
 * 三件事只能在安卓侧做，界面本体（[SearchContent]）在 :feature:search 的 commonMain：
 *   1. BackHandler（androidx.activity）
 *   2. LocalFocusManager 收软键盘
 *   3. 帖子结果列表——PostNoteGridCard 依赖 PostVideoCover / PostFeedMediaAspectRatio
 *      等仍在 androidMain 的组件，所以整块由这里注入
 */
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit = { _, _, _, _ -> },
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onArtistClick: (Long) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onPostClick: (Long) -> Unit = {},
    onBack: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    /** 「去收录」：跳转第三方平台搜索（添加单曲）页，带入当前关键词 */
    onCollectClick: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val postResults = state.postResults.collectAsLazyPagingItems()
    val focusManager = LocalFocusManager.current
    val playlistKey = SEARCH_ROUTE + "-${state.query}"
    val canDismissToResults = state.hasSearched && state.isSearchFocused

    fun handleBack() {
        if (canDismissToResults) {
            focusManager.clearFocus()
            viewModel.sendIntent(SearchIntent.FocusChanged(false))
        } else {
            onBack()
        }
    }

    BackHandler(onBack = { handleBack() })

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SearchEffect.NavigateToMusic -> {
                    val playlist = effect.musicList.ifEmpty { listOf(effect.music) }
                    onMusicClick(
                        effect.music,
                        playlist,
                        playlistKey,
                        PlaybackPlaylistSource(PlaybackSourceKind.SEARCH, title = state.query),
                    )
                }

                is SearchEffect.NavigateToArtist -> onArtistClick(effect.artistId)
                is SearchEffect.NavigateToAlbum -> onAlbumClick(effect.album)
                is SearchEffect.NavigateToPost -> onPostClick(effect.postId)
                SearchEffect.ClearSearchFocus -> focusManager.clearFocus()
            }
        }
    }

    // 返回搜索页时（重新进入组合）：若此前点过「去收录」，强制重搜一次。
    // 注意：直接观察 ON_RESUME 会在返回时错过事件（entry 在 observer 重新挂载前已 RESUMED），
    // 因此用 LaunchedEffect(Unit) —— 搜索页离开组合再回来会重新运行。
    LaunchedEffect(Unit) {
        if (viewModel.consumeCollectRefreshPending()) {
            viewModel.sendIntent(SearchIntent.RefreshSearch)
        }
    }

    SearchContent(
        state = state,
        onIntent = viewModel::sendIntent,
        sharedTransitionScope = sharedTransitionScope,
        animatedContentScope = animatedContentScope,
        modifier = modifier,
        onBack = onBack,
        onMusicClick = viewModel::onMusicClick,
        onArtistClick = viewModel::onArtistClick,
        onAlbumClick = viewModel::onAlbumClick,
        onMusicMoreClick = onMusicMoreClick,
        onClearFocus = { focusManager.clearFocus() },
        onMusicListChanged = viewModel::updateCurrentMusicList,
        updateMusicList = updateMusicList,
        applyPostOverrides = viewModel::applyPostOverrides,
        onPostLikeClick = viewModel::onPostLikeClick,
        onPostTagClick = viewModel::onPostTagClick,
        postResultsContent = {
            PostSearchResults(
                postResults = postResults,
                applyOverrides = viewModel::applyPostOverrides,
                onPostClick = viewModel::onPostClick,
                onLikeClick = viewModel::onPostLikeClick,
                onTagClick = viewModel::onPostTagClick,
            )
        },
        onCollectClick = { keyword ->
            viewModel.markCollectRefreshPending()
            onCollectClick(keyword)
        },
    )
}

@Composable
private fun PostSearchResults(
    postResults: androidx.paging.compose.LazyPagingItems<Post>,
    applyOverrides: (Post) -> Post,
    onPostClick: (Long) -> Unit,
    onLikeClick: (Post) -> Unit,
    onTagClick: (String) -> Unit,
) {
    when {
        postResults.loadState.refresh is LoadState.Loading -> {
            LoadingIndicator(useLottie = false)
        }

        postResults.loadState.refresh is LoadState.Error -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = (postResults.loadState.refresh as LoadState.Error).error.message
                    ?: "搜索失败",
                onRetry = { postResults.retry() },
            )
        }

        postResults.loadState.refresh is LoadState.NotLoading && postResults.itemCount == 0 -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = "未找到相关帖子",
                showIcon = false,
            )
        }

        else -> {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalItemSpacing = 8.dp,
            ) {
                items(
                    count = postResults.itemCount,
                    key = postResults.itemKey { it.id },
                ) { index ->
                    val post = postResults[index] ?: return@items
                    val displayPost = applyOverrides(post)
                    PostNoteGridCard(
                        post = displayPost,
                        onClick = { onPostClick(displayPost.id) },
                        onLikeClick = { onLikeClick(displayPost) },
                        onTagClick = onTagClick,
                    )
                }
                if (postResults.loadState.append is LoadState.Loading) {
                    item(
                        key = "loading_more",
                        span = StaggeredGridItemSpan.FullLine,
                    ) {
                        LoadingIndicator(useLottie = false)
                    }
                }
            }
        }
    }
}
