package com.fox.music.feature.search

import androidx.paging.PagingData
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchHistory
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.model.social.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class SearchResultTab {
    MUSIC,
    ARTIST,
    ALBUM,
    POST,
}

enum class SearchMusicPlatform(val apiValue: String?, val label: String) {
    FOX(null, "平台曲库"),
    QQ("qq", "QQ 音乐"),
    NETEASE("netease", "网易云"),
}

/** 历史 / 热词的折叠展示阈值，UI 与 ViewModel 共用。 */
const val SEARCH_HISTORY_COLLAPSED_MAX = 6
const val SEARCH_HISTORY_COLLAPSED_ROWS = 3
const val SEARCH_HOT_KEYWORD_PAGE_SIZE = 8
const val SEARCH_HOT_KEYWORD_MAX_ROWS = 4

data class SearchState(
    val query: String = "",
    val selectedTab: SearchResultTab = SearchResultTab.MUSIC,
    val selectedMusicPlatform: SearchMusicPlatform = SearchMusicPlatform.FOX,
    val postTag: String? = null,
    val postSort: String = "latest",
    val musicResults: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val artistResults: Flow<PagingData<Artist>> = flowOf(PagingData.empty()),
    val albumResults: Flow<PagingData<Album>> = flowOf(PagingData.empty()),
    val postResults: Flow<PagingData<Post>> = flowOf(PagingData.empty()),
    val searchHistory: List<SearchHistory> = emptyList(),
    val isHistoryExpanded: Boolean = false,
    val hotKeywords: List<HotKeyword> = emptyList(),
    val hotKeywordsHasMore: Boolean = false,
    val isLoadingHotKeywords: Boolean = false,
    val suggestions: List<SearchSuggest> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val isSearchFocused: Boolean = false,
    val postLikeOverrides: Map<Long, Pair<Boolean, Int>> = emptyMap(),
) : UiState {
    /** 聚焦或尚未搜索时展示推荐/建议面板；失焦且已搜索时展示结果 */
    val showRecommendPanel: Boolean
        get() = isSearchFocused || !hasSearched
}

sealed interface SearchIntent : UiIntent {
    data class QueryChange(val query: String) : SearchIntent
    data object Search : SearchIntent
    data class SelectHistory(val keyword: String) : SearchIntent
    /** 删除单条历史（chip 上的 ×） */
    data class DeleteHistory(val keyword: String) : SearchIntent
    data class SelectHotKeyword(val keyword: String) : SearchIntent
    data class SelectSuggest(val suggest: SearchSuggest) : SearchIntent
    data class FocusChanged(val focused: Boolean) : SearchIntent
    data class SelectTab(val tab: SearchResultTab) : SearchIntent
    data class SelectMusicPlatform(val platform: SearchMusicPlatform) : SearchIntent
    data object ToggleHistoryExpanded : SearchIntent
    data object ClearHistory : SearchIntent
    data object RefreshHotKeywords : SearchIntent
    data object ClearPostTag : SearchIntent
    /** 从「去收录」流程返回后，强制重新拉取一次搜索结果 */
    data object RefreshSearch : SearchIntent
    data class FilterPostTag(val tag: String) : SearchIntent
    data class TogglePostLike(val post: Post) : SearchIntent
}

sealed interface SearchEffect : UiEffect {
    data class NavigateToMusic(val music: Music, val musicList: List<Music>) : SearchEffect
    data class NavigateToArtist(val artistId: Long) : SearchEffect
    data class NavigateToAlbum(val album: Album) : SearchEffect
    data class NavigateToPost(val postId: Long) : SearchEffect
    /** 请求 UI 清除搜索框焦点（收起软键盘） */
    data object ClearSearchFocus : SearchEffect
}
