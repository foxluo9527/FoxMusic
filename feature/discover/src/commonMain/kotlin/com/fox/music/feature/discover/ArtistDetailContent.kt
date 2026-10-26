package com.fox.music.feature.discover

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.DetailIntroContent
import com.fox.music.core.ui.component.DetailIntroData
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarIconAction
import com.fox.music.core.ui.component.FoxTopBarSpec
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.core.ui.component.MusicListItem
import com.fox.music.core.ui.component.JumpToPlayingFabForList
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.LocalNowPlayingMusicId
import com.fox.music.core.ui.component.SectionHeader
import kotlinx.coroutines.launch

/**
 * 歌手详情页纯界面。
 *
 * Toast 不在这里弹——[ArtistDetailEffect.ShowToast] 由 Android 外壳用 LocalContext 处理，
 * 这样这块 UI 才能进 commonMain（LocalContext 是 Android 专属）。
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun ArtistDetailContent(
    state: ArtistDetailState,
    onIntent: (ArtistDetailIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onMusicClick: (Music) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onBack: () -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    onViewAllArtistSongs: (Long) -> Unit = {},
    currentMusicId: Long? = null,
) {
    val artist = state.artist
    // 显式传入优先，其次读全局「正在播放」（app 层在 NavHost 外层统一 provide）
    val nowPlayingId = currentMusicId ?: LocalNowPlayingMusicId.current
    var showIntro by rememberSaveable { mutableStateOf(false) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    // 预览兜底链：库里存在「avatar 指向的 OSS 文件已失效、coverImage 仍可显示」的艺人
    // （实测 190+ 位）。CachedImage 有 errorImageUrl 兜底所以列表里看不出异常，
    // 长按预览只传 avatar 就是纯黑，这里补上第二图源。
    var previewFallbackUrls by remember { mutableStateOf<List<String>>(emptyList()) }

    previewImageUrl?.let { url ->
        PostImagePreviewDialog(
            images = listOf(url),
            initialIndex = 0,
            onDismiss = { previewImageUrl = null },
            fallbackImages = previewFallbackUrls,
        )
    }

    // 点头部（头像、名称、简介）进入介绍详情页，完整展示歌手简介与资料
    if (showIntro && artist != null) {
        BackHandler { showIntro = false }
        DetailIntroContent(
            data = artist.toDetailIntroData(),
            onBack = { showIntro = false },
            modifier = modifier,
        )
        return
    }

    val playlistKey = "artist_detail/${artist?.id ?: 0}"
    val listState = rememberLazyListState()
    var headerHeightPx by remember { mutableStateOf(0f) }
    // 长按看图：主图（avatar）失败时自动切封面，与列表 CachedImage 的兜底语义一致
    val openArtistPreview: (String?) -> Unit = { url ->
        val main = url?.takeIf { it.isNotBlank() }
        if (main != null) {
            previewImageUrl = main
            previewFallbackUrls = listOfNotNull(
                artist?.coverImage?.takeIf { it.isNotBlank() && it != main },
            )
        }
    }
    val showCollapsedTitle by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > headerHeightPx
        }
    }

    LaunchedEffect(state.hotMusics, artist?.id) {
        if (state.hotMusics.isNotEmpty()) {
            updateMusicList(state.hotMusics, playlistKey)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                showBackButton = true,
                onBackClick = onBack,
                titleContent = {
                    if (artist != null && showCollapsedTitle) {
                        Row(
                            Modifier
                                .height(60.dp)
                                .clickable { showIntro = true },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CachedImage(
                                imageUrl = artist.avatar,
                                errorImageUrl = artist.coverImage,
                                contentDescription = artist.name,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = { showIntro = true },
                                    onLongClick = {
                                        openArtistPreview(artist.avatar ?: artist.coverImage)
                                    },
                                ),
                                placeholderIcon = Icons.Filled.Person,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = FoxTopBarSpec.TitleSize,
                                    lineHeight = FoxTopBarSpec.TitleLineHeight,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    if (artist != null && showCollapsedTitle) {
                        FoxTopBarIconAction(
                            icon = if (artist.isFavorite) {
                                Icons.Filled.Favorite
                            } else {
                                Icons.Filled.FavoriteBorder
                            },
                            contentDescription = "收藏",
                            onClick = { onIntent(ArtistDetailIntent.ToggleFavorite) },
                            enabled = !state.isFavoriteLoading,
                            tint = if (artist.isFavorite) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                },
            )
        },
        // 底色由外层主题渐变提供，透明才能看到顶光。
        containerColor = Color.Transparent,
    ) { innerPadding ->
        when {
            state.isLoading && artist == null -> {
                LoadingIndicator(useLottie = false)
            }

            state.error != null && artist == null -> {
                ErrorView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    message = state.error ?: "加载失败",
                    onRetry = { onIntent(ArtistDetailIntent.Load) },
                )
            }

            artist != null -> {
                ArtistDetailBody(
                    artist = artist,
                    hotMusics = state.hotMusics,
                    albums = state.albums,
                    listState = listState,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    onMusicClick = onMusicClick,
                    onAlbumClick = onAlbumClick,
                    onMusicMoreClick = onMusicMoreClick,
                    onFavoriteClick = {
                        onIntent(ArtistDetailIntent.ToggleFavorite)
                    },
                    onHeaderClick = { showIntro = true },
                    onCoverLongClick = { openArtistPreview(it) },
                    onHeaderHeightMeasured = { headerHeightPx = it },
                    isFavoriteLoading = state.isFavoriteLoading,
                    onViewAllArtistSongs = onViewAllArtistSongs,
                    nowPlayingId = nowPlayingId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ArtistDetailBody(
    artist: Artist,
    hotMusics: List<Music>,
    albums: List<Album>,
    listState: LazyListState,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onMusicClick: (Music) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onMusicMoreClick: (Music) -> Unit,
    onFavoriteClick: () -> Unit,
    onHeaderClick: () -> Unit = {},
    onCoverLongClick: (String) -> Unit = {},
    onHeaderHeightMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
    isFavoriteLoading: Boolean,
    onViewAllArtistSongs: (Long) -> Unit = {},
    nowPlayingId: Long? = null,
) {
    // 热门歌曲在 LazyColumn 里的全局索引 = 头部(0) + 「热门歌曲」标题(1) + 歌曲内偏移。
    val hotIndex = if (nowPlayingId == null) {
        -1
    } else {
        hotMusics.indexOfFirst { it.id == nowPlayingId }
    }
    val hotLazyIndex = if (hotIndex >= 0) hotIndex + 2 else -1
    val jumpScope = rememberCoroutineScope()
    val jumpHighlight = LocalJumpHighlight.current

    Box(modifier = modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "header") {
            ArtistDetailHeader(
                artist = artist,
                onFavoriteClick = onFavoriteClick,
                onClick = onHeaderClick,
                onCoverLongClick = onCoverLongClick,
                isFavoriteLoading = isFavoriteLoading,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    onHeaderHeightMeasured(coordinates.size.height.toFloat())
                },
            )
        }

        if (hotMusics.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "热门歌曲",
                    // 只有当该歌手还有更多作品时才给「更多」入口；热门歌单本身即全量时不重复给
                    moreText = "",
                    onMoreClick = if (artist.musicCount > hotMusics.size) {
                        { onViewAllArtistSongs(artist.id) }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(hotMusics, key = { it.id }) { music ->
                MusicListItem(
                    music = music,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedContentScope = animatedContentScope,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onClick = { onMusicClick(music) },
                    onMoreClick = { onMusicMoreClick(music) },
                    isNowPlaying = music.id == nowPlayingId,
                )
            }
        }

        if (albums.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "专辑",
                    onMoreClick = null,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(albums, key = { it.id }) { album ->
                        ArtistAlbumItem(
                            album = album,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedContentScope = animatedContentScope,
                            onClick = { onAlbumClick(album) },
                        )
                    }
                }
            }
        }

        // 空态：后端存在 music_count / album_count 均为 0 的歌手（导入产生的占位歌手），
        // 这类歌手详情页此前只有一个头部、下面整片空白，看起来像"页面坏了"。
        // 这里补一条明确说明，两端（Android / iOS）共用同一份文案。
        if (hotMusics.isEmpty() && albums.isEmpty()) {
            item(key = "artist_empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 72.dp, bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicOff,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "该歌手暂无歌曲",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "作品正在陆续收录中",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(68.dp))
        }
    }
        JumpToPlayingFabForList(
            listState = listState,
            targetIndex = hotLazyIndex,
            onClick = {
                jumpScope.launch {
                    listState.animateScrollToItem(hotLazyIndex)
                    // 到位后再闪一下，强调「就是这一行」
                    jumpHighlight.flash(nowPlayingId)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp),
        )
    }
}

@Composable
private fun ArtistDetailHeader(
    artist: Artist,
    modifier: Modifier = Modifier,
    onFavoriteClick: () -> Unit,
    isFavoriteLoading: Boolean,
    onClick: (() -> Unit)? = null,
    onCoverLongClick: (String) -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(8.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = artist.avatar,
            errorImageUrl = artist.coverImage,
            contentDescription = artist.name,
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onClick?.invoke() },
                    onLongClick = {
                        (artist.avatar ?: artist.coverImage)?.takeIf { it.isNotBlank() }
                            ?.let(onCoverLongClick)
                    },
                ),
            shape = RoundedCornerShape(12.dp),
            placeholderIcon = Icons.Filled.Person,
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            artist.alias?.takeIf { it.isNotBlank() }?.let { alias ->
                Text(
                    text = alias,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            val meta = buildList {
                artist.region?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (artist.musicCount > 0) add("${artist.musicCount} 首歌曲")
                if (artist.albumCount > 0) add("${artist.albumCount} 张专辑")
            }
            if (meta.isNotEmpty()) {
                Text(
                    text = meta.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (artist.tags.isNotEmpty()) {
                Text(
                    text = artist.tags.joinToString(" · ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            artist.description?.takeIf { it.isNotBlank() }?.let { description ->
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        IconButton(
            onClick = { onFavoriteClick() },
            enabled = !isFavoriteLoading,
        ) {
            if (isFavoriteLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = if (artist.isFavorite) {
                        Icons.Filled.Favorite
                    } else {
                        Icons.Filled.FavoriteBorder
                    },
                    contentDescription = "收藏",
                    tint = if (artist.isFavorite) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun ArtistAlbumItem(
    album: Album,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    with(sharedTransitionScope) {
        Column(
            modifier = modifier
                .width(120.dp)
                .clickable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                shape = MaterialTheme.shapes.medium,
            ) {
                CachedImage(
                    imageUrl = album.coverImage,
                    contentDescription = album.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .sharedElement(
                            sharedTransitionScope.rememberSharedContentState("album-cover-${album.id}"),
                            animatedContentScope,
                        ),
                    shape = MaterialTheme.shapes.medium,
                    placeholderIcon = Icons.Filled.Album,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 把歌手信息映射成「介绍」详情页的展示数据。 */
private fun Artist.toDetailIntroData(): DetailIntroData {
    val infoRows = buildList {
        region?.takeIf { it.isNotBlank() }?.let { add("地区" to it) }
        birthDate?.takeIf { it.isNotBlank() }?.let { add("出生日期" to it.take(10)) }
        debutDate?.takeIf { it.isNotBlank() }?.let { add("出道时间" to it.take(10)) }
        if (musicCount > 0) add("歌曲数" to "$musicCount 首")
        if (albumCount > 0) add("专辑数" to "$albumCount 张")
    }
    return DetailIntroData(
        title = name,
        coverUrl = avatar ?: coverImage,
        coverFallbackUrl = coverImage,
        coverIsCircle = true,
        coverPlaceholderIcon = Icons.Filled.Person,
        typeLabel = "歌手",
        subtitle = alias?.takeIf { it.isNotBlank() },
        tags = tags.map { it.name },
        description = description,
        infoRows = infoRows,
    )
}
