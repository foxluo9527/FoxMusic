package com.fox.music.feature.profile.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.user.UserHomeProfile
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.ProfileRelationshipBottomBar
import com.fox.music.core.ui.component.ReportBottomSheet
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.util.HomepageBackgroundCropHelper
import com.fox.music.feature.profile.ui.util.UserHomeCoverSpec
import com.fox.music.feature.profile.viewmodel.UserHomeEffect
import com.fox.music.feature.profile.viewmodel.UserHomeIntent
import com.fox.music.feature.profile.viewmodel.UserHomeViewModel
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch

private val UserHomeTabs = listOf("歌单", "动态")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserHomeScreen(
    modifier: Modifier = Modifier,
    viewModel: UserHomeViewModel = hiltViewModel(),
    currentUserId: Long? = null,
    onBack: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToPrivacySettings: () -> Unit = {},
    onNavigateToChat: (Long, String?, String?) -> Unit = { _, _, _ -> },
    onNavigateToAddFriend: (Long, String?) -> Unit = { _, _ -> },
    onNavigateToPlaylist: (Long) -> Unit = {},
    onNavigateToPostDetail: (Long) -> Unit = {},
    onSharePost: (com.fox.music.core.model.social.Post) -> Unit = {},
    onShareProfile: (com.fox.music.core.model.user.UserHomeProfile) -> Unit = {},
    onReportPost: (com.fox.music.core.model.social.Post) -> Unit = {},
    onDeletePost: (com.fox.music.core.model.social.Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val pagerState = rememberPagerState { UserHomeTabs.size }
    val scope = rememberCoroutineScope()
    val postPagingItems = viewModel.postsPagingFlow.collectAsLazyPagingItems()

    val cropBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val croppedUri = result.data?.let(UCrop::getOutput)
            croppedUri?.let { viewModel.sendIntent(UserHomeIntent.UploadBackground(it)) }
        }
    }

    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        uri?.let { sourceUri ->
            cropBackgroundLauncher.launch(
                HomepageBackgroundCropHelper.createCropIntent(context, sourceUri),
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.sendIntent(UserHomeIntent.Load)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                UserHomeEffect.NavigateBack -> onBack()
                is UserHomeEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                UserHomeEffect.NavigateToEditProfile -> onNavigateToEditProfile()
                UserHomeEffect.NavigateToPrivacySettings -> onNavigateToPrivacySettings()
                is UserHomeEffect.NavigateToChat -> onNavigateToChat(
                    effect.userId,
                    effect.peerNickname,
                    effect.peerAvatar,
                )
                is UserHomeEffect.NavigateToAddFriend -> onNavigateToAddFriend(effect.userId, effect.nickname)
                is UserHomeEffect.NavigateToPlaylist -> onNavigateToPlaylist(effect.playlistId)
                is UserHomeEffect.NavigateToPostDetail -> onNavigateToPostDetail(effect.postId)
                UserHomeEffect.PickBackgroundImage -> {
                    pickBackgroundLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
        }
    }

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }

    val profile = state.profile
    val showBottomBar = profile != null && !profile.isSelf

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (showBottomBar && profile != null) {
                ProfileRelationshipBottomBar(
                    isFriend = profile.isFriend,
                    isRequested = profile.isRequested,
                    isPrivateAccount = profile.isPrivate,
                    onSendMessage = viewModel::onSendMessageClick,
                    onAddFriend = viewModel::onAddFriendClick,
                )
            }
        },
    ) { padding ->
        val bottomPadding = padding.calculateBottomPadding()
        when {
            state.isLoading && profile == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                ) {
                    LoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        useLottie = false,
                    )
                    UserHomeTopOverlay(
                        onBack = viewModel::onBackClick,
                        showMore = false,
                    )
                }
            }
            state.error != null && profile == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                ) {
                    ErrorView(
                        modifier = Modifier.fillMaxSize(),
                        message = state.error!!,
                        onRetry = { viewModel.sendIntent(UserHomeIntent.Load) },
                    )
                    UserHomeTopOverlay(
                        onBack = viewModel::onBackClick,
                        showMore = false,
                    )
                }
            }
            profile != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPadding),
                ) {
                    UserHomeHeader(
                        profile = profile,
                        onBack = viewModel::onBackClick,
                        onMore = { viewModel.sendIntent(UserHomeIntent.ShowMoreSheet) },
                    )
                    UserHomeTabRow(
                        selectedTab = state.selectedTab,
                        onTabSelected = { index ->
                            viewModel.sendIntent(UserHomeIntent.SelectTab(index))
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                    )
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.Top,
                        userScrollEnabled = true,
                    ) { page ->
                        when (page) {
                            0 -> UserHomePlaylistTab(
                                playlists = state.playlists,
                                isLoading = state.isPlaylistsLoading,
                                onPlaylistClick = viewModel::onPlaylistClick,
                            )
                            else -> UserHomePostsTab(
                                postPagingItems = postPagingItems,
                                currentUserId = currentUserId,
                                onPostDetail = viewModel::onPostDetailClick,
                                onSharePost = onSharePost,
                                onReportPost = onReportPost,
                                onDeletePost = onDeletePost,
                                onEditPost = onEditPost,
                            )
                        }
                    }
                }
            }
        }

        if (state.isUploadingBackground) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    if (state.showMoreSheet && profile != null) {
        UserHomeMoreSheet(
            isSelf = profile.isSelf,
            onDismiss = { viewModel.sendIntent(UserHomeIntent.DismissMoreSheet) },
            onReplaceBackground = viewModel::onReplaceBackgroundClick,
            onEditProfile = viewModel::onEditProfileClick,
            onPrivacySettings = viewModel::onPrivacySettingsClick,
            onShareProfile = { onShareProfile(profile) },
            onReport = { viewModel.sendIntent(UserHomeIntent.ShowReportSheet) },
            onBlock = { viewModel.sendIntent(UserHomeIntent.ShowBlockDialog) },
        )
    }

    if (state.showBlockDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.sendIntent(UserHomeIntent.DismissBlockDialog) },
            title = { Text("加入黑名单") },
            text = { Text("确定要将该用户加入黑名单吗？加入后将自动解除好友关系。") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.sendIntent(UserHomeIntent.ConfirmBlock) },
                    enabled = !state.isBlocking,
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sendIntent(UserHomeIntent.DismissBlockDialog) }) {
                    Text("取消")
                }
            },
        )
    }

    if (state.showReportSheet && profile != null) {
        ReportBottomSheet(
            targetTitle = "用户",
            targetName = profile.nickname ?: "用户",
            isSubmitting = state.isReporting,
            onDismiss = { viewModel.sendIntent(UserHomeIntent.DismissReportSheet) },
            onSubmit = { reason, description ->
                viewModel.sendIntent(UserHomeIntent.SubmitReport(reason, description))
            },
        )
    }
}

