package com.fox.music.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import kotlin.math.roundToInt
import com.fox.music.core.common.R
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.PlaylistDetail
import com.fox.music.core.model.music.RepeatMode

enum class CarModeTab {
    Recommend,
    Search,
}

@Composable
fun CarModeScreen(
    selectedTab: CarModeTab,
    recommendedPlaylists: List<Playlist>,
    recommendedSongs: List<Music>,
    hotKeywords: List<HotKeyword>,
    searchQuery: String,
    searchResults: List<Music>,
    isSearching: Boolean,
    playlistDetail: PlaylistDetail?,
    isPlaylistLoading: Boolean,
    isLoading: Boolean,
    error: String?,
    playerState: PlayerState,
    positionMs: Long,
    onTabSelected: (CarModeTab) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Music, List<Music>) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onHotKeywordClick: (String) -> Unit,
    onBackFromPlaylist: () -> Unit,
    onExit: () -> Unit,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPreviewMode: Boolean = false,
) {
    var showNowPlaying by remember { mutableStateOf(false) }
    var overlayActive by remember { mutableStateOf(false) }
    val slideOffset = remember { Animatable(1f) }
    val currentMusic = playerState.currentMusic

    LaunchedEffect(showNowPlaying) {
        if (showNowPlaying) {
            overlayActive = true
            slideOffset.snapTo(1f)
            slideOffset.animateTo(0f, tween(280))
        } else if (overlayActive) {
            slideOffset.animateTo(1f, tween(280))
            overlayActive = false
        }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF101418)),
    ) {
        CarModeSidebar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                showNowPlaying = false
                onTabSelected(tab)
            },
            onExit = onExit,
            showExitButton = isPreviewMode,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    when {
                        playlistDetail != null -> CarModePlaylistContent(
                            playlistDetail = playlistDetail,
                            isLoading = isPlaylistLoading,
                            error = error,
                            onBack = onBackFromPlaylist,
                            onSongClick = onSongClick,
                        )
                        selectedTab == CarModeTab.Recommend -> CarModeRecommendContent(
                            playlists = recommendedPlaylists,
                            songs = recommendedSongs,
                            isLoading = isLoading,
                            error = error,
                            onPlaylistClick = onPlaylistClick,
                            onSongClick = onSongClick,
                        )
                        else -> CarModeSearchContent(
                            searchQuery = searchQuery,
                            hotKeywords = hotKeywords,
                            searchResults = searchResults,
                            isSearching = isSearching,
                            error = error,
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchSubmit = onSearchSubmit,
                            onHotKeywordClick = onHotKeywordClick,
                            onSongClick = onSongClick,
                        )
                    }
                }
                if (!showNowPlaying) {
                    CarModeMiniPlayerBar(
                        playerState = playerState,
                        positionMs = positionMs,
                        onOpenNowPlaying = {
                            if (currentMusic != null) {
                                showNowPlaying = true
                            }
                        },
                        onRepeatClick = onRepeatClick,
                        onPreviousClick = onPreviousClick,
                        onTogglePlayClick = onTogglePlayClick,
                        onNextClick = onNextClick,
                    )
                }
            }
            if (overlayActive && currentMusic != null) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(1f),
                ) {
                    val heightPx = with(LocalDensity.current) { maxHeight.toPx() }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset {
                                IntOffset(0, (slideOffset.value * heightPx).roundToInt())
                            }
                            .background(Color(0xFF101418)),
                    ) {
                        CarModeNowPlayingPage(
                            music = currentMusic,
                            playerState = playerState,
                            positionMs = positionMs,
                            onDismiss = { showNowPlaying = false },
                            onRepeatClick = onRepeatClick,
                            onPreviousClick = onPreviousClick,
                            onTogglePlayClick = onTogglePlayClick,
                            onNextClick = onNextClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CarModeSidebar(
    selectedTab: CarModeTab,
    onTabSelected: (CarModeTab) -> Unit,
    onExit: () -> Unit,
    showExitButton: Boolean,
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .fillMaxHeight()
            .background(Color(0xFF0B0E12))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "FoxMusic",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            if (showExitButton) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.Close, contentDescription = "退出", tint = Color.White.copy(0.7f))
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        CarModeNavItem(
            title = "推荐",
            selected = selectedTab == CarModeTab.Recommend,
            onClick = { onTabSelected(CarModeTab.Recommend) },
        )
        CarModeNavItem(
            title = "搜索",
            selected = selectedTab == CarModeTab.Search,
            onClick = { onTabSelected(CarModeTab.Search) },
        )
    }
}

