package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.PostCommentInputBar
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.feature.social.viewmodel.CommunityPlazaEffect
import com.fox.music.feature.social.viewmodel.CommunityPlazaIntent
import com.fox.music.feature.social.viewmodel.CommunityPlazaViewModel

const val COMMUNITY_PLAZA_ROUTE = "community_plaza"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPlazaScreen(
    modifier: Modifier = Modifier,
    refreshTrigger: Int = 0,
    currentUserId: Long? = null,
    viewModel: CommunityPlazaViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onCreatePost: () -> Unit = {},
    onPostDetail: (Long) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val postPagingItems = state.posts.collectAsLazyPagingItems()
    var lastHandledRefreshTrigger by remember { mutableIntStateOf(0) }
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    val context = LocalContext.current

    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > lastHandledRefreshTrigger) {
            lastHandledRefreshTrigger = refreshTrigger
            viewModel.sendIntent(CommunityPlazaIntent.Refresh)
            postPagingItems.refresh()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                CommunityPlazaEffect.NavigateToCreatePost -> onCreatePost()
                is CommunityPlazaEffect.NavigateToPostDetail -> onPostDetail(effect.postId)
                is CommunityPlazaEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    previewImages?.let { images ->
        PostImagePreviewDialog(
            images = images,
            initialIndex = previewIndex,
            onDismiss = { previewImages = null },
        )
    }

    val isPagingRefreshing = postPagingItems.loadState.refresh is LoadState.Loading

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FoxTopBar(
                title = "社区广场",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    IconButton(onClick = { viewModel.onCreatePostClick() }) {
                        Icon(Icons.Filled.AddCircle, contentDescription = "发帖")
                    }
                },
            )
        },
        bottomBar = {
            if (state.commentingPostId != null) {
                Column(modifier = Modifier.navigationBarsPadding().imePadding()) {
                    PostCommentInputBar(
                        value = state.commentInput,
                        onValueChange = { viewModel.sendIntent(CommunityPlazaIntent.UpdateCommentInput(it)) },
                        onSend = { viewModel.sendIntent(CommunityPlazaIntent.SendComment) },
                        isSending = state.isCommentSending,
                    )
                }
            }
        },
    ) { innerPadding ->
        when {
            postPagingItems.loadState.refresh is LoadState.Loading && postPagingItems.itemCount == 0 -> {
                LoadingIndicator(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    useLottie = false,
                )
            }
            postPagingItems.loadState.refresh is LoadState.Error && postPagingItems.itemCount == 0 -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    message = (postPagingItems.loadState.refresh as LoadState.Error).error.message ?: "加载失败",
                    onRetry = { postPagingItems.retry() },
                )
            }
            postPagingItems.loadState.refresh is LoadState.NotLoading && postPagingItems.itemCount == 0 -> {
                ErrorView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    message = "暂无动态，点击右上角发布第一条吧",
                    showIcon = false,
                )
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = isPagingRefreshing,
                    onRefresh = {
                        viewModel.sendIntent(CommunityPlazaIntent.Refresh)
                        postPagingItems.refresh()
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            count = postPagingItems.itemCount,
                            key = postPagingItems.itemKey { it.id },
                        ) { index ->
                            val post = postPagingItems[index] ?: return@items
                            LaunchedEffect(post.id) {
                                viewModel.sendIntent(CommunityPlazaIntent.LoadCommentPreview(post.id))
                            }
                            val displayPost = viewModel.applyOverrides(post)
                            val isOwn = displayPost.userId == currentUserId
                            PostCard(
                                post = displayPost,
                                previewComment = state.commentPreviews[post.id],
                                isOwnPost = isOwn,
                                onPostClick = { onPostDetail(displayPost.id) },
                                onLikeClick = {
                                    viewModel.sendIntent(CommunityPlazaIntent.OnPostLikeClick(displayPost))
                                },
                            onCommentClick = {
                                viewModel.sendIntent(CommunityPlazaIntent.StartComment(displayPost.id))
                            },
                            onShareClick = { onSharePost(displayPost) },
                                onUserClick = { onPostDetail(displayPost.id) },
                                onEditClick = { onEditPost(displayPost.id) },
                                onDeleteClick = { onDeletePost(displayPost) },
                                onReportClick = { onReportPost(displayPost) },
                                onImageClick = { imageIndex ->
                                    previewImages = displayPost.images
                                    previewIndex = imageIndex
                                },
                            )
                        }
                        if (postPagingItems.loadState.append is LoadState.Loading) {
                            item(key = "loading_more") {
                                LoadingIndicator(useLottie = false)
                            }
                        }
                    }
                }
            }
        }
    }
}
