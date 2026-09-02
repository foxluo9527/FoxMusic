package com.fox.music.feature.profile.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fox.music.core.common.R as CommonR
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.ListeningStats
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.AlbumListItem
import com.fox.music.core.ui.component.ArtistListItem
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.DeletePlaylistConfirmDialog
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PlaylistManageBottomSheet
import com.fox.music.core.ui.component.SectionHeader
import com.fox.music.core.ui.theme.FoxSpacing
import com.fox.music.feature.profile.viewmodel.ProfileEffect
import com.fox.music.feature.profile.viewmodel.ProfileIntent
import com.fox.music.feature.profile.viewmodel.ProfileViewModel
import kotlinx.coroutines.launch

const val PROFILE_ROUTE = "profile"


private val ProfileCardShape = RoundedCornerShape(20.dp)
private val ProfileCoverShape = RoundedCornerShape(12.dp)

private val ProfileCoverSize = 56.dp

private val ProfileTabs = listOf("我的", "歌单", "专辑", "艺术家")
/** 避开悬浮 MiniPlayer + 底栏 */
private val ProfileTabListContentPadding = PaddingValues(bottom = FoxSpacing.tabChromeBottom)

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier.fillMaxSize(),
    viewModel: ProfileViewModel = hiltViewModel(),
    isLogin: Boolean,
    onLogin: () -> Unit = {},
    onPlaylistClick: (Long) -> Unit = {},
    onFavoritePlaylistClick: (Playlist) -> Unit = {},
    onAlbumClick: (Long) -> Unit = {},
    onFavoriteTracksClick: () -> Unit = {},
    onPlayHistoryClick: () -> Unit = {},
    onCreatePlaylistClick: () -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onUserHomeClick: () -> Unit = {},
    onDownloadManagerClick: () -> Unit = {},
    onPlayAllPlaylist: (Long, List<Music>, String, String?) -> Unit = { _, _, _, _ -> },
    onEditPlaylistClick: (Long) -> Unit = {},
    onMusicClick: (Music, List<Music>) -> Unit = { _, _ -> },
    refreshKey: Int = 0,
) {
    val state by viewModel.uiState.collectAsState()
    val collectState = rememberPagerState { ProfileTabs.size }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var actionPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showManageSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isLogin) {
        if (isLogin) {
            viewModel.sendIntent(ProfileIntent.Load)
        }
    }

    LaunchedEffect(refreshKey) {
        if (isLogin && refreshKey > 0) {
            viewModel.refresh()
        }
    }

    // 切回「我的」Tab / 从子页返回时，轻量刷新听歌时长与历史播放数量
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isLogin) {
        if (!isLogin) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.sendIntent(ProfileIntent.RefreshListeningSnapshot)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                ProfileEffect.NavigateToLogin -> onLogin()
                is ProfileEffect.NavigateToPlaylist -> onPlaylistClick(effect.playlistId)
                ProfileEffect.NavigateToFavoriteTracks -> onFavoriteTracksClick()
                ProfileEffect.NavigateToCreatePlaylist -> onCreatePlaylistClick()
                ProfileEffect.NavigateToSettings -> onSettingsClick()
                is ProfileEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is ProfileEffect.PlayAllPlaylistTracks -> {
                    onPlayAllPlaylist(
                        effect.playlistId,
                        effect.musicList,
                        effect.title,
                        effect.coverUrl,
                    )
                }
            }
        }
    }

    when {
        !isLogin -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfilePageTopTitle(
                    onSettingsClick = onLogin,
                )
                ProfileHeaderCard(
                    nickname = stringResource(CommonR.string.app_name),
                    signature = "登录",
                    avatarUrl = null,
                    showFavoriteIcon = false,
                    onUserInfoClick = onLogin,
                )
                ProfileGuestLoginPrompt(onLogin = onLogin)
            }
        }

        state.isLoading && state.user == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.user == null -> ErrorView(message = state.error!!)
        else -> state.user?.let { user ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfilePageTopTitle(
                    onSettingsClick = onSettingsClick,
                )
                ProfileHeaderCard(
                    nickname = user.nickname ?: user.username,
                    signature = user.signature,
                    avatarUrl = user.avatar,
                    onUserInfoClick = onUserHomeClick,
                )
                ProfileTabRow(
                    selectedTab = collectState.currentPage,
                    onTabSelected = { index ->
                        scope.launch { collectState.animateScrollToPage(index) }
                    },
                )

                HorizontalPager(
                    state = collectState,
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> {
                            LazyColumn(contentPadding = ProfileTabListContentPadding) {
                                item {
                                    state.listeningStats?.let { stats ->
                                        ListeningStatsCard(stats = stats)
                                    }
                                }

                                item {
                                    SectionHeader(
                                        title = "我的歌单",
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                        moreText = "创建歌单",
                                        moreIcon = Icons.Default.Add,
                                        onMoreClick = { viewModel.onCreatePlaylistClick() },
                                    )
                                }

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
                                        title = "我的收藏",
                                        trackCount = state.favoriteMusicTotal,
                                        coverUrl = null,
                                        placeholderIcon = Icons.Default.Favorite,
                                        showMoreButton = false,
                                        onClick = {
                                            viewModel.sendIntent(ProfileIntent.OnFavoriteTracksClick)
                                        },
                                    )
                                    ProfileListDivider()
                                }

                                item {
                                    ProfilePlaylistListItem(
                                        title = "下载管理",
                                        trackCount = state.downloadedMusicTotal,
                                        subtitle = if (state.downloadedMusicTotal > 0) {
                                            null
                                        } else {
                                            "查看与管理已下载歌曲"
                                        },
                                        coverUrl = null,
                                        placeholderIcon = Icons.Default.Download,
                                        showMoreButton = false,
                                        onClick = onDownloadManagerClick,
                                    )
                                    ProfileListDivider()
                                }

                                items(state.playlists, key = { it.id }) { playlist ->
                                    ProfilePlaylistListItem(
                                        title = playlist.title,
                                        trackCount = playlist.trackCount,
                                        coverUrl = playlist.coverImage,
                                        placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                                        onClick = {
                                            viewModel.sendIntent(
                                                ProfileIntent.OnPlaylistClick(playlist),
                                            )
                                        },
                                        onMoreClick = {
                                            actionPlaylist = playlist
                                            showManageSheet = true
                                        },
                                    )
                                    ProfileListDivider()
                                }
                            }
                        }

                        1 -> {
                            LazyColumn(contentPadding = ProfileTabListContentPadding) {
                                item {
                                    SectionHeader(
                                        title = "收藏的歌单",
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    )
                                }
                                if (state.favoritePlaylists.isNotEmpty()) {
                                    items(state.favoritePlaylists, key = { it.id }) { playlist ->
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
                                } else {
                                    item {
                                        ProfileEmptyHint(message = "暂无任何收藏歌单")
                                    }
                                }
                            }
                        }

                        2 -> {
                            LazyColumn(contentPadding = ProfileTabListContentPadding) {
                                item {
                                    SectionHeader(
                                        title = "收藏的专辑",
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    )
                                }
                                if (state.favoriteAlbums.isNotEmpty()) {
                                    items(state.favoriteAlbums, key = { it.id }) { album ->
                                        AlbumListItem(
                                            album = album,
                                            onClick = { onAlbumClick(album.id) },
                                        )
                                    }
                                } else {
                                    item {
                                        ProfileEmptyHint(message = "暂无任何收藏专辑")
                                    }
                                }
                            }
                        }

                        3 -> {
                            LazyColumn(contentPadding = ProfileTabListContentPadding) {
                                item {
                                    SectionHeader(
                                        title = "收藏的艺术家",
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                                    )
                                }
                                if (state.favoriteArtists.isNotEmpty()) {
                                    items(state.favoriteArtists, key = { it.id }) { artist ->
                                        ArtistListItem(
                                            artist = artist,
                                            onClick = { onArtistClick(artist.id) },
                                        )
                                    }
                                } else {
                                    item {
                                        ProfileEmptyHint(message = "暂无任何收藏艺术家")
                                    }
                                }
                            }
                        }
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
                    viewModel.sendIntent(ProfileIntent.PlayAllPlaylist(playlist.id))
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
                    viewModel.sendIntent(ProfileIntent.DeletePlaylist(playlist.id))
                    showDeleteConfirm = false
                    actionPlaylist = null
                },
            )
        }
    }
}

