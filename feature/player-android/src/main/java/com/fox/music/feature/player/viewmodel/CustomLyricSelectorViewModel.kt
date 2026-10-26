package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.FetchBestMatchedLyricUseCase
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.ScrapedLyric
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import com.fox.music.feature.player.lyric.usecase.SearchCustomLyricPagingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CustomLyricSelectorState(
    val searchKeyword: String = "",
    val currentMusicTitle: String = "",
    val currentMusicArtist: String = "",
    val pagingData: Flow<PagingData<CustomLyricItem>> = emptyFlow(),
    val currentPage: Int = 0,
    /**
     * 服务端智能匹配出的「最佳匹配」歌词（/api/music/lyrics/fetch）。
     * 与列表项的区别：它是按 歌名+艺人+时长 算出来的最优解，
     * 且带命中来源信息；列表项只是「标题全等」的模糊结果，可能张冠李戴。
     */
    val bestMatched: CustomLyricItem? = null,
    val isMatchedLoading: Boolean = true,
) : com.fox.music.core.common.mvi.UiState

sealed interface CustomLyricSelectorIntent : com.fox.music.core.common.mvi.UiIntent {
    data class SelectPage(val page: Int) : CustomLyricSelectorIntent
    data class ApplyLyrics(val music: Music, val lyrics: String, val lyricsTrans: String?) :
        CustomLyricSelectorIntent
    data object Dismiss : CustomLyricSelectorIntent
}

sealed interface CustomLyricSelectorEffect : com.fox.music.core.common.mvi.UiEffect {
    data object Dismiss : CustomLyricSelectorEffect
    data class ShowToast(val message: String) : CustomLyricSelectorEffect
}

@HiltViewModel
class CustomLyricSelectorViewModel @Inject constructor(
    private val searchCustomLyricPagingUseCase: SearchCustomLyricPagingUseCase,
    private val fetchBestMatchedLyricUseCase: FetchBestMatchedLyricUseCase,
    private val musicController: MusicController,
) : MviViewModel<CustomLyricSelectorState, CustomLyricSelectorIntent, CustomLyricSelectorEffect>(
    CustomLyricSelectorState()
) {

    private var bestMatchedJob: Job? = null

    override fun handleIntent(intent: CustomLyricSelectorIntent) {
        when (intent) {
            is CustomLyricSelectorIntent.SelectPage -> {
                updateState { copy(currentPage = intent.page) }
            }
            is CustomLyricSelectorIntent.ApplyLyrics -> {
                val currentMusicId = musicController.playerState.value.currentMusic?.id
                if (currentMusicId != null) {
                    musicController.updateMusicLyrics(
                        currentMusicId,
                        intent.lyrics,
                        intent.lyricsTrans
                    )
                }
                sendEffect(CustomLyricSelectorEffect.ShowToast("歌词已更换"))
                sendEffect(CustomLyricSelectorEffect.Dismiss)
            }
            is CustomLyricSelectorIntent.Dismiss -> {
                sendEffect(CustomLyricSelectorEffect.Dismiss)
            }
        }
    }

    fun search(keyword: String, musicTitle: String, artistName: String) {
        val flow = searchCustomLyricPagingUseCase(keyword, musicTitle)
            .cachedIn(viewModelScope)
        updateState {
            copy(
                searchKeyword = keyword,
                currentMusicTitle = musicTitle,
                currentMusicArtist = artistName,
                currentPage = 0,
                pagingData = flow
            )
        }
        fetchBestMatched(musicTitle, artistName)
    }

    /**
     * 拉「最佳匹配」歌词：服务端按 当前曲目的歌名+艺人+时长 智能匹配
     * （网易云官方优先、时长 ±5s 择近，LRCLIB 兜底）。
     * 尽力而为：失败/无结果都不影响下面的列表浏览。
     */
    private fun fetchBestMatched(title: String, artist: String) {
        if (title.isBlank() || artist.isBlank()) {
            updateState { copy(bestMatched = null, isMatchedLoading = false) }
            return
        }
        bestMatchedJob?.cancel()
        bestMatchedJob = viewModelScope.launch {
            updateState { copy(isMatchedLoading = true, bestMatched = null) }
            val durationMs = musicController.playerState.value.currentMusic?.duration
                ?.takeIf { it > 0 }
            val item = runCatching { bestMatchedLyric(title, artist, durationMs) }.getOrNull()
            updateState { copy(bestMatched = item, isMatchedLoading = false) }
        }
    }

    /**
     * 两级尝试：①主艺人 + 歌名 ②仅歌名。
     * 合作曲的艺人串是「A / B」，塞进关键词反而搜不到（用户实测「清空艺人
     * 大概率能找到」）；服务端会按时长 ±5s 择近，命中不准时会在推荐卡上
     * 展示「匹配到的歌名/艺人」，用户一眼就能看出是不是同一版。
     */
    private suspend fun bestMatchedLyric(
        title: String,
        artist: String,
        durationMs: Long?,
    ): CustomLyricItem? {
        val primaryArtist = artist.split('/', '、', ',', '，', '＆', '&')
            .firstOrNull()?.trim().orEmpty()
        for (artistQuery in listOf(primaryArtist, "").distinct()) {
            val result = fetchBestMatchedLyricUseCase(title, artistQuery, durationMs)
            val item = result.getOrNull()?.let(::toLyricItem)
            if (item != null) return item
        }
        return null
    }

    private fun toLyricItem(lyric: ScrapedLyric): CustomLyricItem? {
        val text = lyric.lyrics
        if (text.isBlank() || lyric.instrumental) return null
        // lyricLineCount=0 表示「有值但客户端解析不出时间轴」，UI 会提示不可选
        val lineCount = LyricsParser.parseLrc(text).size
        if (lineCount <= 0) return null
        val music = Music(
            id = 0L,
            sourceId = lyric.sourceId ?: "0",
            title = lyric.matchedTitle ?: "",
            sourcePlatform = lyric.source,
            url = "",
            duration = lyric.durationMs ?: 0L,
            artists = listOfNotNull(
                lyric.matchedArtist?.takeIf { it.isNotBlank() }?.let { Artist(id = 0L, name = it) }
            ),
            lyrics = text,
        )
        return CustomLyricItem(
            music = music,
            lyrics = text,
            lyricsTrans = lyric.lyricsTrans?.takeIf { it.isNotBlank() },
            lyricLineCount = lineCount,
        )
    }
}
