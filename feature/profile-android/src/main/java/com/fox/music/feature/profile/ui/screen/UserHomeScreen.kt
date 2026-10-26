package com.fox.music.feature.profile.ui.screen

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.core.ui.component.ProfileRelationshipBottomBar
import com.fox.music.core.ui.component.ReportBottomSheet
import com.fox.music.core.ui.component.UserLastListenedBar
import com.fox.music.core.ui.theme.CoverShapeRow
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.foxHairline
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarIconAction
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
    onNavigateToPlayer: () -> Unit = {},
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
    // 头像 / 背景图点击后的全屏浏览（复用帖子图片预览：黑底 + 双指缩放 + 双击放大）
    var previewImage by remember { mutableStateOf<String?>(null) }

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
                UserHomeEffect.NavigateToPlayer -> onNavigateToPlayer()
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
            if (showBottomBar) {
                ProfileRelationshipBottomBar(
                    isFriend = profile.isFriend,
                    isRequested = profile.isRequested,
                    isPrivateAccount = profile.isPrivate,
                    onSendMessage = viewModel::onSendMessageClick,
                    onAddFriend = viewModel::onAddFriendClick,
                    showBackGround = false
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
                        onPlayLastListened = viewModel::onLastListenedClick,
                        onAvatarClick = {
                            profile.avatar?.takeIf { it.isNotBlank() }?.let { previewImage = it }
                        },
                        onBackgroundClick = {
                            profile.homepageBackground?.takeIf { it.isNotBlank() }
                                ?.let { previewImage = it }
                        },
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
                            0 -> if (profile.canViewContent) {
                                UserHomePlaylistTab(
                                    playlists = state.playlists,
                                    isLoading = state.isPlaylistsLoading,
                                    onPlaylistClick = viewModel::onPlaylistClick,
                                )
                            } else {
                                UserHomeContentRestricted(
                                    message = if (profile.isBlockedByTarget) {
                                        "对方已将你加入黑名单"
                                    } else {
                                        "私密账号，暂不可查看"
                                    },
                                )
                            }
                            else -> if (profile.canViewContent) {
                                UserHomePostsTab(
                                    postPagingItems = postPagingItems,
                                    currentUserId = currentUserId,
                                    onPostDetail = viewModel::onPostDetailClick,
                                    onSharePost = onSharePost,
                                    onReportPost = onReportPost,
                                    onDeletePost = onDeletePost,
                                    onEditPost = onEditPost,
                                )
                            } else {
                                UserHomeContentRestricted(
                                    message = if (profile.isBlockedByTarget) {
                                        "对方已将你加入黑名单"
                                    } else {
                                        "私密账号，暂不可查看"
                                    },
                                )
                            }
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
        FoxDialog(
            title = "加入黑名单",
            text = "确定要将该用户加入黑名单吗？加入后将自动解除好友关系。",
            confirmText = "确定",
            onDismiss = { viewModel.sendIntent(UserHomeIntent.DismissBlockDialog) },
            onConfirm = { viewModel.sendIntent(UserHomeIntent.ConfirmBlock) },
            confirmEnabled = !state.isBlocking,
            destructive = true,
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

    previewImage?.let { url ->
        PostImagePreviewDialog(
            images = listOf(url),
            initialIndex = 0,
            onDismiss = { previewImage = null },
        )
    }
}

@Composable
private fun UserHomeTopOverlay(
    onBack: () -> Unit,
    showMore: Boolean,
    onMore: () -> Unit = {},
) {
    FoxTopBar(
        showBackButton = true,
        onBackClick = onBack,
        onMedia = true,
        actions = {
            if (showMore) {
                FoxTopBarIconAction(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    onClick = onMore,
                    tint = Color.White,
                )
            }
        },
    )
}

/**
 * 个人主页头部：照片从下往上淡出成纸感底色，身份信息落在"纸面"而不是照片上。
 *
 * 改版前的三个问题：
 * 1. 昵称 / 签名是纯白压在照片上，浅色照片（本例是米色被褥）只有 1.6:1 左右，几乎读不出来；
 * 2. 头像与照片之间没有任何边界，80dp 圆直接"融"进背景；
 * 3. 照片底边硬切到下面的白底 tab 行，中间少了一层过渡。
 *
 * 现在改成：照片保留在上半部，底部用 [MaterialTheme.colorScheme.background] 渐隐收口，
 * 昵称/签名改用墨色（纸面上约 16:1），头像加 2.5dp 白环 + 阴影跨在渐变交界处。
 */
@Composable
private fun UserHomeHeader(
    profile: UserHomeProfile,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onPlayLastListened: () -> Unit,
    onAvatarClick: () -> Unit = {},
    onBackgroundClick: () -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    val hasBackground = !profile.homepageBackground.isNullOrBlank()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(UserHomeCoverSpec.WIDTH_TO_HEIGHT),
    ) {
        if (hasBackground) {
            CachedImage(
                imageUrl = profile.homepageBackground,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderIcon = Icons.Default.Image,
            )
            // 背景层点击看大图：无涟漪（整屏 ripple 太跳），压在图片之上、
            // 头像 / 顶部按钮 / 最近在听浮层之下，只有点到空白背景才触发。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBackgroundClick,
                    ),
            )
        } else {
            // 没有自定义背景时不再用「主色 + 次色横向渐变」（两色相接处会出一条脏边），
            // 改成同色系自上而下的一次衰减，仍保留一点顶部光感。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                scheme.primary.copy(alpha = 0.18f),
                                scheme.primary.copy(alpha = 0.07f),
                                scheme.background,
                            ),
                        ),
                    ),
            )
        }

        // 顶部压暗：给返回 / 更多 / 最近在听留出可读的深色底。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.34f),
                            Color.Black.copy(alpha = 0.06f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        // 底部渐隐：照片 → 页面底色。身份信息落在这层之上，所以文字可以用墨色。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.34f to scheme.background.copy(alpha = 0.42f),
                        0.62f to scheme.background.copy(alpha = 0.86f),
                        0.82f to scheme.background,
                        1.00f to scheme.background,
                    ),
                ),
        )

        UserHomeTopOverlay(
            onBack = onBack,
            showMore = true,
            onMore = onMore,
        )

        profile.lastListened?.takeIf { profile.showLastListened }?.let { music ->
            UserLastListenedBar(
                music = music,
                labelPrefix = if (profile.isSelf) "最近在听" else "对方在听",
                onClick = onPlayLastListened,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .height(34.dp)
                    .padding(horizontal = 58.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = 0.16f),
                        spotColor = Color.Black.copy(alpha = 0.18f),
                    )
                    .background(scheme.surface, CircleShape)
                    // 2.5dp 白环：让头像从照片里"剥"出来，同时给渐隐交界一个干净的接缝。
                    .padding(2.5.dp)
                    .clip(CircleShape)
                    .then(
                        // 有头像才可点开大图；占位态点了没意思
                        if (!profile.avatar.isNullOrBlank()) {
                            Modifier.clickable(onClick = onAvatarClick)
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CachedImage(
                    imageUrl = profile.avatar,
                    contentDescription = profile.nickname,
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    placeholderIcon = Icons.Default.Person,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = profile.nickname?.takeIf { it.isNotBlank() } ?: "用户",
                // 单独指定 fontSize 会继承 bodyLarge 的 24sp lineHeight 而被裁切，
                // 所以这里连 lineHeight 一起给死。
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            profile.signature?.takeIf { it.isNotBlank() }?.let { signature ->
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = signature,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp,
                    ),
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 下划线 tab：与「我的」页的 [ProfileUnderlineTabs] 同一套规格
 * （pagePadding 对齐、14.5sp、18dp/2dp 指示条），不再用各页各自的 16dp/24dp/3dp。
 */
@Composable
private fun UserHomeTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(scheme.background)
                .padding(horizontal = FoxSpacing.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            UserHomeTabs.forEachIndexed { index, title ->
                val selected = selectedTab == index
                val textColor by animateColorAsState(
                    targetValue = if (selected) scheme.onBackground else scheme.onSurfaceVariant,
                    label = "userHomeTabColor",
                )
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 18.dp else 0.dp,
                    label = "userHomeTabIndicator",
                )
                Column(
                    modifier = Modifier
                        .clickable { onTabSelected(index) }
                        .padding(vertical = 11.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = 14.5.sp,
                            lineHeight = 19.sp,
                        ),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(indicatorWidth)
                            .background(scheme.primary, shape = RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        HorizontalDivider(color = PaperHairline, thickness = 1.dp)
    }
}

@Composable
private fun UserHomeContentRestricted(message: String) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = FoxSpacing.pagePadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(scheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
            color = scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
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
            start = FoxSpacing.pagePadding,
            end = FoxSpacing.pagePadding,
            bottom = FoxSpacing.tabChromeBottom,
        ),
    ) {
        // 分组标题走「标题 + 右侧计数」：计数是弱信息，退到 ink-3，不再和标题一起糊成一片灰。
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = FoxSpacing.headerGap),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "创建的歌单",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${playlists.size} 个",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (playlists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "还没有创建歌单",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "在「我的」页点「创建歌单」即可开始收藏",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                UserHomePlaylistItem(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist.id) },
                )
                // 最后一条不画线，避免列表尾巴多一根"悬空"的分隔。
                if (index != playlists.lastIndex) {
                    UserHomeListDivider()
                }
            }
        }
    }
}