@Composable
private fun UserHomeTopOverlay(
    onBack: () -> Unit,
    showMore: Boolean,
    onMore: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserHomeOverlayIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            onClick = onBack,
        )
        if (showMore) {
            UserHomeOverlayIconButton(
                icon = Icons.Default.MoreVert,
                contentDescription = "更多",
                onClick = onMore,
            )
        } else {
            Spacer(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun UserHomeOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun UserHomeHeader(
    profile: UserHomeProfile,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(UserHomeCoverSpec.WIDTH_TO_HEIGHT),
    ) {
        if (!profile.homepageBackground.isNullOrBlank()) {
            CachedImage(
                imageUrl = profile.homepageBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Image,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                scheme.primary.copy(alpha = 0.35f),
                                scheme.secondary.copy(alpha = 0.22f),
                                scheme.surfaceVariant.copy(alpha = 0.5f),
                            ),
                        ),
                    ),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.32f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        UserHomeTopOverlay(
            onBack = onBack,
            showMore = true,
            onMore = onMore,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CachedImage(
                imageUrl = profile.avatar,
                contentDescription = profile.nickname,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape),
                shape = CircleShape,
                placeholderIcon = Icons.Default.Person,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = profile.nickname?.takeIf { it.isNotBlank() } ?: "用户",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun UserHomeTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        UserHomeTabs.forEachIndexed { index, title ->
            val selected = selectedTab == index
            Column(
                modifier = Modifier.clickable { onTabSelected(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(3.dp)
                        .width(if (selected) 24.dp else 0.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun UserHomePlaylistTab(
    playlists: List<Playlist>,
    isLoading: Boolean,
    onPlaylistClick: (Long) -> Unit,
) {
    if (isLoading && playlists.isEmpty()) {
        LoadingIndicator(modifier = Modifier.fillMaxSize(), useLottie = false)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = FoxSpacing.tabChromeBottom,
        ),
    ) {
        item {
            Text(
                text = "创建的歌单(${playlists.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (playlists.isEmpty()) {
            item {
                Text(
                    text = "暂无歌单",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(playlists, key = { it.id }) { playlist ->
                UserHomePlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                )
            }
        }
    }
}

@Composable
private fun UserHomePlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = playlist.coverImage,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount}首",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun UserHomePostsTab(
    postPagingItems: androidx.paging.compose.LazyPagingItems<com.fox.music.core.model.social.Post>,
    currentUserId: Long?,
    onPostDetail: (Long) -> Unit,
    onSharePost: (com.fox.music.core.model.social.Post) -> Unit,
    onReportPost: (com.fox.music.core.model.social.Post) -> Unit,
    onDeletePost: (com.fox.music.core.model.social.Post) -> Unit,
    onEditPost: (Long) -> Unit,
) {
    when {
        postPagingItems.loadState.refresh is LoadState.Loading && postPagingItems.itemCount == 0 -> {
            LoadingIndicator(modifier = Modifier.fillMaxSize(), useLottie = false)
        }
        postPagingItems.loadState.refresh is LoadState.Error && postPagingItems.itemCount == 0 -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = (postPagingItems.loadState.refresh as LoadState.Error).error.message ?: "加载失败",
                onRetry = { postPagingItems.retry() },
            )
        }
        postPagingItems.itemCount == 0 -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = "暂无动态",
                showIcon = false,
            )
        }
        else -> {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = FoxSpacing.pagePadding,
                    end = FoxSpacing.pagePadding,
                    top = 12.dp,
                    bottom = FoxSpacing.tabChromeBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = postPagingItems.itemCount,
                    key = postPagingItems.itemKey { it.id },
                ) { index ->
                    val post = postPagingItems[index] ?: return@items
                    PostCard(
                        post = post,
                        isOwnPost = post.userId == currentUserId,
                        onPostClick = { onPostDetail(post.id) },
                        onLikeClick = { },
                        onCommentClick = { onPostDetail(post.id) },
                        onShareClick = { onSharePost(post) },
                        onUserClick = { onPostDetail(post.id) },
                        onEditClick = { onEditPost(post.id) },
                        onDeleteClick = { onDeletePost(post) },
                        onReportClick = { onReportPost(post) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserHomeMoreSheet(
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onReplaceBackground: () -> Unit,
    onEditProfile: () -> Unit,
    onPrivacySettings: () -> Unit,
    onShareProfile: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            UserHomeMoreItem(
                icon = Icons.Default.Share,
                title = "分享名片",
                onClick = onShareProfile,
            )
            if (isSelf) {
                UserHomeMoreItem(
                    icon = Icons.Default.Image,
                    title = "替换背景图",
                    onClick = onReplaceBackground,
                )
                UserHomeMoreItem(
                    icon = Icons.Default.Edit,
                    title = "编辑资料",
                    onClick = onEditProfile,
                )
                UserHomeMoreItem(
                    icon = Icons.Default.Security,
                    title = "隐私设置",
                    onClick = onPrivacySettings,
                )
            } else {
                UserHomeMoreItem(
                    icon = Icons.Default.Report,
                    title = "举报",
                    onClick = onReport,
                )
                UserHomeMoreItem(
                    icon = Icons.Default.Block,
                    title = "加入黑名单",
                    onClick = onBlock,
                )
            }
        }
    }
}

@Composable
private fun UserHomeMoreItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
    }
}
