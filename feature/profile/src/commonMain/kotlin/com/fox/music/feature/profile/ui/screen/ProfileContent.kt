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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.user.User
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.DeletePlaylistConfirmDialog
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PlaylistManageBottomSheet
import com.fox.music.core.ui.component.PostCard
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.CoverShapeHero
import com.fox.music.core.ui.theme.PaperHairline
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileState
import kotlinx.coroutines.launch

const val PROFILE_ROUTE = "profile"

private val ProfileTopTabs = listOf("音乐", "收藏", "动态")
private val FavoriteSectionPreviewCount = 3
private val ProfileCoverShape = RoundedCornerShape(12.dp)
private val ProfileCoverSize = 52.dp

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
    /** 动态里的标签点击：跳搜索结果页（tab=POST, tag=xxx）。 */
    onTagClick: (String) -> Unit = {},
    /** 动态里的音乐条播放：由宿主决定「换曲播放」还是「继续播」。 */
    onPlayPostMusic: (Post) -> Unit = {},
    onNavigateToPlayer: () -> Unit = {},
    playerState: PlayerState = PlayerState(),
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

        // 静默加载：有快照（内存 / 磁盘）就直接渲染，网络刷新在后台完成、回来整体替换。
        // 用户明确要求「回到我的页不要 loading」，所以这里不再出现整屏转圈；
        // 只有「既没有用户信息、又确实失败」时才报错，其余情况短暂留白即可。
        state.user == null && state.error != null -> ErrorView(message = state.error)
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

                // 后台刷新期间不再显示细进度条（静默加载），数据回来直接替换即可。
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
                            onUserHomeClick = onUserHomeClick,
                            onTagClick = onTagClick,
                            onPlayPostMusic = onPlayPostMusic,
                            onNavigateToPlayer = onNavigateToPlayer,
                            playerState = playerState,
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

/**
 * 未登录态：不再用「大封面 + 白字」的渐变头部（浅底渐变上白字只有约 1.6:1，几乎读不出来），
 * 改成纸感留白页：左上角同一颗圆形图标按钮保持入口一致，中间一枚登录卡片，
 * 「登录后可同步…」只说一次（旧版头部与正文各说一次）。
 */
@Composable
private fun ProfileGuestState(
    modifier: Modifier,
    onOpenDrawer: () -> Unit,
    onLogin: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = FoxSpacing.pagePadding, end = FoxSpacing.pagePadding, top = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            ProfileCircleIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "更多",
                onClick = onOpenDrawer,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(32.dp),
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "登录 FoxMusic",
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = "同步歌单、收藏与听歌记录",
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = scheme.primary,
                    contentColor = scheme.onPrimary,
                ),
                elevation = null,
            ) {
                Text(text = "登录 / 注册", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        // 让开底部迷你条 + 浮岛底栏，但不必留满 220dp（那是给列表滚到底用的）
        Spacer(modifier = Modifier.height(150.dp))
    }
}

/**
 * 已登录头部：纸感排版，不再铺横向渐变 + 白字。
 *
 * 旧版是一块 aspectRatio 封面（约 1/3 屏高）承载头像与白字昵称，浅色渐变上白字对比度只有 1.6:1，
 * 同时把三个统计数字压到封面底部。新版把"身份"交给一张横向留白：头像 64dp + 昵称 + 签名，
 * 统计改用 tabular 数字排在下一行，视觉重心回到内容而不是色块。
 */
@Composable
private fun ProfileHomeHeader(
    user: User,
    songListCount: Int,
    favoriteCount: Int,
    listeningMinutes: Long,
    onOpenDrawer: () -> Unit,
    onUserInfoClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val nickname = user.nickname?.takeIf { it.isNotBlank() } ?: user.username
    val hours = listeningMinutes / 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = FoxSpacing.pagePadding,
                end = FoxSpacing.pagePadding,
                top = 8.dp,
            ),
    ) {
        // 抽屉入口与身份信息同一行（右上角），与首页头像入口同位——
        // 旧版 ☰ 独占一行挂在左上，把整个头部往下顶了一截，视觉重心散。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(CoverShapeHero)
                    .clickable(onClick = onUserInfoClick)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = user.avatar,
                    contentDescription = nickname,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    shape = CircleShape,
                    placeholderIcon = Icons.Filled.Person,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = nickname,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 21.sp,
                            lineHeight = 26.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    user.signature?.takeIf { it.isNotBlank() }?.let { sig ->
                        Text(
                            text = sig,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                lineHeight = 16.sp,
                            ),
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 5.dp),
                        )
                    }
                }
            }
            ProfileCircleIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = "更多",
                onClick = onOpenDrawer,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            ProfileStatItem(value = songListCount.toString(), label = "歌单")
            ProfileStatItem(value = favoriteCount.toString(), label = "收藏")
            ProfileStatItem(value = "${hours}h", label = "听歌")
        }
        Spacer(modifier = Modifier.height(14.dp))
    }
}

