package com.fox.music.feature.search

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.collectAsLazyPagingItems
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.model.social.Post
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxEditorialCircleButton
import com.fox.music.core.ui.component.FoxEditorialRow
import com.fox.music.core.ui.component.FoxEditorialSectionLabel
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkMuted
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.InkTertiary
import com.fox.music.core.ui.theme.PaperHairline

private val searchTabs = listOf(
    SearchResultTab.MUSIC to "单曲",
    SearchResultTab.ARTIST to "歌手",
    SearchResultTab.ALBUM to "专辑",
    SearchResultTab.POST to "帖子",
)

/**
 * 搜索页纯界面：state + onIntent 驱动。
 *
 * v10 编辑排版：顶部是「返回圆钮 + 12dp 圆角次级填充搜索框」，未搜索时给一记刊头
 * （DISCOVER + 衬线「搜索」），历史走 chip 行，热搜榜走「01 + 编号编辑列表」，
 * 结果态 tab 用 2dp 强调色下划线，结果行统一 [FoxEditorialRow]。
 *
 * 三处平台相关逻辑留在外壳（:feature:search-android 的 SearchScreen）：
 *   1. BackHandler            —— androidx.activity，Android 专属
 *   2. LocalFocusManager      —— 软键盘收起交给外壳的 onClearFocus
 *   3. 帖子结果列表            —— PostNoteGridCard 依赖 PostVideoCover 等安卓组件，
 *                               由外壳以 postResultsContent 插槽注入
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchContent(
    state: SearchState,
    onIntent: (SearchIntent) -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedContentScope: AnimatedContentScope,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onMusicClick: (Music) -> Unit = {},
    onArtistClick: (Long) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onMusicMoreClick: (Music) -> Unit = {},
    onClearFocus: () -> Unit = {},
    onMusicListChanged: (List<Music>) -> Unit = {},
    updateMusicList: (List<Music>, String) -> Unit = { _, _ -> },
    applyPostOverrides: (Post) -> Post = { it },
    onPostLikeClick: (Post) -> Unit = {},
    onPostTagClick: (String) -> Unit = {},
    postResultsContent: (@Composable () -> Unit)? = null,
    /** 「去收录」：跳转第三方平台搜索（添加单曲）页，带入当前关键词 */
    onCollectClick: (String) -> Unit = {},
) {
    val musicResults = state.musicResults.collectAsLazyPagingItems()
    val artistResults = state.artistResults.collectAsLazyPagingItems()
    val albumResults = state.albumResults.collectAsLazyPagingItems()
    val playlistKey = SEARCH_ROUTE + "-${state.query}"
    val showRecommendPanel = state.showRecommendPanel
    val canDismissToResults = state.hasSearched && state.isSearchFocused

    fun handleBack() {
        if (canDismissToResults) {
            onClearFocus()
            onIntent(SearchIntent.FocusChanged(false))
        } else {
            onBack()
        }
    }

    with(sharedTransitionScope) {
        Column(
            modifier = modifier.fillMaxSize(),
        ) {
            // ── 顶部：返回圆钮 + 去胶囊搜索框 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FoxEditorialCircleButton(onClick = { handleBack() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier.size(18.dp),
                        tint = InkMuted,
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                FoxEditorialSearchField(
                    query = state.query,
                    onQueryChange = { onIntent(SearchIntent.QueryChange(it)) },
                    onSearch = { onIntent(SearchIntent.Search) },
                    onFocusChanged = { onIntent(SearchIntent.FocusChanged(it)) },
                    // 进入搜索页自动唤起输入法；带 tag/query 深链进入时（初始 query 非空）
                    // 不抢焦点，避免键盘挡住刚出的帖子结果。
                    autoFocus = state.query.isBlank(),
                )
            }

            val showResultsChrome = state.hasSearched && !showRecommendPanel

            if (showResultsChrome) {
                FoxEditorialTabRow(
                    tabs = searchTabs.map { it.second },
                    selectedIndex = searchTabs.indexOfFirst { it.first == state.selectedTab }
                        .coerceAtLeast(0),
                    onTabSelected = { index ->
                        onIntent(SearchIntent.SelectTab(searchTabs[index].first))
                    },
                )
            } else {
                // ── 刊头：DISCOVER + 衬线「搜索」 ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "DISCOVER",
                        style = FoxEditorial.kicker.copy(fontSize = 9.5.sp),
                        color = InkQuaternary,
                    )
                    Text(
                        text = "搜索",
                        style = FoxEditorial.mastheadTitle.copy(fontSize = 26.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    showRecommendPanel -> RecommendOrSuggestContent(
                        query = state.query,
                        suggestions = state.suggestions,
                        hotKeywords = state.hotKeywords,
                        hotKeywordsHasMore = state.hotKeywordsHasMore,
                        isLoadingHotKeywords = state.isLoadingHotKeywords,
                        searchHistory = state.searchHistory,
                        isHistoryExpanded = state.isHistoryExpanded,
                        onHotKeywordClick = { onIntent(SearchIntent.SelectHotKeyword(it)) },
                        onRefreshHotKeywords = { onIntent(SearchIntent.RefreshHotKeywords) },
                        onHistoryClick = { onIntent(SearchIntent.SelectHistory(it)) },
                        onDeleteHistory = { onIntent(SearchIntent.DeleteHistory(it)) },
                        onSuggestClick = { onIntent(SearchIntent.SelectSuggest(it)) },
                        onToggleHistoryExpanded = {
                            onIntent(SearchIntent.ToggleHistoryExpanded)
                        },
                        onClearHistory = { onIntent(SearchIntent.ClearHistory) },
                    )

                    state.selectedTab == SearchResultTab.MUSIC -> MusicSearchResults(
                        pagingItems = musicResults,
                        sharedTransitionScope = this@with,
                        animatedContentScope = animatedContentScope,
                        onMusicClick = onMusicClick,
                        onMusicMoreClick = onMusicMoreClick,
                        onArtistClick = onArtistClick,
                        onMusicListChanged = onMusicListChanged,
                        updateMusicList = updateMusicList,
                        playlistKey = playlistKey,
                        hasSearched = state.hasSearched,
                        onCollectClick = { onCollectClick(state.query) },
                    )

                    state.selectedTab == SearchResultTab.ARTIST -> ArtistSearchResults(
                        pagingItems = artistResults,
                        onArtistClick = onArtistClick,
                    )

                    state.selectedTab == SearchResultTab.ALBUM -> AlbumSearchResults(
                        pagingItems = albumResults,
                        onAlbumClick = onAlbumClick,
                    )

                    state.selectedTab == SearchResultTab.POST -> {
                        postResultsContent?.invoke() ?: PostSearchResultsFallback(
                            applyOverrides = applyPostOverrides,
                            onPostLikeClick = onPostLikeClick,
                            onPostTagClick = onPostTagClick,
                        )
                    }
                }
            }
        }
    }
}

