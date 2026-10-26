package com.fox.music.iosshared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.data.mapper.toPlaylist
import com.fox.music.core.data.mapper.toUserHomeProfile
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.user.UserHomeProfile
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxDialog
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.ProfileRelationshipBottomBar
import com.fox.music.core.ui.component.ReportBottomSheet
import com.fox.music.core.ui.component.UserLastListenedBar
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.util.UserHomeCoverSpec
import com.fox.music.iosshared.FoxIosBridge
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.PlayableSong
import com.fox.music.iosshared.foxComposeViewController
import com.fox.music.iosshared.foxMainScope
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

private val UserHomeTabs = listOf("歌单", "动态")

/**
 * iOS 端「个人主页」：对齐 Android feature:profile-android 的 UserHomeScreen。
 *
 * * 顶部：背景图（或主题渐变）+ 返回/更多悬浮按钮 + 最近在听胶囊 + 头像/昵称/签名；
 * * 两个 Tab：创建的歌单（GET api/playlists?user_id=）、动态（GET api/posts?author_id=，Paging3）；
 * * 他人主页底部关系条（发消息 / 加好友 / 已申请 / 私密账号）；
 * * 更多菜单：自己＝分享名片/替换背景图/编辑资料/隐私设置，他人＝分享名片/举报/拉黑；
 * * 私密账号 / 被拉黑时两个 Tab 都显示受限文案（can_view_content 由后端算好）。
 *
 * 与 Android 的差异：
 * * 无 Hilt / UCrop：背景改由 Swift PHPicker 选图（不裁剪），把本地文件路径回传
 *   [uploadBackgroundImage]，上传 api/upload/image + PUT api/auth/profile 全在 Kotlin 侧；
 * * 导航全部交回 Swift（各 Tab 自己的 NavigationStack 是唯一导航真相）；
 * * 帖子的举报/分享交 Swift（复用 iOS 统一 ReportSheet / 系统分享），删除在 Compose 内
 *   二次确认后直接调 DELETE api/posts/{id}；编辑动态 iOS 暂无编辑页，给明确提示。
 */
object FoxUserHomeScreen {

    internal val api = FoxMusicApi()
    private val scope = foxMainScope()

    // ---- 背景图上传的跨层桥（Swift PHPicker → 本地路径 → 这里上传） ----

    /** 上传中转圈遮罩：Compose 直接读这个状态 recompose。 */
    var isUploadingBackground by mutableStateOf(false)
        private set

    /** 上传成功后自增；页面 LaunchedEffect 监听到就重新拉资料，换上新背景。 */
    internal var backgroundRevision by mutableIntStateOf(0)
        private set

    /**
     * Swift 选完图后调用：[filePath] 是 Swift 复制进 tmp 的图片路径。
     * [onResult] 回传 null 表示成功，否则是错误信息（已在主线程）。
     */
    fun uploadBackgroundImage(filePath: String, onResult: (String?) -> Unit) {
        scope.launch {
            isUploadingBackground = true
            try {
                val url = api.uploadImage(filePath)
                api.updateProfile(homepageBackground = url)
                backgroundRevision += 1
                runCatching { onResult(null) }
            } catch (e: Exception) {
                runCatching { onResult(e.message ?: "上传失败") }
            } finally {
                isUploadingBackground = false
            }
        }
    }

