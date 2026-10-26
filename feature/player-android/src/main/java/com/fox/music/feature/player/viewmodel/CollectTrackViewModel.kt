package com.fox.music.feature.player.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.usecase.CheckMusicExistsUseCase
import com.fox.music.core.domain.usecase.FetchScrapedLyricsUseCase
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.ImportSingleTrackUseCase
import com.fox.music.core.domain.usecase.ResolveThirdPartyAudioUseCase
import com.fox.music.core.domain.usecase.SearchProviderCandidatesUseCase
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.CandidateResolveParams
import com.fox.music.core.model.music.ImportSingleRequest
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.MusicExists
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectTrackState(
    /** 入口带入的原始元数据（网易云/QQ 一级页所选） */
    val argsTitle: String = "",
    val argsArtist: String = "",
    val argsPlatform: String = "",
    val argsExternalId: String = "",
    val argsCoverUrl: String? = null,
    val argsDurationMs: Long = 0,
    val argsAlbum: String = "",
    val mode: String = "search",
    /** 可编辑的名称/艺人（默认带入原始元数据，宽松匹配） */
    val editTitle: String = "",
    val editArtist: String = "",
    val isSearching: Boolean = false,
    /** 各音源渠道候选（一个渠道一个 tab） */
    val groups: List<ProviderCandidateGroup> = emptyList(),
    val activeGroupKey: String = "",
    val resolvingIds: Set<String> = emptySet(),
    val resolvedUrls: Map<String, String> = emptyMap(),
    val selectedCandidateId: String? = null,
    /** 试听状态 */
    val previewCandidateId: String? = null,
    val preview: PreviewPlaybackState = PreviewPlaybackState(),
    /** 平台已收录时拉到的歌词（试听对照） */
    val previewLyrics: List<LyricsParser.LyricLine> = emptyList(),
    /** 预拉的刮削歌词（LRCLIB/lyrics.ovh），收录时随 import-single 落库 */
    val prefetchedLyrics: String? = null,
    val isPrefetchingLyrics: Boolean = false,
    /** 是否已收录到平台 */
    val exists: MusicExists? = null,
    val isCheckingExists: Boolean = false,
    /** 收录流程 */
    val showCollectChoice: Boolean = false,
    val showPlaylistPicker: Boolean = false,
    val playlists: List<Playlist> = emptyList(),
    val isPlaylistsLoading: Boolean = false,
    val isCollecting: Boolean = false,
    val collectResult: CollectResult? = null,
) : UiState {
    val activeGroup: ProviderCandidateGroup?
        get() = groups.firstOrNull { it.key == activeGroupKey } ?: groups.firstOrNull()

    val activeCandidates: List<ProviderCandidate>
        get() = activeGroup?.items.orEmpty()

    val selectedCandidate: ProviderCandidate?
        get() = groups.asSequence().flatMap { it.items }.firstOrNull { it.id == selectedCandidateId }

    val resolvedCandidate: ProviderCandidate?
        get() = selectedCandidate?.takeIf { resolvedUrls.containsKey(it.id) }
}

data class CollectResult(
    val musicId: Long,
    val isNew: Boolean,
    val toPlaylist: Boolean,
)

sealed interface CollectTrackIntent : UiIntent {
    data object Init : CollectTrackIntent
    data class EditTitleChange(val value: String) : CollectTrackIntent
    data class EditArtistChange(val value: String) : CollectTrackIntent
    data object SearchProviders : CollectTrackIntent
    data class SelectGroup(val key: String) : CollectTrackIntent
    data class SelectCandidate(val candidate: ProviderCandidate) : CollectTrackIntent
    data class ToggleCandidatePlay(val candidate: ProviderCandidate) : CollectTrackIntent
    data class SeekPreview(val positionMs: Long) : CollectTrackIntent
    data object TogglePreviewPlayPause : CollectTrackIntent
    data object StopPreview : CollectTrackIntent
    data object ShowCollectChoice : CollectTrackIntent
    data object DismissCollectChoice : CollectTrackIntent
    data object CollectToPlatform : CollectTrackIntent
    data object CollectToPlaylist : CollectTrackIntent
    data object DismissPlaylistPicker : CollectTrackIntent
    data class PickPlaylist(val playlistId: Long) : CollectTrackIntent
    data object ConsumeCollectResult : CollectTrackIntent
}

