package com.fox.music.feature.player.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.ReplaceMusicSourceUseCase
import com.fox.music.core.domain.usecase.ResolveThirdPartyAudioUseCase
import com.fox.music.core.domain.usecase.SearchProviderCandidatesUseCase
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.CandidatePlayType
import com.fox.music.core.model.music.CandidateResolveParams
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.ProviderCandidate
import com.fox.music.core.model.music.ProviderCandidateGroup
import com.fox.music.core.model.music.ResolveThirdPartyRequest
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SourceCorrectMode(val label: String) {
    SEARCH("搜索替换"),
    UPLOAD("上传文件"),
}

data class SourceCorrectState(
    val musicId: Long = 0,
    val targetMusic: Music? = null,
    val isTargetLoading: Boolean = false,
    /** 原歌曲歌词（LRC 解析），试听候选音源时对照展示 */
    val targetLyrics: List<LyricsParser.LyricLine> = emptyList(),
    /** 右上角切换：搜索替换 / 上传文件 */
    val mode: SourceCorrectMode = SourceCorrectMode.SEARCH,
    /** 单行搜索区：歌名 + 艺人（点击弹窗编辑） */
    val query: String = "",
    val artistQuery: String = "",
    val isSearching: Boolean = false,
    /** 各音源渠道候选（一个渠道一个 tab，宽松匹配） */
    val groups: List<ProviderCandidateGroup> = emptyList(),
    val activeGroupKey: String = "",
    /** 正在解析的候选 id 集合 */
    val resolvingIds: Set<String> = emptySet(),
    /** 已解析出直链的候选：id -> url */
    val resolvedUrls: Map<String, String> = emptyMap(),
    /** 当前选中候选 id（已拿到可播直链） */
    val selectedCandidateId: String? = null,
    /** 正在试听的候选 id */
    val previewCandidateId: String? = null,
    val preview: PreviewPlaybackState = PreviewPlaybackState(),
    val pickedFileUri: Uri? = null,
    val pickedFileName: String? = null,
    val isReplacing: Boolean = false,
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

sealed interface SourceCorrectIntent : UiIntent {
    data class Init(val musicId: Long) : SourceCorrectIntent
    data class SwitchMode(val mode: SourceCorrectMode) : SourceCorrectIntent
    data class EditQuery(val title: String, val artist: String) : SourceCorrectIntent
    data object SearchProviders : SourceCorrectIntent
    data class SelectGroup(val key: String) : SourceCorrectIntent
    /** 勾选候选：DIRECT 直接选中；RESOLVE 先解析再选中 */
    data class SelectCandidate(val candidate: ProviderCandidate) : SourceCorrectIntent
    /** 点击候选右侧播放/解析图标：未解析则先解析后自动播放 */
    data class ToggleCandidatePlay(val candidate: ProviderCandidate) : SourceCorrectIntent
    data class SeekPreview(val positionMs: Long) : SourceCorrectIntent
    data object TogglePreviewPlayPause : SourceCorrectIntent
    data object StopPreview : SourceCorrectIntent
    data class PickFile(val uri: Uri?, val fileName: String?) : SourceCorrectIntent
    data object ReplaceFromThirdParty : SourceCorrectIntent
    data object ReplaceFromFile : SourceCorrectIntent
}

sealed interface SourceCorrectEffect : UiEffect {
    data class ShowMessage(val message: String) : SourceCorrectEffect
    data object ReplaceSucceeded : SourceCorrectEffect
}

@HiltViewModel
class SourceCorrectViewModel @Inject constructor(
    private val getMusicDetailUseCase: GetMusicDetailUseCase,
    private val searchProviderCandidatesUseCase: SearchProviderCandidatesUseCase,
    private val resolveThirdPartyAudioUseCase: ResolveThirdPartyAudioUseCase,
    private val uploadRepository: UploadRepository,
    private val replaceMusicSourceUseCase: ReplaceMusicSourceUseCase,
    private val musicController: MusicController,
) : MviViewModel<SourceCorrectState, SourceCorrectIntent, SourceCorrectEffect>(SourceCorrectState()) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    /**
     * 用户最后一次主动点选/试听的候选 id：异步解析回调只在它仍是最新交互时
     * 才允许改选中项/起播，否则切 tab 后旧 tab 的解析结果会把选中项抢回去
     * （用户 2026-10-22 反馈：其他 tab 勾选音源，最终替换的还是之前那个）。
     */
    private var lastInteractionCandidateId: String? = null

    /** 搜索序号：换搜索词后迟到的旧请求结果不得覆盖新词结果（用户 2026-10-22 反馈） */
    private var searchSeq = 0L

    override fun onCleared() {
        stopPreviewInternal()
        super.onCleared()
    }

    override fun handleIntent(intent: SourceCorrectIntent) {
        when (intent) {
            is SourceCorrectIntent.Init -> {
                if (currentState.musicId != intent.musicId) {
                    updateState { copy(musicId = intent.musicId) }
                }
                loadTargetMusic(intent.musicId)
            }
            is SourceCorrectIntent.SwitchMode -> {
                stopPreviewInternal()
                updateState { copy(mode = intent.mode) }
            }
            is SourceCorrectIntent.EditQuery -> {
                updateState { copy(query = intent.title, artistQuery = intent.artist) }
                searchProviders()
            }
            SourceCorrectIntent.SearchProviders -> searchProviders()
            is SourceCorrectIntent.SelectGroup -> {
                // 切换渠道：清空选择与试听，底部「确认替换」随之禁用
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
            is SourceCorrectIntent.SelectCandidate -> selectCandidate(intent.candidate)
            is SourceCorrectIntent.ToggleCandidatePlay -> toggleCandidatePlay(intent.candidate)
            is SourceCorrectIntent.SeekPreview -> seekPreview(intent.positionMs)
            SourceCorrectIntent.TogglePreviewPlayPause -> togglePreviewPlayPause()
            SourceCorrectIntent.StopPreview -> stopPreviewInternal()
            is SourceCorrectIntent.PickFile -> {
                stopPreviewInternal()
                updateState {
                    copy(pickedFileUri = intent.uri, pickedFileName = intent.fileName)
                }
            }
            SourceCorrectIntent.ReplaceFromThirdParty ->
                viewModelScope.launch { replaceFromThirdParty() }
            SourceCorrectIntent.ReplaceFromFile ->
                viewModelScope.launch { replaceFromFile() }
        }
    }

    private fun loadTargetMusic(musicId: Long) {
        if (musicId <= 0) return
        viewModelScope.launch {
            updateState { copy(isTargetLoading = true) }
            getMusicDetailUseCase(musicId)
                .onSuccess { detail ->
                    val title = detail.music.title
                    val artist = detail.music.artists.joinToString(", ") { it.name }.ifBlank { "" }
                    updateState {
                        copy(
                            targetMusic = detail.music,
                            isTargetLoading = false,
                            query = if (query.isBlank()) title else query,
                            artistQuery = if (artistQuery.isBlank()) artist else artistQuery,
                            targetLyrics = LyricsParser.parseLrc(detail.music.lyrics.orEmpty()),
                        )
                    }
                    searchProviders()
                }
                .onError { _, _ ->
                    updateState { copy(isTargetLoading = false) }
                }
        }
    }

    /** 按歌名+艺人拉取各音源渠道候选（宽松匹配，不解析直链） */
    private fun searchProviders() {
        val title = currentState.query.trim()
        if (title.isBlank()) {
            sendEffect(SourceCorrectEffect.ShowMessage("请输入歌名"))
            return
        }
        val artist = currentState.artistQuery.trim().ifBlank { null }
        val durationMs = currentState.targetMusic?.duration?.takeIf { it > 0 }
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
                        SourceCorrectEffect.ShowMessage(
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
            sendEffect(SourceCorrectEffect.ShowMessage("该候选无法解析音源，不能选择"))
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
        // RESOLVE：勾选即触发解析
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
            sendEffect(SourceCorrectEffect.ShowMessage("该候选无法解析音源"))
            return
        }
        if (currentState.previewCandidateId == candidate.id && currentState.preview.isPlaying) {
            togglePreviewPlayPause()
            return
        }
        lastInteractionCandidateId = candidate.id
        if (currentState.resolvingIds.contains(candidate.id)) {
            sendEffect(SourceCorrectEffect.ShowMessage("正在解析该音源，请稍候"))
            return
        }
        // 直连候选自带的直链也要登记进 resolvedUrls，否则 resolvedCandidate 恒为 null，
        // 底部一直停在「（解析中…）」且「确认替换」永远禁用
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

    /** 解析失败：不留「已选（解析中…）」僵尸态，清掉选中让底部回到未选提示 */
    private fun clearSelectionIfFailed(candidate: ProviderCandidate) {
        if (lastInteractionCandidateId != candidate.id) return
        if (currentState.resolvedUrls.containsKey(candidate.id)) return
        if (currentState.selectedCandidateId != candidate.id) return
        updateState { copy(selectedCandidateId = null) }
    }

    /** 解析候选直链（DIRECT 直接用缓存直链；RESOLVE 走服务端逆向/代理） */
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
                        sendEffect(SourceCorrectEffect.ShowMessage("该音源没有可用播放地址，换一个候选"))
                    }
                    onDone(url)
                }
                is Result.Error -> {
                    updateState { copy(resolvingIds = resolvingIds - candidate.id) }
                    sendEffect(
                        SourceCorrectEffect.ShowMessage(
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
            sendEffect(SourceCorrectEffect.ShowMessage("当前歌曲无法试听（请先解析音源）"))
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
                    sendEffect(SourceCorrectEffect.ShowMessage("当前歌曲无法试听"))
                    true
                }
                prepareAsync()
            }
            mediaPlayer = player
            updateState {
                copy(
                    previewCandidateId = candidate.id,
                    preview = PreviewPlaybackState(music = music, isPlaying = false, isBuffering = true),
                )
            }
        } catch (_: Exception) {
            stopPreviewInternal()
            sendEffect(SourceCorrectEffect.ShowMessage("当前歌曲无法试听"))
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
        // 停播/切 tab/切模式都视为「交互被打断」：在途解析回调不得再改选中项
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

    private suspend fun replaceFromThirdParty() {
        val candidate = currentState.resolvedCandidate
        if (candidate == null) {
            sendEffect(SourceCorrectEffect.ShowMessage("请先选择并解析一首替换歌曲"))
            return
        }
        val url = currentState.resolvedUrls[candidate.id]
            ?: candidate.directUrl?.takeIf { it.isNotBlank() }
        if (url.isNullOrBlank()) {
            sendEffect(SourceCorrectEffect.ShowMessage("该歌曲没有可用音源"))
            return
        }
        stopPreviewInternal()
        updateState { copy(isReplacing = true) }
        when (
            val result = replaceMusicSourceUseCase(
                musicId = currentState.musicId,
                url = url,
                referer = null,
                duration = candidate.durationMs?.takeIf { it > 0 },
            )
        ) {
            is Result.Success -> {
                updateState { copy(isReplacing = false) }
                // 服务端已换源：拉取最新详情，把新地址同步给播放器（内存歌单 + 队列 + 覆盖表），
                // 否则列表/队列里的旧 Music 仍会指向替换前的旧音频。
                refreshPlayerUrl()
                sendEffect(SourceCorrectEffect.ShowMessage("音源替换成功"))
                sendEffect(SourceCorrectEffect.ReplaceSucceeded)
            }
            is Result.Error -> {
                updateState { copy(isReplacing = false) }
                sendEffect(
                    SourceCorrectEffect.ShowMessage(
                        result.message ?: result.exception.message ?: "音源替换失败"
                    )
                )
            }
            Result.Loading -> Unit
        }
    }

    private suspend fun replaceFromFile() {
        val uri = currentState.pickedFileUri
        if (uri == null) {
            sendEffect(SourceCorrectEffect.ShowMessage("请先选择音频文件"))
            return
        }
        stopPreviewInternal()
        updateState { copy(isReplacing = true) }
        // 先上传到服务端（temp 目录），replace 接口会确认进 OSS
        val uploaded = when (val upload = uploadRepository.uploadAudio(uri)) {
            is Result.Success -> upload.data
            is Result.Error -> {
                updateState { copy(isReplacing = false) }
                sendEffect(
                    SourceCorrectEffect.ShowMessage(
                        upload.message ?: upload.exception.message ?: "文件上传失败"
                    )
                )
                return
            }
            Result.Loading -> null
        }
        if (uploaded.isNullOrBlank()) {
            updateState { copy(isReplacing = false) }
            sendEffect(SourceCorrectEffect.ShowMessage("文件上传失败"))
            return
        }
        when (
            val result = replaceMusicSourceUseCase(
                musicId = currentState.musicId,
                url = uploaded,
                referer = null,
                duration = null,
            )
        ) {
            is Result.Success -> {
                updateState { copy(isReplacing = false) }
                refreshPlayerUrl()
                sendEffect(SourceCorrectEffect.ShowMessage("音源替换成功"))
                sendEffect(SourceCorrectEffect.ReplaceSucceeded)
            }
            is Result.Error -> {
                updateState { copy(isReplacing = false) }
                sendEffect(
                    SourceCorrectEffect.ShowMessage(
                        result.message ?: result.exception.message ?: "音源替换失败"
                    )
                )
            }
            Result.Loading -> Unit
        }
    }

    /** 换源成功后，用最新详情里的地址刷新播放器，避免旧 Music 的 url 继续被播放 */
    private suspend fun refreshPlayerUrl() {
        val musicId = currentState.musicId
        if (musicId <= 0) return
        getMusicDetailUseCase(musicId).onSuccess { detail ->
            val url = detail.music.url
            if (url.isNotBlank()) {
                musicController.updateMusicUrl(musicId, url)
            }
        }
    }
}