    fun makeUserHomeViewController(
        userId: Long,
        currentUserId: Long,
        onBack: () -> Unit,
        onNavigateToChat: (userId: Long, peerNickname: String?, peerAvatar: String?) -> Unit,
        onNavigateToAddFriend: (userId: Long, nickname: String?) -> Unit,
        onNavigateToPlaylist: (playlistId: Long) -> Unit,
        onNavigateToPostDetail: (postId: Long) -> Unit,
        /** 动态列表帖子的话题标签 → Swift 推搜索页（帖子 Tab + 标签）。 */
        onNavigateToTagSearch: (tag: String) -> Unit = {},
        onNavigateToEditProfile: () -> Unit,
        onNavigateToPrivacySettings: () -> Unit,
        onPickBackgroundImage: () -> Unit,
        onPlayMusic: (PlayableSong?) -> Unit,
        onShareProfile: (userId: Long, nickname: String?, signature: String?) -> Unit,
        onSharePost: (postId: Long, title: String?) -> Unit,
        onReportPost: (postId: Long, title: String?) -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        UserHomeRoute(
            userId = userId,
            currentUserId = currentUserId,
            onBack = { runCatching(onBack) },
            onNavigateToChat = { id, nickname, avatar -> runCatching { onNavigateToChat(id, nickname, avatar) } },
            onNavigateToAddFriend = { id, nickname -> runCatching { onNavigateToAddFriend(id, nickname) } },
            onNavigateToPlaylist = { id -> runCatching { onNavigateToPlaylist(id) } },
            onNavigateToPostDetail = { id -> runCatching { onNavigateToPostDetail(id) } },
            onNavigateToTagSearch = { tag -> runCatching { onNavigateToTagSearch(tag) } },
            onNavigateToEditProfile = { runCatching(onNavigateToEditProfile) },
            onNavigateToPrivacySettings = { runCatching(onNavigateToPrivacySettings) },
            onPickBackgroundImage = { runCatching(onPickBackgroundImage) },
            onPlayMusic = { song -> runCatching { onPlayMusic(song) } },
            onShareProfile = { id, nickname, signature -> runCatching { onShareProfile(id, nickname, signature) } },
            onSharePost = { id, title -> runCatching { onSharePost(id, title) } },
            onReportPost = { id, title -> runCatching { onReportPost(id, title) } },
            onToast = { message -> runCatching { onToast(message) } },
        )
    }
}

