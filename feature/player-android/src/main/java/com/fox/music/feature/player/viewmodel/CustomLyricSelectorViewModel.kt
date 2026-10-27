package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.lyric.OpenLyricSources
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.usecase.FetchBestMatchedLyricUseCase
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.LyricCandidateRanker
import com.fox.music.core.model.music.LyricHit
import com.fox.music.core.model.music.LyricQuery
import com.fox.music.core.model.music.LyricTextNormalizer
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.ScoredLyricHit
import com.fox.music.core.model.music.ScrapedLyric
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

data class CustomLyricSelectorState(
    val searchKeyword: String = "",
    val currentMusicTitle: String = "",
    val currentMusicArtist: String = "",
    val currentDurationMs: Long = 0L,
    val candidates: List<CustomLyricItem> = emptyList(),
    val isSearching: Boolean = true,
    val searchNote: String? = null,
    val currentPage: Int = 0,
    /**
     * 服务端智能匹配出的「最佳匹配」歌词（/api/music/lyrics/fetch）。
     * 列表是各来源按相似度截断后的少量候选，不再把网易云同名结果整页倒进来。
     */
    val bestMatched: CustomLyricItem? = null,
    val isMatchedLoading: Boolean = true,
    val showCustom: Boolean = false,
    val customText: String = "",
    val customFileName: String? = null,
    val customError: String? = null,
) : com.fox.music.core.common.mvi.UiState

sealed interface CustomLyricSelectorIntent : com.fox.music.core.common.mvi.UiIntent {
    data class SelectPage(val page: Int) : CustomLyricSelectorIntent
    data class ApplyLyrics(val music: Music, val lyrics: String, val lyricsTrans: String?) :
        CustomLyricSelectorIntent
    data class ShowCustom(val show: Boolean) : CustomLyricSelectorIntent
    data class UpdateCustomDraft(val text: String) : CustomLyricSelectorIntent
    data class LoadCustomFile(val name: String?, val text: String) : CustomLyricSelectorIntent
    data class CustomFileError(val message: String) : CustomLyricSelectorIntent
    data class ApplyCustom(val text: String) : CustomLyricSelectorIntent
    data object Dismiss : CustomLyricSelectorIntent
}

sealed interface CustomLyricSelectorEffect : com.fox.music.core.common.mvi.UiEffect {
    data object Dismiss : CustomLyricSelectorEffect
    data class ShowToast(val message: String) : CustomLyricSelectorEffect
}