@Composable
private fun ProfilePageTopTitle(
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "设置",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ColumnScope.ProfileGuestLoginPrompt(onLogin: () -> Unit) {
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
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth(0.6f),
        ) {
            Text(text = "登录")
        }
    }
}

@Composable
private fun ProfileHeaderCard(
    nickname: String,
    signature: String?,
    avatarUrl: String?,
    showFavoriteIcon: Boolean = true,
    onUserInfoClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        shape = ProfileCardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onUserInfoClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = avatarUrl,
                    contentDescription = nickname,
                    modifier = Modifier
                        .size(88.dp)
                        .shadow(4.dp, CircleShape, clip = false)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape)
                        .clip(CircleShape),
                    shape = CircleShape,
                    placeholderIcon = Icons.Filled.Person,
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = nickname,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (showFavoriteIcon) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(18.dp),
                            )
                        }
                    }
                    signature?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "设置",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(24.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun VerticalStatDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun ProfileStatItem(
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "statScale",
    )

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = count.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ProfileTabRow(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        ProfileTabs.forEachIndexed { index, title ->
            ProfileTab(
                title = title,
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ProfileTab(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var indicatorWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            onTextLayout = { result ->
                indicatorWidth = with(density) { result.size.width.toDp() }
            },
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(if (selected) indicatorWidth.coerceAtLeast(20.dp) else 0.dp)
                .background(
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

@Composable
private fun ListeningStatsCard(stats: ListeningStats) {
    val scheme = MaterialTheme.colorScheme
    val totalMinutes = stats.totalDurationMinutes
    val hours = totalMinutes / 60
    val remainMinutes = totalMinutes % 60
    val topArtistsLabel = stats.topArtists
        .take(3)
        .joinToString(" · ") { it.name }
        .takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(ProfileCardShape)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.22f),
                        scheme.secondary.copy(alpha = 0.12f),
                        scheme.surfaceVariant.copy(alpha = 0.6f),
                    ),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "本周听歌",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (hours > 0) {
                    Text(
                        text = hours.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                    Text(
                        text = "小时",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                    if (remainMinutes > 0) {
                        Text(
                            text = remainMinutes.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = scheme.primary,
                        )
                        Text(
                            text = "分",
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.primary.copy(alpha = 0.85f),
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                } else {
                    Text(
                        text = totalMinutes.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.primary,
                    )
                    Text(
                        text = "分钟",
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.primary.copy(alpha = 0.85f),
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
            }
        }
        if (topArtistsLabel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "常听 $topArtistsLabel",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProfilePlaylistListItem(
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
        PlaylistCover(
            coverUrl = coverUrl,
            placeholderIcon = placeholderIcon,
        )
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
            IconButton(
                onClick = onMoreClick,
                modifier = Modifier.size(36.dp),
            ) {
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
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 14.sp,
        )
    }
}
