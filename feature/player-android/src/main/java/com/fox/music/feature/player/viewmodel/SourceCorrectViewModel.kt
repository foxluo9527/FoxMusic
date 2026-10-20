package com.fox.music.feature.player.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.usecase.GetMusicDetailUseCase
import com.fox.music.core.domain.usecase.GetSearchMusicPagingUseCase
import com.fox.music.core.domain.usecase.ReplaceMusicSourceUseCase
import com.fox.music.core.model.music.LyricsParser
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SourceCorrectTab(val label: String) {
    SEARCH("搜索替换"),
    UPLOAD("上传文件"),
}

data class SourceCorrectState(
    val musicId: Long = 0,
    val targetMusic: Music? = null,
    val isTargetLoading: Boolean = false,
    /** 原歌曲歌词（LRC 解析），试听候选音源时对照展示 */
    val targetLyrics: List<LyricsParser.LyricLine> = emptyList(),
    val activeTab: SourceCorrectTab = SourceCorrectTab.SEARCH,
    val query: String = "",
    val platform: ManageSearchPlatform = ManageSearchPlatform.NETEASE,
    val hasSearched: Boolean = false,
    val musicPaging: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val searchKey: Int = 0,
    val selectedThirdParty: Music? = null,
    val preview: PreviewPlaybackState = PreviewPlaybackState(),
    val pickedFileUri: Uri? = null,
    val pickedFileName: String? = null,
    val isReplacing: Boolean = false,
) : UiState

sealed interface SourceCorrectIntent : UiIntent {
    data class Init(val musicId: Long) : SourceCorrectIntent
    data class SelectTab(val tab: SourceCorrectTab) : SourceCorrectIntent
    data class QueryChange(val query: String) : SourceCorrectIntent
    data class SelectPlatform(val platform: ManageSearchPlatform) : SourceCorrectIntent
    data object Search : SourceCorrectIntent
    data class SelectThirdParty(val music: Music?) : SourceCorrectIntent
    data class Preview(val music: Music) : SourceCorrectIntent
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
    private val getSearchMusicPagingUseCase: GetSearchMusicPagingUseCase,
    private val uploadRepository: UploadRepository,
    private val replaceMusicSourceUseCase: ReplaceMusicSourceUseCase,
    private val musicController: MusicController,
) : MviViewModel<SourceCorrectState, SourceCorrectIntent, SourceCorrectEffect>(SourceCorrectState()) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

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
            is SourceCorrectIntent.SelectTab -> {
                stopPreviewInternal()
                updateState { copy(activeTab = intent.tab) }
            }
            is SourceCorrectIntent.QueryChange -> updateState { copy(query = intent.query) }
            is SourceCorrectIntent.SelectPlatform -> {
                updateState { copy(platform = intent.platform) }
                if (currentState.hasSearched && currentState.query.isNotBlank()) {
                    performSearch()
                }
            }
            SourceCorrectIntent.Search -> performSearch()
            is SourceCorrectIntent.SelectThirdParty -> {
                updateState { copy(selectedThirdParty = intent.music) }
                // 选中与当前音源时长几乎一致的候选时提醒：极可能是同一首歌
                val targetDuration = currentState.targetMusic?.duration ?: 0L
                val candidateDuration = intent.music?.duration ?: 0L
                if (targetDuration > 0 && candidateDuration > 0 &&
                    Math.abs(candidateDuration - targetDuration) < 3_000L
                ) {
                    sendEffect(
                        SourceCorrectEffect.ShowMessage("注意：该候选与当前音源时长几乎一致，可能是同一首歌，替换后内容可能不变")
                    )
                }
            }
            is SourceCorrectIntent.Preview -> startPreview(intent.music)
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
                    updateState {
                        copy(
                            targetMusic = detail.music,
                            isTargetLoading = false,
                            // 搜索框默认带入当前歌曲名；用户已输入过关键词则不覆盖
                            query = if (query.isBlank()) detail.music.title else query,
                            // 原歌歌词用于试听对照
                            targetLyrics = LyricsParser.parseLrc(detail.music.lyrics.orEmpty()),
                        )
                    }
                }
                .onError { _, _ ->
                    updateState { copy(isTargetLoading = false) }
                }
        }
    }

    private fun performSearch() {
        val keyword = currentState.query.trim()
        if (keyword.isBlank()) {
            sendEffect(SourceCorrectEffect.ShowMessage("请输入搜索关键词"))
            return
        }
        val flow = getSearchMusicPagingUseCase(
            keyword = keyword,
            platform = currentState.platform.apiValue,
        ).cachedIn(viewModelScope)
        updateState {
            copy(
                hasSearched = true,
                musicPaging = flow,
                searchKey = searchKey + 1,
                selectedThirdParty = null,
            )
        }
    }

    private fun startPreview(music: Music) {
        val url = music.url
        if (url.isBlank()) {
            sendEffect(SourceCorrectEffect.ShowMessage("当前歌曲无法试听"))
            return
        }
        stopPreviewInternal()
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
                            preview = PreviewPlaybackState(
                                music = music,
                                isPlaying = true,
                                positionMs = 0,
                                durationMs = mp.duration.toLong().coerceAtLeast(0),
                            )
                        )
                    }
                    startProgressTicker()
                }
                setOnCompletionListener {
                    updateState {
                        copy(
                            preview = preview.copy(
                                isPlaying = false,
                                positionMs = preview.durationMs,
                            )
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
                copy(preview = PreviewPlaybackState(music = music, isPlaying = false))
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
                            )
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
        updateState { copy(preview = PreviewPlaybackState()) }
    }

    private suspend fun replaceFromThirdParty() {
        val music = currentState.selectedThirdParty
        if (music == null) {
            sendEffect(SourceCorrectEffect.ShowMessage("请先选择一首替换歌曲"))
            return
        }
        if (music.url.isBlank()) {
            sendEffect(SourceCorrectEffect.ShowMessage("该歌曲没有可用音源"))
            return
        }
        stopPreviewInternal()
        updateState { copy(isReplacing = true) }
        val referer = platformReferer(currentState.platform)
        when (
            val result = replaceMusicSourceUseCase(
                musicId = currentState.musicId,
                url = music.url,
                referer = referer,
                duration = music.duration.takeIf { it > 0 },
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

    private fun platformReferer(platform: ManageSearchPlatform): String? = when (platform) {
        ManageSearchPlatform.NETEASE -> "https://music.163.com/"
        ManageSearchPlatform.QQ -> "https://y.qq.com/"
    }
}