/**
 * v10 搜索框：40dp 高、12dp 圆角、surfaceVariant 实底填充（去胶囊），
 * 左侧线性放大镜，右侧在输入非空时出现线性 ×。
 */
@Composable
private fun FoxEditorialSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    // 进入搜索页自动聚焦并唤起输入法（只在首次组合时触发一次，避免后续重组反复弹键盘）
    LaunchedEffect(Unit) {
        if (autoFocus) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
    }

    val fieldShape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant, fieldShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "搜索",
                    modifier = Modifier.size(17.dp),
                    tint = InkQuaternary,
                )
                Spacer(modifier = Modifier.width(9.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索歌曲、艺人、歌单",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = InkQuaternary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "清空输入",
                            modifier = Modifier.size(16.dp),
                            tint = InkTertiary,
                        )
                    }
                }
            }
        },
    )
}

/**
 * v10 结果 tab 行：选中 = 13.5sp 强调色 + 底部 2dp 强调色下划线，
 * 未选中 = ink3；整行底部一条 1px 发丝线。命中区 44dp。
 */
@Composable
private fun FoxEditorialTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .height(44.dp)
                        .clickable { onTabSelected(index) },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                            color = if (selected) accent else InkTertiary,
                        )
                        Spacer(modifier = Modifier.height(9.dp))
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(if (selected) accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = PaperHairline)
    }
}

