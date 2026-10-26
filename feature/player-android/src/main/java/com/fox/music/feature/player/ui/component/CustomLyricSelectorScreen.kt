package com.fox.music.feature.player.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import com.fox.music.feature.player.lyric.data.LyricStyle
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorEffect
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorIntent
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorState
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 歌词来源展示名（客户端 λ 协定：netease/lrclib/lyricsovh） */
private fun sourceLabel(raw: String?): String = when (raw?.lowercase()) {
    "netease" -> "网易云官方"
    "lrclib" -> "LRCLIB"
    "lyricsovh" -> "lyrics.ovh"
    else -> raw ?: "未知来源"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLyricSelectorSheet(
    searchKeyword: String,
    musicTitle: String,
    artistName: String,
    style: LyricStyle,
    musicController: MusicController,
    onDismiss: () -> Unit,
    viewModel: CustomLyricSelectorViewModel = hiltViewModel(),
) {
    LaunchedEffect(searchKeyword) {
        viewModel.search(searchKeyword, musicTitle, artistName)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CustomLyricSelectorEffect.Dismiss -> onDismiss()
                is CustomLyricSelectorEffect.ShowToast ->
                    com.blankj.utilcode.util.ToastUtils.make().show(effect.message)
            }
        }
    }

    val state by viewModel.uiState.collectAsState()
    val playerState by musicController.playerState.collectAsState(PlayerState())
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val backProgress = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    fun dismissSheet() {
        scope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    FoxSheet(
        onDismissRequest = { dismissSheet() },
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 60.dp)
            .onSizeChanged { sheetHeightPx = it.height.toFloat() }
            .graphicsLayer {
                val h = sheetHeightPx.takeIf { it > 0f } ?: size.height
                translationY = h * backProgress.value
                scaleX = 1f
                scaleY = 1f
            },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.3f),
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
    ) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event -> backProgress.snapTo(event.progress) }
                backProgress.animateTo(1f, animationSpec = tween(180))
                try {
                    sheetState.hide()
                } finally {
                    onDismiss()
                }
            } catch (e: CancellationException) {
                backProgress.animateTo(0f, animationSpec = tween(180))
                throw e
            }
        }

        CustomLyricSelectorContent(
            state = state,
            style = style,
            playerState = playerState,
            onApply = { music, lyrics, lyricsTrans ->
                viewModel.sendIntent(
                    CustomLyricSelectorIntent.ApplyLyrics(music, lyrics, lyricsTrans)
                )
            }
        )
    }
}

@Composable
private fun CustomLyricSelectorContent(
    state: CustomLyricSelectorState,
    style: LyricStyle,
    playerState: PlayerState,
    onApply: (com.fox.music.core.model.music.Music, String, String?) -> Unit,
) {
    val pagingItems = state.pagingData.collectAsLazyPagingItems()
    val pagerScope = rememberCoroutineScope()

    // 首页 = 服务端智能匹配（歌名+艺人+时长）的「最佳匹配」歌词，其后才是搜索列表
    val best = state.bestMatched
    val bestOffset = if (best != null) 1 else 0
    val itemCount = bestOffset + pagingItems.itemCount
    val pagerState = rememberPagerState(pageCount = { itemCount })

    val currentPage = pagerState.currentPage
    val hasPrev = currentPage > 0
    val hasNext = currentPage < itemCount - 1
    val activeItem: CustomLyricItem? = when {
        bestOffset == 1 && currentPage == 0 -> best
        else -> {
            // LazyPagingItems 越界访问会直接抛 IndexOutOfBoundsException("Index: X, Size: Y")，
            // 不像普通 List 返回 null。best 未就绪 / 列表仍为空时 currentPage 会指向空列表，
            // 必须先做边界保护（曾因缺此判断进入歌词纠正即崩溃）。
            val listIndex = currentPage - bestOffset
            if (listIndex in 0 until pagingItems.itemCount) pagingItems[listIndex] else null
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize()) {
            // Song info header — follows current page
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    activeItem?.music?.title ?: state.currentMusicTitle,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    activeItem?.music?.artists?.joinToString(" / ") { it.name }
                        ?: state.currentMusicArtist,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when {
                // 列表还在加载时，若已拿到最佳匹配就先展示它（不空等列表）
                pagingItems.loadState.refresh is LoadState.Loading && itemCount == 0 -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                if (state.isMatchedLoading) "智能匹配中..." else "搜索中...",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                itemCount == 0 && pagingItems.loadState.refresh is LoadState.NotLoading -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "未找到匹配歌词",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 16.sp
                        )
                    }
                }

                else -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val item: CustomLyricItem? = if (bestOffset == 1 && page == 0) best else {
                                // 同 activeItem：越界访问 LazyPagingItems 会抛异常，需边界保护
                                val listIndex = page - bestOffset
                                if (listIndex in 0 until pagingItems.itemCount) pagingItems[listIndex] else null
                            }
                            val isSelected = page == currentPage
                            val isBestMatched = bestOffset == 1 && page == 0

                            if (item != null) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (!isSelected) Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                pagerScope.launch {
                                                    pagerState.animateScrollToPage(page)
                                                }
                                            }
                                            else Modifier
                                        )
                                ) {
                                    if (isBestMatched) {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(top = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                                        RoundedCornerShape(50)
                                                    )
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    "最佳匹配 · ${sourceLabel(item?.music?.sourcePlatform)} · ${item?.lyricLineCount ?: 0} 行",
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                    BilingualLyricComponent(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 40.dp),
                                        lyrics = LyricsParser.parseBilingualLrc(
                                            item.lyrics,
                                            item.lyricsTrans
                                        ),
                                        currentTimeMs = playerState.position,
                                        onSeek = {},
                                        delay = 200,
                                        userScrollEnabled = isSelected,
                                        activeLineColor = MaterialTheme.colorScheme.primary,
                                        inactiveLineColor = MaterialTheme.colorScheme.primaryContainer,
                                        activeLineFontSize = style.fontSize + 4,
                                        inactiveLineFontSize = style.fontSize,
                                        autoScrollToCurrent = true,
                                    )
                                }
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }

                        if (hasPrev) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 16.dp)
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .clickable {
                                        pagerScope.launch {
                                            pagerState.animateScrollToPage(currentPage - 1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "上一个",
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (hasNext) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 16.dp)
                                    .size(48.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                        CircleShape
                                    )
                                    .clickable {
                                        pagerScope.launch {
                                            pagerState.animateScrollToPage(currentPage + 1)
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = "下一个",
                                    modifier = Modifier.size(30.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (itemCount > 1) {
                            Text(
                                "${currentPage + 1} / $itemCount",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                fontSize = 13.sp
                            )
                        } else {
                            Spacer(Modifier)
                        }

                        val applyItem = activeItem
                        if (applyItem != null) {
                            Box(
                                Modifier
                                    .height(36.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.medium
                                    )
                                    .clickable {
                                        onApply(
                                            applyItem.music,
                                            applyItem.lyrics,
                                            applyItem.lyricsTrans
                                        )
                                    }
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "使用此歌词",
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
        if (pagingItems.loadState.append is LoadState.Loading) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }

        val refreshError = pagingItems.loadState.refresh as? LoadState.Error
        val appendError = pagingItems.loadState.append as? LoadState.Error
        val error = refreshError ?: appendError
        if (error != null) {
            Text(
                "加载失败，点击重试",
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (refreshError != null) pagingItems.refresh()
                        else pagingItems.retry()
                    }
                    .padding(16.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp
            )
        }
    }
}