/** 分隔线从文字轴起算：封面 52 + 间距 13（24 的 pagePadding 已由 contentPadding 承担）。 */
@Composable
private fun UserHomeListDivider() {
    HorizontalDivider(
        color = PaperHairline,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 65.dp),
    )
}

@Composable
private fun UserHomePlaylistItem(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = playlist.coverImage,
            contentDescription = playlist.title,
            modifier = Modifier
                .size(52.dp)
                .clip(CoverShapeRow),
            shape = CoverShapeRow,
            placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
        )
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                ),
                fontWeight = FontWeight.Medium,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                ),
                // 旧版用 colorScheme.outline（浅灰）当副标题，纸感底上只有 2.6:1，
                // 换成 ink-3 的 onSurfaceVariant 拿回 7:1。这里也不再"${n}首"挤在一起。
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = scheme.onSurfaceVariant.copy(alpha = 0.45f),
            modifier = Modifier.size(18.dp),
        )
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
    val scheme = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    FoxSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
        ) {
            Text(
                text = if (isSelf) "我的主页" else "更多操作",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                if (isSelf) {
                    UserHomeMoreItem(
                        icon = Icons.Default.Share,
                        title = "分享名片",
                        onClick = {
                            // 分享面板会盖在操作面板上，先把本面板收起再弹。
                            onDismiss()
                            onShareProfile()
                        },
                    )
                    UserHomeMoreDivider()
                    UserHomeMoreItem(
                        icon = Icons.Default.Image,
                        title = "替换背景图",
                        onClick = onReplaceBackground,
                    )
                    UserHomeMoreDivider()
                    UserHomeMoreItem(
                        icon = Icons.Default.Edit,
                        title = "编辑资料",
                        onClick = onEditProfile,
                    )
                    UserHomeMoreDivider()
                    UserHomeMoreItem(
                        icon = Icons.Default.Security,
                        title = "隐私设置",
                        onClick = onPrivacySettings,
                    )
                } else {
                    UserHomeMoreItem(
                        icon = Icons.Default.Share,
                        title = "分享名片",
                        onClick = {
                            // 分享面板会盖在操作面板上，先把本面板收起再弹。
                            onDismiss()
                            onShareProfile()
                        },
                    )
                    UserHomeMoreDivider()
                    UserHomeMoreItem(
                        icon = Icons.Default.Report,
                        title = "举报该用户",
                        onClick = onReport,
                        destructive = true,
                    )
                    UserHomeMoreDivider()
                    UserHomeMoreItem(
                        icon = Icons.Default.Block,
                        title = "加入黑名单",
                        onClick = onBlock,
                        destructive = true,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 独立「取消」：M3 默认面板只能下滑关闭，返回键之外的鼠标/单手用户没有明确出口。
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "取消",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UserHomeMoreDivider() {
    HorizontalDivider(
        color = foxHairline(),
        thickness = 1.dp,
        modifier = Modifier.padding(start = 58.dp),
    )
}

/**
 * 面板行：图标放进 30dp 圆角方块（surface 底），让图标从「一列黑符号」变成可分组的视觉锚点；
 * 危险操作（举报/拉黑）整行走 error 色，避免和普通设置项长得一样。
 */
@Composable
private fun UserHomeMoreItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val contentColor = if (destructive) scheme.error else scheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 15.sp,
                lineHeight = 20.sp,
            ),
            color = contentColor,
        )
    }
}