sealed interface CollectTrackEffect : UiEffect {
    data class ShowMessage(val message: String) : CollectTrackEffect
    data object CollectSucceeded : CollectTrackEffect
}

@HiltViewModel
class CollectTrackViewModel @Inject constructor(
    private val searchProviderCandidatesUseCase: SearchProviderCandidatesUseCase,
    private val resolveThirdPartyAudioUseCase: ResolveThirdPartyAudioUseCase,
    private val checkMusicExistsUseCase: CheckMusicExistsUseCase,
    private val importSingleTrackUseCase: ImportSingleTrackUseCase,
    private val fetchScrapedLyricsUseCase: FetchScrapedLyricsUseCase,
    private val playlistRepository: PlaylistRepository,
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
) : MviViewModel<CollectTrackState, CollectTrackIntent, CollectTrackEffect>(CollectTrackState()) {

    private var initArgs: CollectTrackArgs? = null
    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null
    private var lyricsPrefetchJob: Job? = null

    /**
     * 用户最后一次主动点选/试听的候选 id。
     *
     * 解析是异步的（hifini 站点逆向 5~20s），若 A tab 的解析在用户切到 B tab
     * 并选中 B 之后才返回，旧回调会把选中项改回 A —— 表现为「其他 tab 勾选音源，
     * 最终提交的还是之前那个」（用户 2026-10-22 反馈）。所以异步回调只在
     * 「它仍然是最新一次交互」时才允许改选中项与起播；解析结果本身照样进缓存。
     */
    private var lastInteractionCandidateId: String? = null

    /**
     * 搜索序号：换搜索词时旧请求可能比新请求晚返回（服务端多渠道并发 5~20s），
     * 若直接写回会把上一次的结果覆盖到新词上（用户 2026-10-22 反馈：换词后还显示旧结果）。
     * 回调只在「仍是最近一次搜索」时才允许写回。
     */
    private var searchSeq = 0L

    override fun onCleared() {
        stopPreviewInternal()
        super.onCleared()
    }

    override fun handleIntent(intent: CollectTrackIntent) {
        when (intent) {
            CollectTrackIntent.Init -> initArgs?.let { init(it) }
            is CollectTrackIntent.EditTitleChange -> {
                updateState { copy(editTitle = intent.value) }
                scheduleLyricsReprefetch()
            }
            is CollectTrackIntent.EditArtistChange -> {
                updateState { copy(editArtist = intent.value) }
                scheduleLyricsReprefetch()
            }
            CollectTrackIntent.SearchProviders -> searchProviders()
            is CollectTrackIntent.SelectGroup -> {
                // 切换渠道：清空选择与试听，底部「提交收录」随之禁用
                // （用户 2026-10-21：切 tab 后没有选择 item，按钮不应可点）
                if (currentState.activeGroupKey == intent.key) return
                stopPreviewInternal()
                updateState {
                    copy(
                        activeGroupKey = intent.key,
                        selectedCandidateId = null,
                    )
                }
            }
            is CollectTrackIntent.SelectCandidate -> selectCandidate(intent.candidate)
            is CollectTrackIntent.ToggleCandidatePlay -> toggleCandidatePlay(intent.candidate)
            is CollectTrackIntent.SeekPreview -> seekPreview(intent.positionMs)
            CollectTrackIntent.TogglePreviewPlayPause -> togglePreviewPlayPause()
            CollectTrackIntent.StopPreview -> stopPreviewInternal()
            CollectTrackIntent.ShowCollectChoice ->
                updateState { copy(showCollectChoice = true) }
            CollectTrackIntent.DismissCollectChoice ->
                updateState { copy(showCollectChoice = false) }
            CollectTrackIntent.CollectToPlatform -> {
                updateState { copy(showCollectChoice = false) }
                collect(playlistId = null)
            }
            CollectTrackIntent.CollectToPlaylist -> {
                updateState { copy(showCollectChoice = false, showPlaylistPicker = true, isPlaylistsLoading = true) }
                loadPlaylists()
            }
            CollectTrackIntent.DismissPlaylistPicker ->
                updateState { copy(showPlaylistPicker = false) }
            is CollectTrackIntent.PickPlaylist -> {
                updateState { copy(showPlaylistPicker = false) }
                collect(playlistId = intent.playlistId)
            }
            CollectTrackIntent.ConsumeCollectResult ->
                updateState { copy(collectResult = null) }
        }
    }

    fun initWithArgs(args: CollectTrackArgs) {
        initArgs = args
        updateState {
            copy(
                argsTitle = args.title,
                argsArtist = args.artist,
                argsPlatform = args.platform,
                argsExternalId = args.externalId,
                argsCoverUrl = args.coverUrl,
                argsDurationMs = args.durationMs,
                argsAlbum = args.album,
                mode = args.mode,
                editTitle = args.title,
                editArtist = args.artist,
            )
        }
        handleIntent(CollectTrackIntent.Init)
    }

    private fun init(args: CollectTrackArgs) {
        // 自动按带入的元数据做一次多渠道搜索（宽松匹配：live/dj 版本也展示）
        searchProviders()
        // 同时查询平台是否已收录（命中则拉歌词供试听对照）
        checkExists(args.title, args.artist)
        // 预拉刮削歌词（LRCLIB 优先 + lyrics.ovh 兜底）：用户浏览/试听期间后台拉，
        // 收录时带上落库（2026-10-21 用户要求歌词入库；平台歌词没有时才用刮削的）
        prefetchLyrics(args.title, args.artist, args.durationMs)
    }

    /**
     * 预拉刮削歌词。编辑过名称/艺人后按当前值重拉一次（用户可能修正了错别字）。
     * 失败静默（国际链路不稳，尽力而为），不阻塞任何交互。
     */
    private fun prefetchLyrics(title: String, artist: String, durationMs: Long) {
        val t = title.trim()
        val a = artist.trim()
        if (t.isBlank() || a.isBlank()) return
        lyricsPrefetchJob?.cancel()
        lyricsPrefetchJob = viewModelScope.launch {
            updateState { copy(isPrefetchingLyrics = true) }
            val result = fetchScrapedLyricsUseCase(t, a, durationMs.takeIf { it > 0 })
            if (result is Result.Success) {
                updateState {
                    copy(
                        prefetchedLyrics = result.data,
                        isPrefetchingLyrics = false,
                        // 对照面板兜底：平台库歌词没命中时（未收录/标题不同），
                        // 用刮削歌词渲染，避免「暂无歌词」；平台歌词后到会覆盖它
                        previewLyrics = previewLyrics.ifEmpty {
                            LyricsParser.parseLrc(result.data.orEmpty())
                        },
                    )
                }
            } else {
                updateState { copy(isPrefetchingLyrics = false) }
            }
        }
    }

    /** 编辑名称/艺人后 800ms 防抖重拉（用户停手才发请求） */
    private fun scheduleLyricsReprefetch() {
        lyricsPrefetchJob?.cancel()
        lyricsPrefetchJob = viewModelScope.launch {
            delay(800)
            val s = currentState
            prefetchLyrics(s.editTitle, s.editArtist, s.argsDurationMs)
        }
    }

    private fun checkExists(title: String, artist: String) {
        viewModelScope.launch {
            updateState { copy(isCheckingExists = true) }
            when (val result = checkMusicExistsUseCase(title, artist.ifBlank { null })) {
                is Result.Success -> {
                    updateState { copy(exists = result.data, isCheckingExists = false) }
                    // 已收录：拉平台内歌词，试听时对照展示
                    val musicId = result.data.musicId
                    if (result.data.exists && musicId != null && musicId > 0) {
                        getMusicDetailUseCase(musicId).onSuccess { detail ->
                            updateState {
                                copy(previewLyrics = LyricsParser.parseLrc(detail.music.lyrics.orEmpty()))
                            }
                        }
                    }
                }
                is Result.Error -> updateState { copy(exists = null, isCheckingExists = false) }
                Result.Loading -> Unit
            }
        }
    }

    /** 多渠道候选搜索（宽松匹配，不解析直链） */
    private fun searchProviders() {
        val title = currentState.editTitle.trim()
        if (title.isBlank()) {
            sendEffect(CollectTrackEffect.ShowMessage("请输入歌名"))
            return
        }
        val artist = currentState.editArtist.trim().ifBlank { null }
        val durationMs = currentState.argsDurationMs.takeIf { it > 0 }
        val seq = ++searchSeq
        viewModelScope.launch {
            updateState {
                copy(
                    isSearching = true,
                    groups = emptyList(),
                    activeGroupKey = "",
                    resolvingIds = emptySet(),
                    resolvedUrls = emptyMap(),
                    selectedCandidateId = null,
                )
            }
            stopPreviewInternal()
            when (
                val result = searchProviderCandidatesUseCase(
                    title = title,
                    artist = artist,
                    durationMs = durationMs,
                )
            ) {
                is Result.Success -> {
                    // 已被更新的搜索取代：丢弃这次迟到的旧结果
                    if (seq != searchSeq) return@launch
                    updateState {
                        copy(
                            isSearching = false,
                            groups = result.data,
                            activeGroupKey = result.data.firstOrNull()?.key.orEmpty(),
                        )
                    }
                }
                is Result.Error -> {
                    if (seq != searchSeq) return@launch
                    updateState { copy(isSearching = false) }
                    sendEffect(
                        CollectTrackEffect.ShowMessage(
                            result.message ?: result.exception.message ?: "多源搜索失败"
                        )
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    /** 勾选候选：DIRECT 直接选中；RESOLVE 先按需解析直链 */
    private fun selectCandidate(candidate: ProviderCandidate) {
        if (!candidate.canSelect) {
            sendEffect(CollectTrackEffect.ShowMessage("该候选无法解析音源，不能选择"))
            return
        }
        lastInteractionCandidateId = candidate.id
        val cached = currentState.resolvedUrls[candidate.id]
        if (cached != null) {
            updateState { copy(selectedCandidateId = candidate.id) }
            return
        }
        if (candidate.playType == CandidatePlayType.DIRECT) {
            val direct = candidate.directUrl
            if (!direct.isNullOrBlank()) {
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
            if (url == null) {
                clearSelectionIfFailed(candidate)
                return@resolveCandidate
            }
            updateState { copy(resolvedUrls = resolvedUrls + (candidate.id to url)) }
        }
    }

    /** 点击播放/解析图标：未解析则先解析，成功后自动播放；已播则暂停 */
    private fun toggleCandidatePlay(candidate: ProviderCandidate) {
        if (!candidate.canSelect) {
            sendEffect(CollectTrackEffect.ShowMessage("该候选无法解析音源"))
            return
        }
        if (currentState.previewCandidateId == candidate.id && currentState.preview.isPlaying) {
            togglePreviewPlayPause()
            return
        }
        lastInteractionCandidateId = candidate.id
        if (currentState.resolvingIds.contains(candidate.id)) {
            // 已在解析中：早退会让用户以为点了没反应，明确提示（解析完成会自动起播）
            sendEffect(CollectTrackEffect.ShowMessage("正在解析该音源，请稍候"))
            return
        }
        // 直连候选（网易云等）必须把自带直链也登记进 resolvedUrls：
        // 原实现只调 startPreview 不写缓存 → resolvedCandidate 仍为 null，
        // 底部一直停在「（解析中…）」且「提交收录」永远禁用（用户 2026-10-22 反馈）
        val url = currentState.resolvedUrls[candidate.id]
            ?: candidate.directUrl?.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            resolveCandidate(candidate) { resolved ->
                if (resolved == null) {
                    clearSelectionIfFailed(candidate)
                    return@resolveCandidate
                }
                val isLatest = lastInteractionCandidateId == candidate.id
                updateState {
                    copy(
                        resolvedUrls = resolvedUrls + (candidate.id to resolved),
                        selectedCandidateId = if (isLatest) candidate.id else selectedCandidateId,
                    )
                }
                // 已切走的候选不抢播放（否则切 tab 后声音突然从上一个 tab 响起）
                if (isLatest) startPreview(candidate, resolved)
            }
            return
        }
        updateState {
            copy(
                selectedCandidateId = candidate.id,
                resolvedUrls = resolvedUrls + (candidate.id to url),
            )
        }
        startPreview(candidate, url)
    }

    /** 解析失败：不要留下「已选（解析中…）」的僵尸态，清掉选中让底部回到未选提示 */
    private fun clearSelectionIfFailed(candidate: ProviderCandidate) {
        if (lastInteractionCandidateId != candidate.id) return
        if (currentState.resolvedUrls.containsKey(candidate.id)) return
        if (currentState.selectedCandidateId != candidate.id) return
        updateState { copy(selectedCandidateId = null) }
    }

    /** 解析候选直链（DIRECT 用自带直链；RESOLVE 走服务端逆向/代理） */
    private fun resolveCandidate(candidate: ProviderCandidate, onDone: (String?) -> Unit) {
        if (currentState.resolvingIds.contains(candidate.id)) return
        updateState { copy(resolvingIds = resolvingIds + candidate.id) }
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
                    updateState { copy(resolvingIds = resolvingIds - candidate.id) }
                    val url = result.data.url.takeIf { it.isNotBlank() }
                    if (url == null) {
                        // 解析「成功」但没直链：不能让选中态卡在「解析中…」
                        sendEffect(CollectTrackEffect.ShowMessage("该音源没有可用播放地址，换一个候选"))
                    }
                    onDone(url)
                }
                is Result.Error -> {
                    updateState { copy(resolvingIds = resolvingIds - candidate.id) }
                    sendEffect(
                        CollectTrackEffect.ShowMessage(
                            result.message ?: result.exception.message ?: "音源解析失败"
                        )
                    )
                    onDone(null)
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun previewMusicOf(candidate: ProviderCandidate, url: String): Music = Music(
        id = candidate.id.hashCode().toLong(),
        sourceId = candidate.id,
        title = candidate.title,
        url = url,
        duration = candidate.durationMs ?: 0L,
        artists = listOf(Artist(id = 0L, name = candidate.artist)),
    )

    private fun startPreview(candidate: ProviderCandidate, url: String) {
        if (url.isBlank()) {
            sendEffect(CollectTrackEffect.ShowMessage("当前歌曲无法试听（请先解析音源）"))
            return
        }
        stopPreviewInternal()
        val music = previewMusicOf(candidate, url)
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
                            preview = PreviewPlaybackState(
                                music = music,
                                isPlaying = true,
                                isBuffering = false,
                                positionMs = 0,
                                durationMs = mp.duration.toLong().coerceAtLeast(0),
                            ),
                        )
                    }
                    startProgressTicker()
                }
                setOnCompletionListener {
                    updateState {
                        copy(
                            preview = preview.copy(
                                isPlaying = false,
                                isBuffering = false,
                                positionMs = preview.durationMs,
                            ),
                        )
                    }
                    progressJob?.cancel()
                }
                setOnErrorListener { _, _, _ ->
                    stopPreviewInternal()
                    sendEffect(CollectTrackEffect.ShowMessage("当前歌曲无法试听"))
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            // 进入缓冲态：列表/面板展示转圈，直到 onPrepared 把 isBuffering 置回 false
            updateState {
                copy(
                    previewCandidateId = candidate.id,
                    preview = PreviewPlaybackState(music = music, isPlaying = false, isBuffering = true),
                )
            }
        } catch (_: Exception) {
            stopPreviewInternal()
            sendEffect(CollectTrackEffect.ShowMessage("当前歌曲无法试听"))
        }
    }

    private fun togglePreviewPlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                progressJob?.cancel()
                updateState { copy(preview = preview.copy(isPlaying = false)) }
            } else {
                player.start()
                startProgressTicker()
                updateState { copy(preview = preview.copy(isPlaying = true)) }
            }
        } catch (_: Exception) {
            stopPreviewInternal()
        }
    }

    private fun seekPreview(positionMs: Long) {
        val player = mediaPlayer ?: return
        try {
            val target = positionMs.coerceIn(0L, player.duration.toLong().coerceAtLeast(0L))
            player.seekTo(target.toInt())
            updateState { copy(preview = preview.copy(positionMs = target)) }
        } catch (_: Exception) {
        }
    }

    private fun startProgressTicker() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val player = mediaPlayer ?: break
                try {
                    updateState {
                        copy(
                            preview = preview.copy(
                                positionMs = player.currentPosition.toLong(),
                                durationMs = player.duration.toLong().coerceAtLeast(0),
                                isPlaying = player.isPlaying,
                            ),
                        )
                    }
                } catch (_: Exception) {
                    break
                }
                delay(200)
            }
        }
    }

    private fun stopPreviewInternal() {
        // 停播/切 tab/重新搜索都视为「交互被打断」：在途解析回调不得再改选中项
        lastInteractionCandidateId = null
        progressJob?.cancel()
        progressJob = null
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
                preview = PreviewPlaybackState(),
            )
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            when (val result = playlistRepository.getPlaylists()) {
                is Result.Success ->
                    updateState { copy(playlists = result.data, isPlaylistsLoading = false) }
                is Result.Error ->
                    updateState { copy(playlists = emptyList(), isPlaylistsLoading = false) }
                Result.Loading -> Unit
            }
        }
    }

    /**
     * 收录单曲到平台（或歌单）。
     * 元数据 = 一级页所选网易云/QQ 元数据（用户可编辑名称/艺人）；
     * 音源 = 用户勾选候选解析出的直链（audioUrl 直传，免服务端二次解析）。
     */
    private fun collect(playlistId: Long?) {
        val s = currentState
        if (s.isCollecting) return
        val selected = s.resolvedCandidate
        if (selected == null) {
            sendEffect(CollectTrackEffect.ShowMessage("请先选择并解析一个音源候选"))
            return
        }
        val audioUrl = s.resolvedUrls[selected.id] ?: selected.directUrl?.takeIf { it.isNotBlank() }
        val req = ImportSingleRequest(
            platform = s.argsPlatform.ifBlank { null },
            externalId = s.argsExternalId.ifBlank { null },
            title = s.editTitle.trim(),
            artist = s.editArtist.trim().ifBlank { null },
            album = s.argsAlbum.trim().ifBlank { null },
            coverUrl = s.argsCoverUrl,
            durationMs = s.argsDurationMs.takeIf { it > 0 } ?: selected.durationMs,
            audioUrl = audioUrl,
            lyrics = s.prefetchedLyrics,
            playlistId = playlistId,
        )
        viewModelScope.launch {
            updateState { copy(isCollecting = true) }
            when (val result = importSingleTrackUseCase(req)) {
                is Result.Success -> {
                    updateState {
                        copy(
                            isCollecting = false,
                            exists = MusicExists(exists = true, musicId = result.data.musicId),
                            collectResult = CollectResult(
                                musicId = result.data.musicId,
                                isNew = result.data.isNew,
                                toPlaylist = playlistId != null,
                            ),
                        )
                    }
                    sendEffect(
                        CollectTrackEffect.ShowMessage(
                            if (playlistId != null) "已收录并加入歌单" else "已收录到平台"
                        )
                    )
                    sendEffect(CollectTrackEffect.CollectSucceeded)
                }
                is Result.Error -> {
                    updateState { copy(isCollecting = false) }
                    sendEffect(
                        CollectTrackEffect.ShowMessage(
                            result.message ?: result.exception.message ?: "收录失败"
                        )
                    )
                }
                Result.Loading -> Unit
            }
        }
    }
}
