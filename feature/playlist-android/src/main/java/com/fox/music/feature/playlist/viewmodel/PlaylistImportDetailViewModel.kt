package com.fox.music.feature.playlist.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.ImportTrackItem
import com.fox.music.core.domain.repository.PlaylistImportDetail
import com.fox.music.core.domain.repository.PlaylistImportProgressRepository
import com.fox.music.core.domain.usecase.ResolveThirdPartyAudioUseCase
import com.fox.music.core.domain.usecase.SearchProviderCandidatesUseCase
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.CandidateResolveParams
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 歌单导入详情：逐首上传状态 + 失败曲目手动指定音源。
 *
 * 「手动选择音源」与音源纠错同一条链路：按歌名/艺人拉多源候选 → 解析直链 →
 * 试听确认 → 确认后服务端转存音源、建歌、加回歌单。
 */
data class PlaylistImportDetailState(
    val detail: PlaylistImportDetail? = null,
    val isLoading: Boolean = true,
    /** 任务已被清理（全部成功或主动取消） */
    val jobMissing: Boolean = false,
    /** 一次性提示，展示后由界面消费 */
    val message: String? = null,
    // —— 音源选择面板 ——
    val pickerTrack: ImportTrackItem? = null,
    val query: String = "",
    val artistQuery: String = "",
    val isSearching: Boolean = false,
    val groups: List<ProviderCandidateGroup> = emptyList(),
    val activeGroupKey: String = "",
    val selectedCandidateId: String? = null,
    val resolvingIds: Set<String> = emptySet(),
    /** 候选 id -> 已解析出的可播直链 */
    val resolvedUrls: Map<String, String> = emptyMap(),
    val applying: Boolean = false,
    val previewCandidateId: String? = null,
    val isPreviewPlaying: Boolean = false,
    val previewPositionMs: Long = 0L,
    val previewDurationMs: Long = 0L,
    /** 原曲歌词（导入时记录的 platform/externalId 拉取），用于对照候选音源是否同曲 */
    val trackLyrics: List<LyricsParser.LyricLine> = emptyList(),
    val isLyricsLoading: Boolean = false,
) : UiState {

    val activeGroup: ProviderCandidateGroup?
        get() = groups.firstOrNull { it.key == activeGroupKey } ?: groups.firstOrNull()

    val activeCandidates: List<ProviderCandidate>
        get() = activeGroup?.items.orEmpty()

    /**
     * 面板展示用排序：同艺人优先，其次时长最接近歌单标注的优先。
     *
     * hifini 四站返回的是站点搜索结果（宽松匹配），实测搜「Dancing King」会掺进
     * 《Song for a King》《WaKing Up》这类完全无关的歌；不排序用户要在 20~50 条里翻。
     */
    val sortedCandidates: List<ProviderCandidate>
        get() {
            val target = pickerTrack?.artists?.substringBefore(",")?.trim()?.lowercase().orEmpty()
            val targetDuration = pickerTrack?.durationMs ?: 0L
            return activeCandidates.sortedWith(
                compareByDescending<ProviderCandidate> {
                    target.isNotBlank() && it.artist.lowercase().contains(target)
                }.thenBy {
                    val duration = it.durationMs ?: 0L
                    if (targetDuration > 0 && duration > 0) {
                        kotlin.math.abs(duration - targetDuration)
                    } else {
                        Long.MAX_VALUE
                    }
                },
            )
        }

    val selectedCandidate: ProviderCandidate?
        get() = groups.asSequence().flatMap { it.items }
            .firstOrNull { it.id == selectedCandidateId }

    /** 已选中且已拿到直链 */
    val selectedResolvedUrl: String?
        get() = selectedCandidateId?.let { resolvedUrls[it] }

    val canApply: Boolean
        get() = !applying && selectedCandidate != null && !selectedResolvedUrl.isNullOrBlank()
}