@Composable
private fun ProfileStatItem(value: String, label: String) {
    val scheme = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = "tnum",
            ),
            color = scheme.onSurface,
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
    }
}

/** 全站统一的圆形图标按钮：surface 底 + 1dp 发丝描边，替代旧版 35% 黑圆底。 */
@Composable
private fun ProfileCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(scheme.surface)
            .border(
                width = 1.dp,
                color = scheme.onSurface.copy(alpha = 0.07f),
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = scheme.onSurface,
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun ProfileUnderlineTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = FoxSpacing.pagePadding),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = selectedIndex == index
                Column(
                    modifier = Modifier.clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        fontSize = 14.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .width(if (selected) 18.dp else 0.dp)
                            .background(scheme.primary, shape = RoundedCornerShape(2.dp)),
                    )
                }
            }
        }
        HorizontalDivider(
            color = scheme.onSurface.copy(alpha = 0.07f),
            thickness = 1.dp,
        )
    }
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
            start = FoxSpacing.pagePadding,
            end = FoxSpacing.pagePadding,
            top = 2.dp,
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
            start = FoxSpacing.pagePadding,
            end = FoxSpacing.pagePadding,
            top = 2.dp,
            bottom = FoxSpacing.tabChromeBottom,
        ),
    ) {
        item {
            SectionHeader(title = "歌单")
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
            SectionHeader(title = "专辑")
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
            SectionHeader(title = "艺术家")
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
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
    HorizontalDivider(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        thickness = 1.dp,
    )
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
    onUserHomeClick: () -> Unit,
    onTagClick: (String) -> Unit,
    onPlayPostMusic: (Post) -> Unit,
    onNavigateToPlayer: () -> Unit,
    playerState: PlayerState,
) {
    if (postPagingItems == null) {
        ProfileEmptyHint(message = "暂无动态")
        return
    }

    // 图片全屏预览（与社区广场同一套 PostMediaPreviewDialog）
    var previewMedia by remember { mutableStateOf<List<PostMedia>?>(null) }
    var previewIndex by remember { mutableStateOf(0) }

    previewMedia?.let { media ->
        PostMediaPreviewDialog(
            media = media,
            initialIndex = previewIndex,
            onDismiss = { previewMedia = null },
        )
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
                    // 「我的」动态全是自己的帖子，所以点头像/昵称一律进自己的用户主页
                    PostCard(
                        post = post,
                        isOwnPost = post.userId == currentUserId,
                        onPostClick = { onPostDetail(post.id) },
                        onLikeClick = { },
                        onCommentClick = { onPostDetail(post.id) },
                        onShareClick = { onSharePost(post) },
                        onUserClick = onUserHomeClick,
                        onEditClick = { onEditPost(post.id) },
                        onDeleteClick = { onDeletePost(post) },
                        onReportClick = { onReportPost(post) },
                        onImageClick = { imageIndex ->
                            val media = post.displayMedia
                            if (media.isNotEmpty()) {
                                previewMedia = media
                                previewIndex = imageIndex.coerceIn(0, media.lastIndex)
                            }
                        },
                        onTagClick = onTagClick,
                        onMusicPlayClick = { onPlayPostMusic(post) },
                        onMusicRowClick = {
                            onPlayPostMusic(post)
                            onNavigateToPlayer()
                        },
                        isCurrentTrack = playerState.currentMusic?.id != null &&
                            post.music?.id == playerState.currentMusic?.id,
                        isPlaying = playerState.currentMusic?.id != null &&
                            post.music?.id == playerState.currentMusic?.id &&
                            playerState.isPlaying,
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
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlaylistCover(coverUrl = coverUrl, placeholderIcon = placeholderIcon)
        Spacer(modifier = Modifier.width(13.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: "${trackCount} 首歌曲",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                ),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showMoreButton) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
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
    // 与首页同一规则：hairline 从文字轴起（24 + 52 封面 + 13 间距），不到通栏
    HorizontalDivider(
        color = PaperHairline,
        thickness = 1.dp,
        modifier = Modifier.padding(start = 89.dp),
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}