@Composable
private fun RecommendOrSuggestContent(
    query: String,
    suggestions: List<SearchSuggest>,
    hotKeywords: List<HotKeyword>,
    hotKeywordsHasMore: Boolean,
    isLoadingHotKeywords: Boolean,
    searchHistory: List<SearchHistory>,
    isHistoryExpanded: Boolean,
    onHotKeywordClick: (String) -> Unit,
    onRefreshHotKeywords: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onSuggestClick: (SearchSuggest) -> Unit,
    onToggleHistoryExpanded: () -> Unit,
    onClearHistory: () -> Unit,
) {
    when {
        suggestions.isNotEmpty() -> SearchSuggestList(
            suggestions = suggestions,
            onSuggestClick = onSuggestClick,
        )

        query.trim().length < 2 -> SearchRecommendContent(
            hotKeywords = hotKeywords,
            hotKeywordsHasMore = hotKeywordsHasMore,
            isLoadingHotKeywords = isLoadingHotKeywords,
            searchHistory = searchHistory,
            isHistoryExpanded = isHistoryExpanded,
            onHotKeywordClick = onHotKeywordClick,
            onRefreshHotKeywords = onRefreshHotKeywords,
            onHistoryClick = onHistoryClick,
            onDeleteHistory = onDeleteHistory,
            onToggleHistoryExpanded = onToggleHistoryExpanded,
            onClearHistory = onClearHistory,
        )

        else -> {
            // 已触发建议请求但返回空：清空上一屏推荐内容
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun SearchSuggestList(
    suggestions: List<SearchSuggest>,
    onSuggestClick: (SearchSuggest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(
            items = suggestions,
            key = { "${it.type}_${it.id}_${it.text}" },
        ) { suggest ->
            FoxEditorialRow(
                title = suggest.text,
                subtitle = suggestTypeLabel(suggest.type),
                leading = {
                    CachedImage(
                        imageUrl = suggest.cover,
                        contentDescription = suggest.text,
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(9.dp),
                    )
                },
                onClick = { onSuggestClick(suggest) },
            )
        }
    }
}

private fun suggestTypeLabel(type: String): String = when (type.lowercase()) {
    "music" -> "音乐"
    "artist" -> "艺人"
    "album" -> "专辑"
    "video" -> "视频"
    "novel" -> "小说"
    "post" -> "动态"
    else -> type.ifBlank { "搜索" }
}

/** 热度：万位以上用「x 万」（保留一位小数，纯整数运算，避免 commonMain 用 String.format）。 */
private fun formatHotCount(count: Int): String {
    if (count <= 0) return ""
    if (count < 10_000) return count.toString()
    val wan = count / 10_000
    val tenths = (count % 10_000) / 1_000
    return if (wan >= 10 || tenths == 0) "$wan 万" else "$wan.$tenths 万"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchRecommendContent(
    hotKeywords: List<HotKeyword>,
    hotKeywordsHasMore: Boolean,
    isLoadingHotKeywords: Boolean,
    searchHistory: List<SearchHistory>,
    isHistoryExpanded: Boolean,
    onHotKeywordClick: (String) -> Unit,
    onRefreshHotKeywords: () -> Unit,
    onHistoryClick: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onToggleHistoryExpanded: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val historyKeywords = searchHistory.map { it.keyword }.distinct()
    val visibleHistory = if (isHistoryExpanded) {
        historyKeywords
    } else {
        historyKeywords.take(SEARCH_HISTORY_COLLAPSED_MAX)
    }
    val canExpandHistory = historyKeywords.size > SEARCH_HISTORY_COLLAPSED_MAX

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        if (historyKeywords.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "历史",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontSize = 11.5.sp,
                        letterSpacing = (-0.2).sp,
                    ),
                    color = InkTertiary,
                    modifier = Modifier.weight(1f),
                )
                if (canExpandHistory) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .clickable(onClick = onToggleHistoryExpanded)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isHistoryExpanded) "收起" else "展开",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = InkQuaternary,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(onClick = onClearHistory)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = InkQuaternary,
                    )
                }
            }
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                visibleHistory.forEach { keyword ->
                    HistoryKeywordChip(
                        text = keyword,
                        onClick = { onHistoryClick(keyword) },
                        onDelete = { onDeleteHistory(keyword) },
                    )
                }
            }
        }

        if (hotKeywords.isNotEmpty()) {
            FoxEditorialSectionLabel(
                number = "01",
                title = "热搜榜",
                modifier = Modifier.padding(top = 20.dp, bottom = 2.dp),
                trailing = {
                    if (hotKeywordsHasMore) {
                        val refreshTransition = rememberInfiniteTransition(label = "refresh_spin")
                        val refreshRotation by refreshTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 800, easing = LinearEasing),
                            ),
                            label = "refresh_rotation",
                        )
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .clickable(enabled = !isLoadingHotKeywords) { onRefreshHotKeywords() }
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Autorenew,
                                    contentDescription = "换一换",
                                    modifier = Modifier
                                        .size(14.dp)
                                        .rotate(if (isLoadingHotKeywords) refreshRotation else 0f),
                                    tint = InkQuaternary,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "换一换",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = InkQuaternary,
                                )
                            }
                        }
                    }
                },
            )
            hotKeywords.forEachIndexed { index, hot ->
                FoxEditorialRow(
                    number = (index + 1).toString().padStart(2, '0'),
                    title = hot.keyword,
                    subtitle = suggestTypeLabel(hot.type).takeIf { hot.type.isNotBlank() },
                    trailing = {
                        Text(
                            text = formatHotCount(hot.searchCount),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontFeatureSettings = "tnum",
                            ),
                            color = InkQuaternary,
                        )
                    },
                    onClick = { onHotKeywordClick(hot.keyword) },
                )
            }
        }
    }
}

/** 历史 chip：surface 实底 + 1px hairline，9dp 圆角，尾带线性 ×（点击即搜索，× 删除）。 */
@Composable
private fun HistoryKeywordChip(
    text: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val chipShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(chipShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, PaperHairline, chipShape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "删除该历史",
                modifier = Modifier.size(12.dp),
                tint = InkQuaternary,
            )
        }
    }
}