sealed interface PlaylistImportDetailIntent : UiIntent {
    data object RetryAll : PlaylistImportDetailIntent
    data class RetryTrack(val trackJobId: String) : PlaylistImportDetailIntent
    data object ResumeJob : PlaylistImportDetailIntent
    data object CancelJob : PlaylistImportDetailIntent
    data class OpenSourcePicker(val trackJobId: String) : PlaylistImportDetailIntent
    data object CloseSourcePicker : PlaylistImportDetailIntent
    data class EditQuery(val title: String, val artist: String) : PlaylistImportDetailIntent
    data object SearchProviders : PlaylistImportDetailIntent
    data class SelectGroup(val key: String) : PlaylistImportDetailIntent
    data class SelectCandidate(val candidate: ProviderCandidate) : PlaylistImportDetailIntent
    data class ToggleCandidatePlay(val candidate: ProviderCandidate) : PlaylistImportDetailIntent
    data object TogglePreviewPlayPause : PlaylistImportDetailIntent
    data class SeekPreview(val positionMs: Long) : PlaylistImportDetailIntent
    data object StopPreview : PlaylistImportDetailIntent
    data object ApplySelected : PlaylistImportDetailIntent
    data object ConsumeMessage : PlaylistImportDetailIntent
}

sealed interface PlaylistImportDetailEffect : UiEffect {
    data class ShowMessage(val message: String) : PlaylistImportDetailEffect
    data object NavigateBack : PlaylistImportDetailEffect
}