@Composable
private fun CarModeMiniPlayerBar(
    playerState: PlayerState,
    positionMs: Long,
    onOpenNowPlaying: () -> Unit,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val music = playerState.currentMusic
    Surface(color = Color(0xFF171B22)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (music != null) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenNowPlaying)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        shape = RoundedCornerShape(8.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = musicTitleArtist(music),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = music.currentLyricText(positionMs),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = "未在播放",
                    modifier = Modifier.weight(1f),
                    color = Color.White.copy(alpha = 0.45f),
                )
            }
            CarModePlaybackControls(
                playerState = playerState,
                onRepeatClick = onRepeatClick,
                onPreviousClick = onPreviousClick,
                onTogglePlayClick = onTogglePlayClick,
                onNextClick = onNextClick,
                showRepeat = false,
            )
        }
    }
}

@Composable
private fun CarModeBackButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun CarModeTopBar(
    onBack: () -> Unit,
    backContentDescription: String,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = true,
    title: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(Color(0xFF101418))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CarModeBackButton(
            onClick = onBack,
            contentDescription = backContentDescription,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (centerTitle) 0.dp else 4.dp),
            contentAlignment = if (centerTitle) Alignment.Center else Alignment.CenterStart,
        ) {
            title()
        }
        if (centerTitle) {
            Spacer(modifier = Modifier.width(72.dp))
        }
    }
}

@Composable
private fun CarModeNowPlayingPage(
    music: Music,
    playerState: PlayerState,
    positionMs: Long,
    onDismiss: () -> Unit,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101418)),
    ) {
        CarModeTopBar(
            onBack = onDismiss,
            backContentDescription = "退出正在播放",
        ) {
            Text(
                text = "正在播放",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RectangleShape)
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CachedImage(
                        imageUrl = music.coverImage,
                        contentDescription = music.title,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp)),
                        shape = RoundedCornerShape(18.dp),
                    )
                }
                Text(
                    text = music.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(top = 12.dp),
                )
                Text(
                    text = music.artists.joinToString { it.name },
                    color = Color.White.copy(alpha = 0.62f),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .padding(top = 4.dp),
                )
                CarModePlaybackControls(
                    playerState = playerState,
                    onRepeatClick = onRepeatClick,
                    onPreviousClick = onPreviousClick,
                    onTogglePlayClick = onTogglePlayClick,
                    onNextClick = onNextClick,
                    showRepeat = true,
                    large = true,
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 8.dp),
                )
            }
            CarLyricLines(
                music = music,
                positionMs = positionMs,
                maxVisibleLines = 8,
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun CarModePlaybackControls(
    playerState: PlayerState,
    onRepeatClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onTogglePlayClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    showRepeat: Boolean = false,
    large: Boolean = false,
) {
    val barWidth = if (large) 360.dp else 280.dp
    val barHeight = if (large) 96.dp else 72.dp
    val playButtonSize = if (large) 72.dp else 56.dp
    val playIconSize = if (large) 48.dp else 36.dp
    val transportButtonSize = if (large) 64.dp else 52.dp
    val transportIconSize = if (large) 40.dp else 32.dp
    val modeButtonSize = if (large) 56.dp else 48.dp
    val modeIconSize = if (large) 32.dp else 26.dp

    Row(
        modifier = modifier
            .width(barWidth)
            .height(barHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showRepeat) {
            CarModeControlButton(
                buttonSize = modeButtonSize,
                onClick = onRepeatClick,
            ) {
                Icon(
                    painter = painterResource(
                        when (playerState.repeatMode) {
                            RepeatMode.SEQUENTIAL -> R.drawable.ic_sequential
                            RepeatMode.ALL -> R.drawable.ic_cycle
                            RepeatMode.ONE -> R.drawable.ic_single
                            RepeatMode.RANDOM -> R.drawable.ic_random
                        },
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(modeIconSize),
                    tint = Color.White,
                )
            }
        }
        CarModeControlButton(
            buttonSize = transportButtonSize,
            onClick = onPreviousClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_previous),
                contentDescription = null,
                modifier = Modifier.size(transportIconSize),
                tint = Color.White,
            )
        }
        CarModeControlButton(
            buttonSize = playButtonSize,
            onClick = onTogglePlayClick,
        ) {
            if (playerState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(playIconSize),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = painterResource(
                        if (playerState.isPlaying) R.drawable.iv_pause else R.drawable.iv_play,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(playIconSize),
                    tint = Color.White,
                )
            }
        }
        CarModeControlButton(
            buttonSize = transportButtonSize,
            onClick = onNextClick,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_next),
                contentDescription = null,
                modifier = Modifier.size(transportIconSize),
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun CarModeControlButton(
    buttonSize: Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(buttonSize)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun musicTitleArtist(music: Music): String =
    "${music.title}-${music.artists.joinToString { it.name }}"

private fun Music.currentLyricText(positionMs: Long): String =
    getCurrentLyric(positionMs)?.takeIf { it.isNotBlank() }
        ?: if (lyricLines.isEmpty()) "暂无歌词" else ""

@Composable
private fun CarModeNavItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent,
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
            fontSize = 17.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CarModeRecommendContent(
    playlists: List<Playlist>,
    songs: List<Music>,
    isLoading: Boolean,
    error: String?,
    onPlaylistClick: (Playlist) -> Unit,
    onSongClick: (Music, List<Music>) -> Unit,
) {
    if (isLoading && playlists.isEmpty() && songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("推荐歌单", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                playlists.forEach { playlist ->
                    CarModePlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
            }
        }
        item {
            Text("推荐歌曲", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        if (error != null && songs.isEmpty()) {
            item { Text(error, color = Color.White.copy(alpha = 0.5f)) }
        }
        items(songs, key = { it.id }) { music ->
            CarModeSongRow(music = music, onClick = { onSongClick(music, songs) })
        }
    }
}

@Composable
private fun CarModePlaylistContent(
    playlistDetail: PlaylistDetail,
    isLoading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onSongClick: (Music, List<Music>) -> Unit,
) {
    val tracks = playlistDetail.tracks.list
    val playlist = playlistDetail.playlist
    Column(modifier = Modifier.fillMaxSize()) {
        CarModeTopBar(
            onBack = onBack,
            backContentDescription = "返回",
            centerTitle = false,
        ) {
            Column {
                Text(
                    text = playlist.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        isLoading -> "加载中..."
                        tracks.isNotEmpty() -> "${tracks.size} 首歌曲"
                        playlist.trackCount > 0 -> "共 ${playlist.trackCount} 首"
                        else -> "暂无歌曲"
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                )
            }
        }
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            error != null && tracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(error, color = Color.White.copy(alpha = 0.5f))
                }
            }
            tracks.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("暂无歌曲", color = Color.White.copy(alpha = 0.5f))
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(tracks, key = { it.id }) { music ->
                        CarModeSongRow(music = music, onClick = { onSongClick(music, tracks) })
                    }
                }
            }
        }
    }
}