@HiltViewModel
class CustomLyricSelectorViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val fetchBestMatchedLyricUseCase: FetchBestMatchedLyricUseCase,
    private val openLyricSources: OpenLyricSources,
    private val musicController: MusicController,
) : MviViewModel<CustomLyricSelectorState, CustomLyricSelectorIntent, CustomLyricSelectorEffect>(
    CustomLyricSelectorState()
) {

    private var bestMatchedJob: Job? = null
    private var searchJob: Job? = null
    private val publishMutex = Mutex()
    private var searchGeneration = 0
    private var latestGroups = listOf<List<ScoredLyricHit>>()
    private var latestFailed = listOf<String>()

    override fun handleIntent(intent: CustomLyricSelectorIntent) {
        when (intent) {
            is CustomLyricSelectorIntent.SelectPage -> {
                updateState { copy(currentPage = intent.page) }
            }
            is CustomLyricSelectorIntent.ApplyLyrics -> applyLyrics(intent.lyrics, intent.lyricsTrans)
            is CustomLyricSelectorIntent.ShowCustom -> updateState {
                copy(showCustom = intent.show, customError = null)
            }
            is CustomLyricSelectorIntent.UpdateCustomDraft -> updateState {
                copy(customText = intent.text, customFileName = null, customError = null)
            }
            is CustomLyricSelectorIntent.LoadCustomFile -> {
                updateState {
                    copy(
                        showCustom = true,
                        customText = intent.text,
                        customFileName = intent.name,
                        customError = null,
                    )
                }
                applyPrepared(intent.text)
            }
            is CustomLyricSelectorIntent.CustomFileError -> updateState {
                copy(showCustom = true, customError = intent.message)
            }
            is CustomLyricSelectorIntent.ApplyCustom -> applyPrepared(intent.text)
            is CustomLyricSelectorIntent.Dismiss -> {
                sendEffect(CustomLyricSelectorEffect.Dismiss)
            }
        }
    }

    fun search(keyword: String, musicTitle: String, artistName: String) {
        val durationMs = musicController.playerState.value.currentMusic?.duration
            ?.takeIf { it > 0L } ?: 0L
        searchJob?.cancel()
        val generation = ++searchGeneration
        updateState {
            copy(
                searchKeyword = keyword,
                currentMusicTitle = musicTitle,
                currentMusicArtist = artistName,
                currentDurationMs = durationMs,
                currentPage = 0,
                candidates = emptyList(),
                isSearching = true,
                searchNote = null,
            )
        }
        fetchBestMatched(musicTitle, artistName)
        val query = LyricQuery(
            title = musicTitle.ifBlank { keyword },
            artist = artistName,
            durationMs = durationMs,
        )
        searchJob = viewModelScope.launch {
            publishMutex.withLock {
                if (generation != searchGeneration) return@withLock
                latestGroups = emptyList()
                latestFailed = emptyList()
            }
            if (generation != searchGeneration) return@launch
            coroutineScope {
                listOf(
                    async { capture(generation, "网易云") { neteaseCandidates(query) } },
                    async { capture(generation, "LRCLIB") { openLyricSources.lrclib(query) } },
                    async { capture(generation, "Megalobiz") { openLyricSources.megalobiz(query) } },
                    async { capture(generation, "lyrics.ovh") { openLyricSources.lyricsOvh(query) } },
                ).awaitAll()
            }
            publishMutex.withLock {
                if (generation != searchGeneration) return@withLock
                publish(searching = false)
            }
        }
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
            if (!isActive) return@launch
            updateState { copy(bestMatched = item, isMatchedLoading = false) }
            publishMutex.withLock {
                if (!isActive) return@withLock
                publish(searching = currentState.isSearching)
            }
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

    private suspend fun capture(
        generation: Int,
        sourceName: String,
        block: suspend () -> List<ScoredLyricHit>,
    ) {
        val result = runCatching { block() }
        publishMutex.withLock {
            if (generation != searchGeneration) return@withLock
            result.fold(
                onSuccess = { latestGroups = latestGroups + listOf(it) },
                onFailure = { latestFailed = latestFailed + sourceName },
            )
            publish(searching = true)
        }
    }

    private fun publish(searching: Boolean) {
        val merged = LyricCandidateRanker.dropMatching(
            LyricCandidateRanker.merge(latestGroups),
            currentState.bestMatched?.lyrics,
        )
        updateState {
            copy(
                candidates = merged.map { it.toItem() },
                isSearching = searching,
                searchNote = latestFailed.takeIf { it.isNotEmpty() }
                    ?.joinToString("、")
                    ?.let { "部分来源暂不可用：$it" },
            )
        }
    }

    /**
     * 网易云只取第一小页再按相似度留前 [LyricCandidateRanker.NETEASE_LIMIT] 条。
     * 以前标题全等就继续翻页，同名歌会把列表撑得很长。
     */
    private suspend fun neteaseCandidates(query: LyricQuery): List<ScoredLyricHit> {
        if (query.title.isBlank()) return emptyList()
        val page = searchRepository.searchMusic(
            keyword = query.title,
            page = 1,
            limit = LyricCandidateRanker.NETEASE_FETCH,
            platform = "netease",
        ).getOrThrow()
        val hits = page.list.mapNotNull { music ->
            val raw = music.lyrics?.trim().orEmpty()
            if (raw.isEmpty()) return@mapNotNull null
            val prepared = LyricTextNormalizer.prepare(raw, music.duration)
            if (!prepared.ok) return@mapNotNull null
            LyricHit(
                source = "netease",
                sourceId = music.sourceId,
                title = music.title,
                artist = music.artists.joinToString(" / ") { it.name },
                album = music.album?.title,
                durationMs = music.duration,
                lyrics = prepared.lrc,
                lyricsTrans = music.lyricsTrans?.takeIf { it.isNotBlank() },
                synced = prepared.synced,
            )
        }
        return LyricCandidateRanker.select(query, hits, LyricCandidateRanker.NETEASE_LIMIT)
    }

    private fun applyPrepared(raw: String) {
        val prepared = LyricTextNormalizer.prepare(raw, currentState.currentDurationMs)
        if (!prepared.ok) {
            updateState { copy(showCustom = true, customError = prepared.message) }
            return
        }
        applyLyrics(prepared.lrc, null)
    }

    private fun applyLyrics(lyrics: String, lyricsTrans: String?) {
        val currentMusicId = musicController.playerState.value.currentMusic?.id
        if (currentMusicId != null) {
            musicController.updateMusicLyrics(currentMusicId, lyrics, lyricsTrans)
        }
        sendEffect(CustomLyricSelectorEffect.ShowToast("歌词已更换"))
        sendEffect(CustomLyricSelectorEffect.Dismiss)
    }

    private fun toLyricItem(lyric: ScrapedLyric): CustomLyricItem? {
        val text = lyric.lyrics
        if (text.isBlank() || lyric.instrumental) return null
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

    private fun ScoredLyricHit.toItem(): CustomLyricItem {
        val music = Music(
            id = 0L,
            sourceId = hit.sourceId ?: "0",
            title = hit.title,
            sourcePlatform = hit.source,
            url = "",
            duration = hit.durationMs,
            artists = listOfNotNull(
                hit.artist.takeIf { it.isNotBlank() }?.let { Artist(id = 0L, name = it) }
            ),
            album = hit.album?.takeIf { it.isNotBlank() }?.let { Album(id = 0L, title = it) },
            lyrics = hit.lyrics,
            lyricsTrans = hit.lyricsTrans,
        )
        return CustomLyricItem(
            music = music,
            lyrics = hit.lyrics,
            lyricsTrans = hit.lyricsTrans,
            lyricLineCount = LyricsParser.parseLrc(hit.lyrics).size,
            matchScore = score,
            attributionUrl = hit.attributionUrl,
            synced = hit.synced,
        )
    }
}