@HiltViewModel
class PlaylistImportDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val progressRepository: PlaylistImportProgressRepository,
    private val importRepository: ImportRepository,
    private val searchProviderCandidatesUseCase: SearchProviderCandidatesUseCase,
    private val resolveThirdPartyAudioUseCase: ResolveThirdPartyAudioUseCase,
) : MviViewModel<PlaylistImportDetailState, PlaylistImportDetailIntent, PlaylistImportDetailEffect>(
    PlaylistImportDetailState(),
) {

    val playlistId: Long = savedStateHandle.get<String>("playlistId")?.toLongOrNull() ?: 0L

    private var mediaPlayer: MediaPlayer? = null
    private var previewTicker: Job? = null

    /**
     * 请求归属令牌：`trackJobId#自增序号`。
     *
     * 面板是复用的，切曲目只会重置 state 而不是取消请求。若无令牌，A 曲目慢回来的
     * 结果会直接写进 B 曲目的 state（实测：点 3.6.5 没等回来又点 24/7，结果 3.6.5
     * 的候选盖住了 24/7）。现在回包先认令牌，不是当前页就只落缓存、绝不碰 UI。
     */
    private var pickerRequestToken: String = ""
    private var requestSeq: Int = 0

    /** 候选搜索缓存：key = trackJobId|歌名|艺人，切回同一首不再打网络 */
    private val searchCache = mutableMapOf<String, List<ProviderCandidateGroup>>()

    /** 直链解析缓存：key = resolveProvider|candidateId，跨曲目复用（同候选只解析一次） */
    private val resolveCache = mutableMapOf<String, String>()

    private fun searchCacheKey(trackJobId: String, title: String, artist: String?): String =
        "$trackJobId|$title|${artist.orEmpty()}"

    private fun candidateCacheKey(candidate: ProviderCandidate): String =
        "${candidate.resolveProvider.orEmpty()}|${candidate.id}"

    /** 回包时判断是否仍属于当前面板；旧请求一律只落缓存 */
    private fun isCurrentRequest(token: String): Boolean =
        token == pickerRequestToken && currentState.pickerTrack != null

    init {
        viewModelScope.launch {
            progressRepository.observeDetail(playlistId)
                .collect { detail ->
                    updateState {
                        copy(
                            detail = detail,
                            isLoading = false,
                            jobMissing = detail == null,
                        )
                    }
                }
        }
    }

    override fun onCleared() {
        releasePreview()
        super.onCleared()
    }

    override fun handleIntent(intent: PlaylistImportDetailIntent) {
        when (intent) {
            PlaylistImportDetailIntent.RetryAll -> retryAll()
            is PlaylistImportDetailIntent.RetryTrack -> retryTrack(intent.trackJobId)
            PlaylistImportDetailIntent.ResumeJob -> resumeJob()
            PlaylistImportDetailIntent.CancelJob -> cancelJob()
            is PlaylistImportDetailIntent.OpenSourcePicker -> openSourcePicker(intent.trackJobId)
            PlaylistImportDetailIntent.CloseSourcePicker -> closeSourcePicker()
            is PlaylistImportDetailIntent.EditQuery -> {
                updateState { copy(query = intent.title, artistQuery = intent.artist) }
                searchProviders()
            }
            PlaylistImportDetailIntent.SearchProviders -> searchProviders()
            is PlaylistImportDetailIntent.SelectGroup -> {
                if (currentState.activeGroupKey == intent.key) return
                releasePreview()
                updateState {
                    copy(
                        activeGroupKey = intent.key,
                        selectedCandidateId = null,
                        previewCandidateId = null,
                        isPreviewPlaying = false,
                    )
                }
            }
            is PlaylistImportDetailIntent.SelectCandidate -> selectCandidate(intent.candidate)
            is PlaylistImportDetailIntent.ToggleCandidatePlay -> toggleCandidatePlay(intent.candidate)
            PlaylistImportDetailIntent.TogglePreviewPlayPause -> togglePreviewPlayPause()
            is PlaylistImportDetailIntent.SeekPreview -> seekPreview(intent.positionMs)
            PlaylistImportDetailIntent.StopPreview -> releasePreview()
            PlaylistImportDetailIntent.ApplySelected -> viewModelScope.launch { applySelected() }
            PlaylistImportDetailIntent.ConsumeMessage -> updateState { copy(message = null) }
        }
    }

    private fun retryAll() {
        val jobId = currentState.detail?.jobId ?: return
        viewModelScope.launch {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("已重新排队，稍后自动重试"))
            progressRepository.retryFailed(jobId)
        }
    }

    private fun retryTrack(trackJobId: String) {
        val detail = currentState.detail
        if (detail?.status == "paused") {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("任务已暂停，请先继续导入"))
            return
        }
        viewModelScope.launch {
            progressRepository.retryTrack(trackJobId)
            sendEffect(PlaylistImportDetailEffect.ShowMessage("已重新排队该曲目"))
        }
    }

    private fun resumeJob() {
        val jobId = currentState.detail?.jobId ?: return
        viewModelScope.launch {
            progressRepository.resume(jobId)
            sendEffect(PlaylistImportDetailEffect.ShowMessage("已继续导入"))
        }
    }

    private fun cancelJob() {        val jobId = currentState.detail?.jobId ?: return
        viewModelScope.launch {
            progressRepository.cancelFailed(jobId)
            sendEffect(PlaylistImportDetailEffect.ShowMessage("已取消剩余导入"))
            sendEffect(PlaylistImportDetailEffect.NavigateBack)
        }
    }

    private fun openSourcePicker(trackJobId: String) {
        val track = currentState.detail?.tracks?.firstOrNull { it.trackJobId == trackJobId } ?: return
        releasePreview()
        val title = track.title
        val artist = track.artists.substringBefore(",").trim().ifBlank { null }
        val cachedGroups = searchCache[searchCacheKey(trackJobId, title, artist)]
        val cachedLyrics = LyricsParser.parseLrc(track.lyrics.orEmpty())
        // 换曲目立刻递增令牌：上一首还在飞的请求回来时只能落缓存
        pickerRequestToken = "$trackJobId#${++requestSeq}"
        updateState {
            copy(
                pickerTrack = track,
                query = title,
                artistQuery = artist.orEmpty(),
                groups = cachedGroups.orEmpty(),
                activeGroupKey = cachedGroups?.firstOrNull()?.key.orEmpty(),
                selectedCandidateId = null,
                resolvingIds = emptySet(),
                resolvedUrls = emptyMap(),
                isSearching = cachedGroups == null,
                previewCandidateId = null,
                isPreviewPlaying = false,
                previewPositionMs = 0L,
                previewDurationMs = 0L,
                trackLyrics = cachedLyrics,
                isLyricsLoading = cachedLyrics.isEmpty(),
                applying = false,
            )
        }
        if (cachedLyrics.isEmpty()) loadTrackLyrics(track)
        // 命中缓存就不再请求；原样复用上次结果
        if (cachedGroups == null) searchProviders()
    }

    /**
     * 「原曲歌词」：优先用入队时预取落库的歌词（离线可用、零等待），
     * 老数据没有缓存时才实时拉一次，成功后回写，下次直接读库。
     */
    private fun loadTrackLyrics(track: ImportTrackItem) {
        val cached = track.lyrics
        if (!cached.isNullOrBlank()) {
            updateState {
                copy(isLyricsLoading = false, trackLyrics = LyricsParser.parseLrc(cached))
            }
            return
        }
        val platform = track.platform
        val externalId = track.externalId
        if (platform.isNullOrBlank() || externalId.isNullOrBlank()) {
            updateState { copy(isLyricsLoading = false, trackLyrics = emptyList()) }
            return
        }
        val owner = track.trackJobId
        viewModelScope.launch {
            val result = importRepository.fetchLyrics(platform, externalId)
            // 已经切到别的曲目：结果只落库缓存，不写当前页
            val current = currentState.pickerTrack?.trackJobId == owner
            val lrc = if (result is Result.Success) result.data.lyric else null
            if (!lrc.isNullOrBlank()) {
                runCatching { progressRepository.cacheTrackLyrics(owner, lrc) }
            }
            if (!current) return@launch
            updateState {
                copy(
                    isLyricsLoading = false,
                    trackLyrics = LyricsParser.parseLrc(lrc.orEmpty()),
                )
            }
        }
    }

    private fun closeSourcePicker() {
        // 关闭后作废在飞请求：它们回来时不该再污染下一次打开的面板
        pickerRequestToken = ""
        releasePreview()
        updateState {
            copy(
                pickerTrack = null,
                groups = emptyList(),
                activeGroupKey = "",
                selectedCandidateId = null,
                resolvedUrls = emptyMap(),
                previewCandidateId = null,
                isPreviewPlaying = false,
                isSearching = false,
            )
        }
    }

    /** 按歌名 + 艺人拉各音源渠道候选（与音源纠错同一接口） */
    private fun searchProviders() {
        val track = currentState.pickerTrack ?: return
        val title = currentState.query.trim().ifBlank { track.title }
        val artist = currentState.artistQuery.trim().ifBlank { null }
        val cacheKey = searchCacheKey(track.trackJobId, title, artist)
        // 缓存命中：直接还原，不打网络
        searchCache[cacheKey]?.let { cached ->
            updateState {
                copy(
                    isSearching = false,
                    groups = cached,
                    activeGroupKey = cached.firstOrNull()?.key.orEmpty(),
                    resolvedUrls = restoreResolved(cached),
                )
            }
            return
        }
        val token = "${track.trackJobId}#${++requestSeq}"
        pickerRequestToken = token
        viewModelScope.launch {
            releasePreview()
            updateState {
                copy(
                    isSearching = true,
                    groups = emptyList(),
                    activeGroupKey = "",
                    selectedCandidateId = null,
                    resolvingIds = emptySet(),
                    resolvedUrls = emptyMap(),
                )
            }
            val result = searchProviderCandidatesUseCase(
                title = title,
                artist = artist,
                durationMs = track.durationMs.takeIf { it > 0 },
            )
            when (result) {
                is Result.Success -> {
                    // 无论是否当前页，结果都缓存下来（后台跑完也不浪费）
                    searchCache[cacheKey] = result.data
                    if (!isCurrentRequest(token)) return@launch
                    updateState {
                        copy(
                            isSearching = false,
                            groups = result.data,
                            activeGroupKey = result.data.firstOrNull()?.key.orEmpty(),
                            resolvedUrls = restoreResolved(result.data),
                        )
                    }
                }
                is Result.Error -> {
                    if (!isCurrentRequest(token)) return@launch
                    updateState { copy(isSearching = false) }
                    sendEffect(
                        PlaylistImportDetailEffect.ShowMessage(
                            result.message ?: "多源搜索失败",
                        )
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    /** 把之前已解析过的候选直链还原回列表（同一候选不重复解析） */
    private fun restoreResolved(groups: List<ProviderCandidateGroup>): Map<String, String> =
        groups.asSequence()
            .flatMap { it.items }
            .mapNotNull { c -> resolveCache[candidateCacheKey(c)]?.let { c.id to it } }
            .toMap()

    private fun selectCandidate(candidate: ProviderCandidate) {
        if (!candidate.canSelect) {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("该候选无法解析音源，不能选择"))
            return
        }
        val cached = currentState.resolvedUrls[candidate.id] ?: resolveCache[candidateCacheKey(candidate)]
        if (cached != null) {
            updateState {
                copy(
                    selectedCandidateId = candidate.id,
                    resolvedUrls = resolvedUrls + (candidate.id to cached),
                )
            }
            return
        }
        if (candidate.playType == CandidatePlayType.DIRECT) {
            val direct = candidate.directUrl
            if (!direct.isNullOrBlank()) {
                resolveCache[candidateCacheKey(candidate)] = direct
                updateState {
                    copy(
                        selectedCandidateId = candidate.id,
                        resolvedUrls = resolvedUrls + (candidate.id to direct),
                    )
                }
                return
            }
        }
        updateState { copy(selectedCandidateId = candidate.id) }
        resolveCandidate(candidate) { url ->
            if (url != null) {
                updateState { copy(resolvedUrls = resolvedUrls + (candidate.id to url)) }
            }
        }
    }

    /** 点击右侧播放/解析：未解析先解析，成功后自动试听；正在播则暂停 */
    private fun toggleCandidatePlay(candidate: ProviderCandidate) {
        if (!candidate.canSelect) {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("该候选无法解析音源"))
            return
        }
        val url = currentState.resolvedUrls[candidate.id]
            ?: candidate.directUrl
            ?: resolveCache[candidateCacheKey(candidate)]
        if (url.isNullOrBlank()) {
            resolveCandidate(candidate) { resolved ->
                if (resolved != null) {
                    updateState {
                        copy(
                            resolvedUrls = resolvedUrls + (candidate.id to resolved),
                            selectedCandidateId = candidate.id,
                        )
                    }
                    startPreview(candidate, resolved)
                }
            }
            return
        }
        if (currentState.previewCandidateId == candidate.id && currentState.isPreviewPlaying) {
            togglePreviewPlayPause()
            return
        }
        updateState { copy(selectedCandidateId = candidate.id) }
        startPreview(candidate, url)
    }

    /**
     * 解析候选直链。回包带令牌校验：用户已切到别的曲目时只写解析缓存，
     * 不更新 state、不弹 toast、不启动试听（否则会「新页面上放旧歌」）。
     */
    private fun resolveCandidate(candidate: ProviderCandidate, onDone: (String?) -> Unit) {
        if (currentState.resolvingIds.contains(candidate.id)) return
        updateState { copy(resolvingIds = resolvingIds + candidate.id) }
        val token = pickerRequestToken
        val cacheKey = candidateCacheKey(candidate)
        val req = ResolveThirdPartyRequest(
            title = candidate.title,
            artist = candidate.artist.ifBlank { null },
            durationMs = candidate.durationMs,
            resolveProvider = candidate.resolveProvider,
            resolveParams = candidate.resolveParams?.let {
                CandidateResolveParams(
                    site = it.site,
                    detailUrl = it.detailUrl,
                    title = it.title,
                    artist = it.artist,
                    durationMs = it.durationMs,
                )
            },
        )
        viewModelScope.launch {
            when (val result = resolveThirdPartyAudioUseCase(req)) {
                is Result.Success -> {
                    val url = result.data.url.takeIf { it.isNotBlank() }
                    if (url != null) resolveCache[cacheKey] = url
                    if (!isCurrentRequest(token)) return@launch
                    updateState { copy(resolvingIds = resolvingIds - candidate.id) }
                    onDone(url)
                }
                is Result.Error -> {
                    if (!isCurrentRequest(token)) return@launch
                    updateState { copy(resolvingIds = resolvingIds - candidate.id) }
                    sendEffect(
                        PlaylistImportDetailEffect.ShowMessage(
                            result.message ?: "音源解析失败",
                        )
                    )
                    onDone(null)
                }
                Result.Loading -> Unit
            }
        }
    }

    /** 确认用当前音源导入该曲目 */
    private suspend fun applySelected() {
        val track = currentState.pickerTrack ?: return
        val candidate = currentState.selectedCandidate ?: return
        val url = currentState.selectedResolvedUrl
        if (url.isNullOrBlank()) {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("请先选择并解析一首候选音源"))
            return
        }
        updateState { copy(applying = true) }
        releasePreview()
        when (
            val result = progressRepository.applyManualSource(
                trackJobId = track.trackJobId,
                url = url,
                referer = candidate.referer,
                durationMs = candidate.durationMs,
            )
        ) {
            is Result.Success -> {
                updateState { copy(applying = false) }
                closeSourcePicker()
                sendEffect(
                    PlaylistImportDetailEffect.ShowMessage("已导入：${track.title}"),
                )
            }
            is Result.Error -> {
                updateState { copy(applying = false) }
                sendEffect(
                    PlaylistImportDetailEffect.ShowMessage(
                        result.message ?: "导入失败",
                    )
                )
            }
            Result.Loading -> updateState { copy(applying = false) }
        }
    }

    private fun startPreview(candidate: ProviderCandidate, url: String) {
        if (url.isBlank()) {
            sendEffect(PlaylistImportDetailEffect.ShowMessage("当前候选没有可播地址"))
            return
        }
        releasePreview()
        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(url)
                setOnPreparedListener { mp ->
                    mp.start()
                    updateState {
                        copy(
                            previewCandidateId = candidate.id,
                            isPreviewPlaying = true,
                            previewPositionMs = 0L,
                            previewDurationMs = mp.duration.toLong().coerceAtLeast(0L),
                        )
                    }
                    startPreviewTicker()
                }
                setOnCompletionListener {
                    previewTicker?.cancel()
                    updateState {
                        copy(isPreviewPlaying = false, previewPositionMs = previewDurationMs)
                    }
                }
                setOnErrorListener { _, _, _ ->
                    releasePreview()
                    sendEffect(PlaylistImportDetailEffect.ShowMessage("当前候选无法试听"))
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            updateState {
                copy(
                    previewCandidateId = candidate.id,
                    isPreviewPlaying = false,
                    previewPositionMs = 0L,
                    previewDurationMs = candidate.durationMs?.coerceAtLeast(0L) ?: 0L,
                )
            }
        } catch (_: Exception) {
            releasePreview()
            sendEffect(PlaylistImportDetailEffect.ShowMessage("当前候选无法试听"))
        }
    }

    /** 进度心跳：歌词高亮与进度条都依赖它（与音源纠错同款 200ms） */
    private fun startPreviewTicker() {
        previewTicker?.cancel()
        previewTicker = viewModelScope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                try {
                    updateState {
                        copy(
                            previewPositionMs = player.currentPosition.toLong(),
                            previewDurationMs = player.duration.toLong().coerceAtLeast(0L),
                            isPreviewPlaying = player.isPlaying,
                        )
                    }
                } catch (_: Exception) {
                    break
                }
                delay(200)
            }
        }
    }

    private fun togglePreviewPlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                previewTicker?.cancel()
                updateState { copy(isPreviewPlaying = false) }
            } else {
                player.start()
                startPreviewTicker()
                updateState { copy(isPreviewPlaying = true) }
            }
        } catch (_: Exception) {
            releasePreview()
        }
    }

    private fun seekPreview(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            val target = positionMs.coerceIn(0L, player.duration.toLong().coerceAtLeast(0L))
            player.seekTo(target.toInt())
            updateState { copy(previewPositionMs = target) }
        } catch (_: Exception) {
        }
    }

    private fun releasePreview() {
        previewTicker?.cancel()
        previewTicker = null
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        updateState {
            copy(
                previewCandidateId = null,
                isPreviewPlaying = false,
                previewPositionMs = 0L,
                previewDurationMs = 0L,
            )
        }
    }
}