@Composable
private fun CarModeSearchContent(
    searchQuery: String,
    hotKeywords: List<HotKeyword>,
    searchResults: List<Music>,
    isSearching: Boolean,
    error: String?,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onHotKeywordClick: (String) -> Unit,
    onSongClick: (Music, List<Music>) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("搜索音乐", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        item {
            CarModeSearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                onSubmit = onSearchSubmit,
            )
        }
        if (hotKeywords.isNotEmpty()) {
            item {
                Text("热门搜索", color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    hotKeywords.forEach { keyword ->
                        FilterChip(
                            selected = false,
                            onClick = { onHotKeywordClick(keyword.keyword) },
                            label = { Text(keyword.keyword) },
                        )
                    }
                }
            }
        }
        if (isSearching) {
            item {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        if (error != null && searchResults.isEmpty()) {
            item { Text(error, color = Color.White.copy(alpha = 0.5f)) }
        }
        items(searchResults, key = { it.id }) { music ->
            CarModeSongRow(music = music, onClick = { onSongClick(music, searchResults) })
        }
    }
}

@Composable
private fun CarModeSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White.copy(0.6f))
            Spacer(modifier = Modifier.width(10.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text("搜索歌曲、歌手", color = Color.White.copy(alpha = 0.4f))
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun CarModePlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick),
    ) {
        CachedImage(
            imageUrl = playlist.coverImage,
            contentDescription = playlist.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            placeholderIcon = Icons.Filled.QueueMusic,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.trackCount} 首",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CarModeSongRow(
    music: Music,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                color = Color.White,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString { it.name },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
