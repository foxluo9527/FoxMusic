package com.fox.music.feature.player.ui.component

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.LyricCandidateRanker
import com.fox.music.core.model.music.LyricTextNormalizer
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.player.controller.MusicController
import com.fox.music.core.ui.component.FoxSheet
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import com.fox.music.feature.player.lyric.data.LyricStyle
import com.fox.music.feature.player.lyric.readLyricFile
import com.fox.music.feature.player.lyric.LyricFileRead
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorEffect
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorIntent
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorState
import com.fox.music.feature.player.viewmodel.CustomLyricSelectorViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val lrcPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        when (val read = readLyricFile(context, uri)) {
            is LyricFileRead.Ok -> viewModel.sendIntent(
                CustomLyricSelectorIntent.LoadCustomFile(read.name, read.text)
            )
            is LyricFileRead.Failed -> viewModel.sendIntent(
                CustomLyricSelectorIntent.CustomFileError(read.message)
            )
        }
    }
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
            },
            onShowCustom = { show ->
                viewModel.sendIntent(CustomLyricSelectorIntent.ShowCustom(show))
            },
            onCustomDraft = { text ->
                viewModel.sendIntent(CustomLyricSelectorIntent.UpdateCustomDraft(text))
            },
            onPickLrc = {
                lrcPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
            },
            onApplyCustom = {
                viewModel.sendIntent(CustomLyricSelectorIntent.ApplyCustom(state.customText))
            },
            onRetry = { viewModel.search(searchKeyword, musicTitle, artistName) },
        )
    }
}

@Composable
private fun CustomLyricSelectorContent(
    state: CustomLyricSelectorState,
    style: LyricStyle,
    playerState: PlayerState,
    onApply: (com.fox.music.core.model.music.Music, String, String?) -> Unit,
    onShowCustom: (Boolean) -> Unit,
    onCustomDraft: (String) -> Unit,
    onPickLrc: () -> Unit,
    onApplyCustom: () -> Unit,
    onRetry: () -> Unit,
) {
    val pagerScope = rememberCoroutineScope()

    // 首页 = 服务端智能匹配（歌名+艺人+时长）的「最佳匹配」歌词，其后是各来源截断后的候选
    val best = state.bestMatched
    val bestOffset = if (best != null) 1 else 0
    val itemCount = bestOffset + state.candidates.size
    val pagerState = rememberPagerState(pageCount = { itemCount.coerceAtLeast(1) })

    val currentPage = pagerState.currentPage
    val hasPrev = currentPage > 0 && itemCount > 0
    val hasNext = currentPage < itemCount - 1
    val activeItem: CustomLyricItem? = when {
        bestOffset == 1 && currentPage == 0 -> best
        else -> state.candidates.getOrNull(currentPage - bestOffset)
    }
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        Column(Modifier.fillMaxSize()) {
            // Song info header — follows current page
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 88.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        activeItem?.music?.title ?: state.currentMusicTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        activeItem?.music?.artists?.joinToString(" / ") { it.name }
                            ?: state.currentMusicArtist,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LyricModeToggle(
                    showCustom = state.showCustom,
                    onShowCustom = onShowCustom,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }

            if (state.showCustom) {
                CustomLyricEditor(
                    state = state,
                    modifier = Modifier.weight(1f),
                    onDraft = onCustomDraft,
                    onPickLrc = onPickLrc,
                    onApply = onApplyCustom,
                )
            } else when {
                // 列表还在加载时，若已拿到最佳匹配就先展示它（不空等列表）
                state.isSearching && itemCount == 0 -> {
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

                itemCount == 0 && !state.isSearching -> {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(), contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "未找到匹配歌词",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontSize = 16.sp
                            )
                            if (!state.searchNote.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    state.searchNote,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "点击重试",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 14.sp,
                                modifier = Modifier.clickable(onClick = onRetry),
                            )
                        }
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
                                state.candidates.getOrNull(page - bestOffset)
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
                                    LyricSourceChip(item = item, isBestMatched = isBestMatched)
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
                                        activeLineColor = Color(style.highlightColor),
                                        inactiveLineColor = Color(style.textColor),
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
        if (!state.showCustom && itemCount > 0 && !state.searchNote.isNullOrBlank()) {
            Text(
                state.searchNote,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 11.sp,
            )
        }
    }
}

/**
 * 右上角单按钮：文案是将要切过去的模式，点击在「匹配歌词」与「自定义」之间切换。
 */
@Composable
private fun LyricModeToggle(
    showCustom: Boolean,
    onShowCustom: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (showCustom) "匹配歌词" else "自定义"
    Box(
        modifier
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                RoundedCornerShape(50),
            )
            .clickable(
                onClickLabel = "切换到$label",
                onClick = { onShowCustom(!showCustom) },
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun LyricSourceChip(
    item: CustomLyricItem,
    isBestMatched: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val syncNote = if (item.synced) "" else " · 纯文本"
        val title = if (isBestMatched) {
            "最佳匹配 · ${LyricCandidateRanker.sourceLabel(item.music.sourcePlatform)} · ${item.lyricLineCount} 行"
        } else {
            "${LyricCandidateRanker.sourceLabel(item.music.sourcePlatform)} · ${item.lyricLineCount} 行$syncNote"
        }
        Box(
            Modifier
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    RoundedCornerShape(50),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                title,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        val sourcePage = item.attributionUrl
        if (!sourcePage.isNullOrBlank()) {
            Text(
                sourcePage,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 24.dp, end = 24.dp),
            )
        }
    }
}

@Composable
private fun CustomLyricEditor(
    state: CustomLyricSelectorState,
    modifier: Modifier,
    onDraft: (String) -> Unit,
    onPickLrc: () -> Unit,
    onApply: () -> Unit,
) {
    val preview = if (state.customText.isBlank()) {
        null
    } else {
        LyricTextNormalizer.prepare(state.customText, state.currentDurationMs)
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            "粘贴纯文本或 LRC，也可以直接选本地 .lrc。读到可用的文件会立刻替换当前歌词。",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.customText,
            onValueChange = onDraft,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 180.dp),
            placeholder = { Text("在这里粘贴歌词") },
            minLines = 8,
        )
        if (!state.customFileName.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "已选择：${state.customFileName}",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        val message = state.customError ?: preview?.let { prepared ->
            if (prepared.ok) {
                val kind = if (prepared.synced) "LRC" else "纯文本，按时长铺开"
                "可写入 ${prepared.lineCount} 行（$kind）"
            } else {
                prepared.message
            }
        }
        if (!message.isNullOrBlank()) {
            val isError = state.customError != null || preview?.ok == false
            Text(
                message,
                color = if (isError) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .height(36.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        MaterialTheme.shapes.medium,
                    )
                    .clickable(onClick = onPickLrc)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("选择 .lrc 文件", fontSize = 14.sp)
            }
            Box(
                Modifier
                    .height(36.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
                    .clickable(onClick = onApply)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "使用此歌词",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
