package com.fox.music.feature.profile.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.user.User
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.DeletePlaylistConfirmDialog
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PlaylistManageBottomSheet
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.FoxDisplayFont
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.ui.util.UserHomeCoverSpec
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileState
import kotlinx.coroutines.launch

const val PROFILE_ROUTE = "profile"

private val ProfileTopTabs = listOf("音乐", "收藏", "动态")
private val FavoriteSectionPreviewCount = 3
private val ProfileCoverShape = RoundedCornerShape(12.dp)
private val ProfileCoverSize = 56.dp

/**
 * 「我的」页：个人主页样式头部（封面 + 头像 + 昵称 + 统计），顶层三个 tab「音乐 / 收藏 / 动态」。
 *
 * 音乐 → 我创建的歌单与历史/我喜欢；收藏 → 收藏的歌单/专辑/艺术家分区；动态 → 自己的帖子分页列表。
 */
@Composable
fun ProfileContent(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    isLogin: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    onOpenDrawer: () -> Unit = {},
    onLogin: () -> Unit = {},
    onFavoritePlaylistClick: (Playlist) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onPlayHistoryClick: () -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserHomeClick: () -> Unit = {},
    onDownloadManagerClick: () -> Unit = {},
    onEditPlaylistClick: (Long) -> Unit = {},
    onCreatePlaylistFromVm: () -> Unit = onCreatePlaylistClick,
    onMoreFavoritePlaylists: () -> Unit = {},
    onMoreFavoriteAlbums: () -> Unit = {},
    onMoreFavoriteArtists: () -> Unit = {},
    postPagingItems: LazyPagingItems<Post>? = null,
    currentUserId: Long? = null,
    onPostDetail: (Long) -> Unit = {},
    onSharePost: (Post) -> Unit = {},
    onReportPost: (Post) -> Unit = {},
    onDeletePost: (Post) -> Unit = {},
    onEditPost: (Long) -> Unit = {},
) {
    val topPagerState = rememberPagerState { ProfileTopTabs.size }
    val scope = rememberCoroutineScope()
    var actionPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    when {
        !isLogin -> ProfileGuestState(
            modifier = modifier,
            onOpenDrawer = onOpenDrawer,
            onLogin = onLogin,
        )

        state.isLoading && state.user == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.user == null -> ErrorView(message = state.error!!)
        else -> state.user?.let { user ->
            Column(modifier = modifier.fillMaxSize()) {
                ProfileHomeHeader(
                    user = user,
                    songListCount = state.playlists.size,
                    favoriteCount = state.favoriteMusicTotal,
                    listeningMinutes = state.listeningStats?.totalDurationMinutes ?: 0L,
                    onOpenDrawer = onOpenDrawer,
                    onUserInfoClick = onUserHomeClick,
                )
                ProfileUnderlineTabs(
                    tabs = ProfileTopTabs,
                    selectedIndex = topPagerState.currentPage,
                    onSelect = { index -> scope.launch { topPagerState.animateScrollToPage(index) } },
                )

                HorizontalPager(
                    state = topPagerState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> CreatedTab(
                            state = state,
                            onIntent = onIntent,
                            onPlayHistoryClick = onPlayHistoryClick,
                            onCreatePlaylistFromVm = onCreatePlaylistFromVm,
                            onPlaylistMore = { playlist ->
                                actionPlaylist = playlist
                                showManageSheet = true
                            },
                        )

                        1 -> FavoriteTab(
                            state = state,
                            onFavoritePlaylistClick = onFavoritePlaylistClick,
                            onAlbumClick = onAlbumClick,
                            onArtistClick = onArtistClick,
                            onMoreFavoritePlaylists = onMoreFavoritePlaylists,
                            onMoreFavoriteAlbums = onMoreFavoriteAlbums,
                            onMoreFavoriteArtists = onMoreFavoriteArtists,
                        )

                        else -> PostsTab(
                            postPagingItems = postPagingItems,
                            currentUserId = currentUserId,
                            onPostDetail = onPostDetail,
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

    if (showManageSheet) {
        actionPlaylist?.let { playlist ->
            PlaylistManageBottomSheet(
                playlistTitle = playlist.title,
                onDismiss = { showManageSheet = false },
                onEdit = {
                    showManageSheet = false
                    onEditPlaylistClick(playlist.id)
                    actionPlaylist = null
                },
                onDelete = {
                    showManageSheet = false
                    showDeleteConfirm = true
                },
                onPlayAll = {
                    onIntent(ProfileIntent.PlayAllPlaylist(playlist.id))
                    showManageSheet = false
                    actionPlaylist = null
                },
            )
        }
    }

    if (showDeleteConfirm) {
        actionPlaylist?.let { playlist ->
            DeletePlaylistConfirmDialog(
                playlistTitle = playlist.title,
                onDismiss = {
                    showDeleteConfirm = false
                    actionPlaylist = null
                },
                onConfirm = {
                    onIntent(ProfileIntent.DeletePlaylist(playlist.id))
                    showDeleteConfirm = false
                    actionPlaylist = null
                },
            )
        }
    }
}

@Composable
private fun ProfileGuestState(
    modifier: Modifier,
    onOpenDrawer: () -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        ProfileHomeHeader(
            user = null,
            songListCount = 0,
            favoriteCount = 0,
            listeningMinutes = 0L,
            onOpenDrawer = onOpenDrawer,
            onUserInfoClick = onLogin,
            nicknameOverride = "点击登录",
            signatureOverride = "登录后可同步歌单、收藏与个人数据",
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "登录后可同步歌单、收藏与个人数据",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(0.6f)) {
                Text(text = "登录")
            }
        }
    }
}

@Composable
private fun ProfileHomeHeader(
    user: User?,
    songListCount: Int,
    favoriteCount: Int,
    listeningMinutes: Long,
    onOpenDrawer: () -> Unit,
    onUserInfoClick: () -> Unit,
    nicknameOverride: String? = null,
    signatureOverride: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val nickname = nicknameOverride ?: user?.nickname?.takeIf { it.isNotBlank() }
        ?: user?.username ?: "FoxMusic"
    val signature = signatureOverride ?: user?.signature
    val hours = listeningMinutes / 60

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(UserHomeCoverSpec.WIDTH_TO_HEIGHT),
    ) {
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

        ProfileOverlayIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = "更多",
            onClick = onOpenDrawer,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(start = 12.dp, top = 8.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clickable(onClick = onUserInfoClick)
                .padding(bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CachedImage(
                imageUrl = user?.avatar,
                contentDescription = nickname,
                modifier = Modifier
                    .size(80.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .clip(CircleShape),
                shape = CircleShape,
                placeholderIcon = Icons.Filled.Person,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = nickname,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            signature?.takeIf { it.isNotBlank() }?.let { sig ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = sig,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
            }
            if (user != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProfileStatItem(value = songListCount.toString(), label = "歌单")
                    ProfileStatItem(value = favoriteCount.toString(), label = "收藏")
                    ProfileStatItem(value = "${hours}h", label = "听歌")
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.padding(start = 3.dp),
        )
    }
}

@Composable
private fun ProfileOverlayIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
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
private fun ProfileUnderlineTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = selectedIndex == index
            Column(
                modifier = Modifier.clickable { onSelect(index) },
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
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun CreatedTab(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    onPlayHistoryClick: () -> Unit,
    onCreatePlaylistFromVm: () -> Unit,
    onPlaylistMore: (Playlist) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = FoxSpacing.tabChromeBottom,
        ),
    ) {
        item {
            // 「创建歌单」作为分组标题右侧的入口（不再单独占一行）
            SectionHeader(
                title = "我的歌单",
                moreText = "创建歌单",
                moreIcon = Icons.Default.Add,
                iconRotate = 0f,
                onMoreClick = onCreatePlaylistFromVm,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        // 特殊歌单紧随分组标题、位于自建歌单之前，避免被挤到列表最下方。
        item {
            ProfilePlaylistListItem(
                title = "历史播放",
                trackCount = state.playHistoryTotal,
                coverUrl = null,
                placeholderIcon = Icons.Default.History,
                showMoreButton = false,
                onClick = onPlayHistoryClick,
            )
            ProfileListDivider()
        }
        item {
            ProfilePlaylistListItem(
                title = "我喜欢的音乐",
                trackCount = state.favoriteMusicTotal,
                coverUrl = null,
                placeholderIcon = Icons.Default.Favorite,
                showMoreButton = false,
                onClick = { onIntent(ProfileIntent.OnFavoriteTracksClick) },
            )
            ProfileListDivider()
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
        if (state.playlists.isEmpty()) {
            item { ProfileEmptyHint(message = "暂无歌单，点击右上角创建") }
        } else {
            items(state.playlists, key = { it.id }) { playlist ->
                ProfilePlaylistListItem(
                    title = playlist.title,
                    trackCount = playlist.trackCount,
                    coverUrl = playlist.coverImage,
                    placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = { onIntent(ProfileIntent.OnPlaylistClick(playlist)) },
                    onMoreClick = { onPlaylistMore(playlist) },
                )
                ProfileListDivider()
            }
        }
    }
}

@Composable
private fun FavoriteTab(
    state: ProfileState,
    onFavoritePlaylistClick: (Playlist) -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (Long) -> Unit,
    onMoreFavoritePlaylists: () -> Unit,
    onMoreFavoriteAlbums: () -> Unit,
    onMoreFavoriteArtists: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = FoxSpacing.tabChromeBottom,
        ),
    ) {
        item {
            SectionHeader(
                title = "歌单",
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        if (state.favoritePlaylists.isEmpty()) {
            item { ProfileEmptyHint(message = "暂无收藏歌单") }
        } else {
            items(state.favoritePlaylists.take(FavoriteSectionPreviewCount), key = { it.id }) { playlist ->
                ProfilePlaylistListItem(
                    title = playlist.title,
                    trackCount = playlist.trackCount,
                    coverUrl = playlist.coverImage,
                    showMoreButton = false,
                    placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                    onClick = { onFavoritePlaylistClick(playlist) },
                )
                ProfileListDivider()
            }
            if (state.favoritePlaylistTotal > FavoriteSectionPreviewCount) {
                item {
                    ProfileMoreRow(
                        text = "更多歌单",
                        total = state.favoritePlaylistTotal,
                        onClick = onMoreFavoritePlaylists,
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "专辑",
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        if (state.favoriteAlbums.isEmpty()) {
            item { ProfileEmptyHint(message = "暂无收藏专辑") }
        } else {
            items(state.favoriteAlbums.take(FavoriteSectionPreviewCount), key = { it.id }) { album ->
                AlbumListItem(
                    album = album,
                    onClick = { onAlbumClick(album.id) },
                )
            }
            if (state.favoriteAlbumTotal > FavoriteSectionPreviewCount) {
                item {
                    ProfileMoreRow(
                        text = "更多专辑",
                        total = state.favoriteAlbumTotal,
                        onClick = onMoreFavoriteAlbums,
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "艺术家",
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
        }
        if (state.favoriteArtists.isEmpty()) {
            item { ProfileEmptyHint(message = "暂无收藏艺术家") }
        } else {
            items(state.favoriteArtists.take(FavoriteSectionPreviewCount), key = { it.id }) { artist ->
                ArtistListItem(
                    artist = artist,
                    onClick = { onArtistClick(artist.id) },
                )
            }
            if (state.favoriteArtistTotal > FavoriteSectionPreviewCount) {
                item {
                    ProfileMoreRow(
                        text = "更多艺术家",
                        total = state.favoriteArtistTotal,
                        onClick = onMoreFavoriteArtists,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileMoreRow(
    text: String,
    total: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$text ($total)",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
}

@Composable
private fun PostsTab(
    postPagingItems: LazyPagingItems<Post>?,
    currentUserId: Long?,
    onPostDetail: (Long) -> Unit,
    onSharePost: (Post) -> Unit,
    onReportPost: (Post) -> Unit,
    onDeletePost: (Post) -> Unit,
    onEditPost: (Long) -> Unit,
) {
    if (postPagingItems == null) {
        ProfileEmptyHint(message = "暂无动态")
        return
    }
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
            ProfileEmptyHint(message = "暂无动态")
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

@Composable
internal fun ProfilePlaylistListItem(
    title: String,
    trackCount: Int,
    coverUrl: String?,
    placeholderIcon: ImageVector,
    showMoreButton: Boolean = true,
    subtitle: String? = null,
    onClick: () -> Unit,
    onMoreClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(coverUrl = coverUrl, placeholderIcon = placeholderIcon)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: "${trackCount}首歌曲",
                color = MaterialTheme.colorScheme.outline,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showMoreButton) {
            IconButton(onClick = onMoreClick, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaylistCover(
    coverUrl: String?,
    placeholderIcon: ImageVector,
) {
    if (coverUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .size(ProfileCoverSize)
                .clip(ProfileCoverShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = placeholderIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                modifier = Modifier.size(26.dp),
            )
        }
    } else {
        CachedImage(
            imageUrl = coverUrl,
            contentDescription = null,
            modifier = Modifier.size(ProfileCoverSize),
            shape = ProfileCoverShape,
            placeholderIcon = placeholderIcon,
        )
    }
}

@Composable
private fun ProfileListDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.5.dp,
    )
}

@Composable
private fun ProfileEmptyHint(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 14.sp,
        )
    }
}