private data class UserHomeUiState(
    val profile: UserHomeProfile? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val isPlaylistsLoading: Boolean = false,
    val error: String? = null,
    val showMoreSheet: Boolean = false,
    val showBlockDialog: Boolean = false,
    val showReportSheet: Boolean = false,
    val isReporting: Boolean = false,
    val isBlocking: Boolean = false,
    val pendingDeletePost: Post? = null,
    val isDeletingPost: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserHomeRoute(
    userId: Long,
    currentUserId: Long,
    onBack: () -> Unit,
    onNavigateToChat: (Long, String?, String?) -> Unit,
    onNavigateToAddFriend: (Long, String?) -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onNavigateToPostDetail: (Long) -> Unit,
    onNavigateToTagSearch: (String) -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onNavigateToPrivacySettings: () -> Unit,
    onPickBackgroundImage: () -> Unit,
    onPlayMusic: (PlayableSong?) -> Unit,
    onShareProfile: (Long, String?, String?) -> Unit,
    onSharePost: (Long, String?) -> Unit,
    onReportPost: (Long, String?) -> Unit,
    onToast: (String) -> Unit,
) {
    val screenScope = remember { foxMainScope() }
    var state by remember { mutableStateOf(UserHomeUiState()) }

    val postsFlow = remember(userId) {
        Pager(
            config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 20),
            pagingSourceFactory = { PostPagingSource(FoxUserHomeScreen.api, authorId = userId) },
        ).flow.cachedIn(screenScope)
    }
    val postPagingItems = postsFlow.collectAsLazyPagingItems()

    fun loadPlaylists(profile: UserHomeProfile) {
        if (!profile.canViewContent) {
            state = state.copy(playlists = emptyList(), isPlaylistsLoading = false)
            return
        }
        screenScope.launch {
            state = state.copy(isPlaylistsLoading = true)
            val list = runCatching {
                FoxUserHomeScreen.api.fetchUserPlaylists(userId).map { it.toPlaylist() }
            }.getOrDefault(emptyList())
            state = state.copy(playlists = list, isPlaylistsLoading = false)
        }
    }

    fun load() {
        screenScope.launch {
            state = state.copy(isLoading = true, error = null)
            try {
                val profile = FoxUserHomeScreen.api.fetchUserHomeProfile(userId)?.toUserHomeProfile()
                    ?: throw IllegalStateException("用户不存在")
                state = state.copy(profile = profile, isLoading = false)
                loadPlaylists(profile)
            } catch (e: Exception) {
                state = state.copy(isLoading = false, error = e.message ?: "加载失败")
            }
        }
    }

    LaunchedEffect(userId) { load() }

    // 背景图上传成功后重拉资料
    val bgRevision = FoxUserHomeScreen.backgroundRevision
    LaunchedEffect(bgRevision) {
        if (bgRevision > 0) load()
    }

    val profile = state.profile
    val pagerState = rememberPagerState { UserHomeTabs.size }

    LaunchedEffect(state.selectedTab) {
        if (pagerState.currentPage != state.selectedTab) {
            pagerState.animateScrollToPage(state.selectedTab)
        }
    }

    // 用户手势左右滑 Pager 时反向同步 Tab 选中态（下划线/加粗跟随）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (state.selectedTab != page) {
                state = state.copy(selectedTab = page)
            }
        }
    }

    // ---- 行为 ----

    fun confirmBlock() {
        screenScope.launch {
            state = state.copy(isBlocking = true)
            try {
                FoxUserHomeScreen.api.blockUser(userId)
                state = state.copy(isBlocking = false, showBlockDialog = false)
                onToast("已加入黑名单")
                onBack()
            } catch (e: Exception) {
                state = state.copy(isBlocking = false)
                onToast(e.message ?: "操作失败")
            }
        }
    }

    fun submitUserReport(reason: String, description: String?) {
        screenScope.launch {
            state = state.copy(isReporting = true)
            try {
                FoxUserHomeScreen.api.submitReport(
                    targetType = "user",
                    targetId = userId,
                    reason = reason,
                    description = description,
                )
                state = state.copy(isReporting = false, showReportSheet = false)
                onToast("举报已提交")
            } catch (e: Exception) {
                state = state.copy(isReporting = false)
                onToast(e.message ?: "举报失败")
            }
        }
    }

    fun confirmDeletePost() {
        val post = state.pendingDeletePost ?: return
        screenScope.launch {
            state = state.copy(isDeletingPost = true)
            try {
                FoxUserHomeScreen.api.deletePost(post.id)
                state = state.copy(isDeletingPost = false, pendingDeletePost = null)
                onToast("动态已删除")
                postPagingItems.refresh()
            } catch (e: Exception) {
                state = state.copy(isDeletingPost = false, pendingDeletePost = null)
                onToast(e.message ?: "删除失败")
            }
        }
    }

    // ---- 布局 ----

    Column(modifier = Modifier.fillMaxSize()) {
        when {
            state.isLoading && profile == null -> {
                Box(modifier = Modifier.weight(1f)) {
                    LoadingIndicator(modifier = Modifier.fillMaxSize(), useLottie = false)
                    UserHomeTopOverlay(onBack = onBack, showMore = false)
                }
            }
            state.error != null && profile == null -> {
                Box(modifier = Modifier.weight(1f)) {
                    ErrorView(
                        modifier = Modifier.fillMaxSize(),
                        message = state.error!!,
                        onRetry = { load() },
                    )
                    UserHomeTopOverlay(onBack = onBack, showMore = false)
                }
            }
            profile != null -> {
                UserHomeHeader(
                    profile = profile,
                    onBack = onBack,
                    onMore = { state = state.copy(showMoreSheet = true) },
                    onPlayLastListened = {
                        profile.lastListened?.let { music ->
                            onPlayMusic(FoxIosBridge.playableSong(music))
                        }
                    },
                )
                UserHomeTabRow(
                    selectedTab = state.selectedTab,
                    onTabSelected = { index ->
                        state = state.copy(selectedTab = index)
                        screenScope.launch { pagerState.animateScrollToPage(index) }
                    },
                )
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                ) { page ->
                    if (!profile.canViewContent) {
                        UserHomeContentRestricted(
                            message = if (profile.isBlockedByTarget) {
                                "对方已将你加入黑名单"
                            } else {
                                "私密账号，暂不可查看"
                            },
                        )
                        return@HorizontalPager
                    }
                    when (page) {
                        0 -> UserHomePlaylistTab(
                            playlists = state.playlists,
                            isLoading = state.isPlaylistsLoading,
                            onPlaylistClick = onNavigateToPlaylist,
                        )
                        else -> UserHomePostsTab(
                            postPagingItems = postPagingItems,
                            currentUserId = currentUserId,
                            onPostDetail = onNavigateToPostDetail,
                            onTagClick = onNavigateToTagSearch,
                            onSharePost = onSharePost,
                            onReportPost = onReportPost,
                            onDeletePost = { post ->
                                state = state.copy(pendingDeletePost = post)
                            },
                            onEditPost = { onToast("编辑动态功能待迁移") },
                        )
                    }
                }
            }
        }

        if (profile != null && !profile.isSelf) {
            ProfileRelationshipBottomBar(
                isFriend = profile.isFriend,
                isRequested = profile.isRequested,
                isPrivateAccount = profile.isPrivate,
                // iOS 页面底色是暖色米白，Surface 默认白底 + 8dp 阴影会在底部形成一条
                // 与列表割裂的白条；容器透明、阴影去掉，让底栏融入页面背景。
                showBackGround = false,
                onSendMessage = {
                    onNavigateToChat(profile.id, profile.nickname, profile.avatar)
                },
                onAddFriend = {
                    onNavigateToAddFriend(profile.id, profile.nickname)
                },
                // iOS 底栏自己吃底部安全区（Android 靠 Scaffold bottomBar）
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }

    if (FoxUserHomeScreen.isUploadingBackground) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }

    if (state.showMoreSheet && profile != null) {
        UserHomeMoreSheet(
            isSelf = profile.isSelf,
            onDismiss = { state = state.copy(showMoreSheet = false) },
            onReplaceBackground = {
                state = state.copy(showMoreSheet = false)
                onPickBackgroundImage()
            },
            onEditProfile = {
                state = state.copy(showMoreSheet = false)
                onNavigateToEditProfile()
            },
            onPrivacySettings = {
                state = state.copy(showMoreSheet = false)
                onNavigateToPrivacySettings()
            },
            onShareProfile = {
                state = state.copy(showMoreSheet = false)
                onShareProfile(profile.id, profile.nickname, profile.signature)
            },
            onReport = {
                state = state.copy(showReportSheet = true, showMoreSheet = false)
            },
            onBlock = {
                state = state.copy(showBlockDialog = true, showMoreSheet = false)
            },
        )
    }

    if (state.showBlockDialog) {
        FoxDialog(
            title = "加入黑名单",
            text = "确定要将该用户加入黑名单吗？加入后将自动解除好友关系。",
            confirmText = "确定",
            onDismiss = { state = state.copy(showBlockDialog = false) },
            onConfirm = { confirmBlock() },
            confirmEnabled = !state.isBlocking,
            destructive = true,
        )
    }

    if (state.showReportSheet && profile != null) {
        ReportBottomSheet(
            targetTitle = "用户",
            targetName = profile.nickname ?: "用户",
            isSubmitting = state.isReporting,
            onDismiss = { state = state.copy(showReportSheet = false) },
            onSubmit = { reason, description -> submitUserReport(reason, description) },
        )
    }

    state.pendingDeletePost?.let { post ->
        FoxDialog(
            title = "删除动态",
            text = "确定删除这条动态吗？删除后不可恢复。",
            confirmText = "删除",
            onDismiss = {
                if (!state.isDeletingPost) state = state.copy(pendingDeletePost = null)
            },
            onConfirm = { confirmDeletePost() },
            confirmEnabled = !state.isDeletingPost,
            destructive = true,
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
    onPlayLastListened: () -> Unit,
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

        profile.lastListened?.takeIf { profile.showLastListened }?.let { music ->
            UserLastListenedBar(
                music = music,
                labelPrefix = if (profile.isSelf) "最近在听" else "对方在听",
                onClick = onPlayLastListened,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .height(36.dp)
                    .padding(horizontal = 56.dp),
            )
        }

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
private fun UserHomeContentRestricted(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    postPagingItems: androidx.paging.compose.LazyPagingItems<Post>,
    currentUserId: Long,
    onPostDetail: (Long) -> Unit,
    onTagClick: (String) -> Unit,
    onSharePost: (Long, String?) -> Unit,
    onReportPost: (Long, String?) -> Unit,
    onDeletePost: (Post) -> Unit,
    onEditPost: (Long) -> Unit,
) {
    when {
        postPagingItems.loadState.refresh is androidx.paging.LoadState.Loading &&
            postPagingItems.itemCount == 0 -> {
            LoadingIndicator(modifier = Modifier.fillMaxSize(), useLottie = false)
        }
        postPagingItems.loadState.refresh is androidx.paging.LoadState.Error &&
            postPagingItems.itemCount == 0 -> {
            ErrorView(
                modifier = Modifier.fillMaxSize(),
                message = (postPagingItems.loadState.refresh as androidx.paging.LoadState.Error).error.message
                    ?: "加载失败",
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
                        onShareClick = { onSharePost(post.id, post.title) },
                        onUserClick = { onPostDetail(post.id) },
                        onTagClick = { onTagClick(it) },
                        onEditClick = { onEditPost(post.id) },
                        onDeleteClick = { onDeletePost(post) },
                        onReportClick = { onReportPost(post.id, post.title) },
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
    FoxSheet(
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
    icon: ImageVector,
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
