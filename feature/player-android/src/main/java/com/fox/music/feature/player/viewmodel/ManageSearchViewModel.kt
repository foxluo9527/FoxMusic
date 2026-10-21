package com.fox.music.feature.player.viewmodel

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.domain.repository.AdminArtistInput
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.CreateAdminMusicInput
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.UploadRepository
import com.fox.music.core.domain.usecase.FetchScrapedLyricsUseCase
import com.fox.music.core.domain.usecase.GetSearchMusicPagingUseCase
import com.fox.music.core.model.music.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ManageSearchPlatform(val apiValue: String, val label: String) {
    NETEASE("netease", "网易云"),
    QQ("qq", "QQ 音乐"),
}

data class PreviewPlaybackState(
    val music: Music? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
)

data class ImportProgressState(
    val current: Int = 0,
    val total: Int = 0,
    val message: String = "正在导入音乐...",
)

data class ManageSearchState(
    val isLoggedIn: Boolean = false,
    val query: String = "",
    val platform: ManageSearchPlatform = ManageSearchPlatform.NETEASE,
    val hasSearched: Boolean = false,
    val musicPaging: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val searchKey: Int = 0,
    val selectedIds: Set<Long> = emptySet(),
    val selectedMusic: Map<Long, Music> = emptyMap(),
    val selectAllChecked: Boolean = false,
    val preview: PreviewPlaybackState = PreviewPlaybackState(),
    val showPlaylistImport: Boolean = false,
    val importProgress: ImportProgressState? = null,
) : UiState {
    val selectedCount: Int get() = selectedIds.size
}

sealed interface ManageSearchIntent : UiIntent {
    data class QueryChange(val query: String) : ManageSearchIntent
    data class SelectPlatform(val platform: ManageSearchPlatform) : ManageSearchIntent
    data object Search : ManageSearchIntent
    data class ToggleSelect(val music: Music) : ManageSearchIntent
    data class SetSelectAll(val checked: Boolean, val visibleItems: List<Music>) : ManageSearchIntent
    data class Preview(val music: Music) : ManageSearchIntent
    data object TogglePreviewPlayPause : ManageSearchIntent
    data object StopPreview : ManageSearchIntent
    data object RequestImportSelected : ManageSearchIntent
    data object ShowPlaylistImport : ManageSearchIntent
    data object DismissPlaylistImport : ManageSearchIntent
    data class ConfirmPlaylistImport(val url: String) : ManageSearchIntent
    data object RequestLogin : ManageSearchIntent
}

sealed interface ManageSearchEffect : UiEffect {
    data object NavigateToLogin : ManageSearchEffect
    data class ShowMessage(val message: String) : ManageSearchEffect
}

@HiltViewModel
class ManageSearchViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val getSearchMusicPagingUseCase: GetSearchMusicPagingUseCase,
    private val uploadRepository: UploadRepository,
    private val adminMusicRepository: AdminMusicRepository,
    private val importRepository: ImportRepository,
    private val fetchScrapedLyricsUseCase: FetchScrapedLyricsUseCase,
) : MviViewModel<ManageSearchState, ManageSearchIntent, ManageSearchEffect>(ManageSearchState()) {

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    init {
        viewModelScope.launch {
            tokenManager.isLoggedIn.collectLatest { loggedIn ->
                updateState { copy(isLoggedIn = loggedIn) }
            }
        }
    }

    override fun onCleared() {
        stopPreviewInternal()
        super.onCleared()
    }

    override fun handleIntent(intent: ManageSearchIntent) {
        when (intent) {
            is ManageSearchIntent.QueryChange -> updateState { copy(query = intent.query) }
            is ManageSearchIntent.SelectPlatform -> {
                updateState { copy(platform = intent.platform) }
                if (currentState.hasSearched && currentState.query.isNotBlank()) {
                    performSearch()
                }
            }
            ManageSearchIntent.Search -> performSearch()
            is ManageSearchIntent.ToggleSelect -> toggleSelect(intent.music)
            is ManageSearchIntent.SetSelectAll -> setSelectAll(intent.checked, intent.visibleItems)
            is ManageSearchIntent.Preview -> startPreview(intent.music)
            ManageSearchIntent.TogglePreviewPlayPause -> togglePreviewPlayPause()
            ManageSearchIntent.StopPreview -> stopPreviewInternal()
            ManageSearchIntent.RequestImportSelected -> viewModelScope.launch { importSelected() }
            ManageSearchIntent.ShowPlaylistImport -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageSearchEffect.NavigateToLogin)
                } else {
                    updateState { copy(showPlaylistImport = true) }
                }
            }
            ManageSearchIntent.DismissPlaylistImport -> updateState { copy(showPlaylistImport = false) }
            is ManageSearchIntent.ConfirmPlaylistImport ->
                viewModelScope.launch { importPlaylist(intent.url) }
            ManageSearchIntent.RequestLogin -> sendEffect(ManageSearchEffect.NavigateToLogin)
        }
    }

    private fun performSearch() {
        val keyword = currentState.query.trim()
        if (keyword.isBlank()) {
            sendEffect(ManageSearchEffect.ShowMessage("请输入搜索关键词"))
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
                selectedIds = emptySet(),
                selectedMusic = emptyMap(),
                selectAllChecked = false,
            )
        }
    }

    private fun toggleSelect(music: Music) {
        val id = music.id
        val selected = currentState.selectedIds.toMutableSet()
        val map = currentState.selectedMusic.toMutableMap()
        if (id in selected) {
            selected.remove(id)
            map.remove(id)
        } else {
            selected.add(id)
            map[id] = music
        }
        updateState {
            copy(
                selectedIds = selected,
                selectedMusic = map,
                selectAllChecked = false,
            )
        }
    }

    private fun setSelectAll(checked: Boolean, visibleItems: List<Music>) {
        if (!checked) {
            updateState {
                copy(
                    selectedIds = emptySet(),
                    selectedMusic = emptyMap(),
                    selectAllChecked = false,
                )
            }
            return
        }
        val map = visibleItems.associateBy { it.id }
        updateState {
            copy(
                selectedIds = map.keys,
                selectedMusic = map,
                selectAllChecked = true,
            )
        }
    }

    private fun startPreview(music: Music) {
        val url = music.url
        if (url.isBlank()) {
            sendEffect(ManageSearchEffect.ShowMessage("当前歌曲无法试听"))
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
                    sendEffect(ManageSearchEffect.ShowMessage("当前歌曲无法试听"))
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
            sendEffect(ManageSearchEffect.ShowMessage("当前歌曲无法试听"))
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

    private suspend fun importSelected() {
        if (!currentState.isLoggedIn) {
            sendEffect(ManageSearchEffect.NavigateToLogin)
            return
        }
        val items = currentState.selectedMusic.values.toList()
        if (items.isEmpty()) {
            sendEffect(ManageSearchEffect.ShowMessage("请先选择要导入的音乐"))
            return
        }
        var success = 0
        var failed = 0
        updateState {
            copy(importProgress = ImportProgressState(current = 0, total = items.size))
        }
        items.forEachIndexed { index, music ->
            updateState {
                copy(
                    importProgress = ImportProgressState(
                        current = index + 1,
                        total = items.size,
                        message = "正在导入「${music.title}」...",
                    )
                )
            }
            val ok = importOne(music)
            if (ok) success++ else failed++
        }
        updateState {
            copy(
                importProgress = null,
                selectedIds = emptySet(),
                selectedMusic = emptyMap(),
                selectAllChecked = false,
            )
        }
        sendEffect(ManageSearchEffect.ShowMessage("导入结果：成功 $success 首，失败 $failed 首"))
    }

    private suspend fun importOne(music: Music): Boolean {
        return try {
            val audioSource = music.url
            if (audioSource.isBlank()) return false

            val convertedAudio = when (val r = uploadRepository.uploadAudioByUrl(audioSource)) {
                is Result.Success -> r.data
                else -> audioSource
            }

            val coverSource = music.coverImage
            val convertedCover = if (!coverSource.isNullOrBlank()) {
                when (val r = uploadRepository.uploadImageByUrl(coverSource)) {
                    is Result.Success -> r.data
                    else -> coverSource
                }
            } else {
                null
            }

            // 歌词：列表元数据（网易云/QQ 平台歌词）优先；为空时按 歌名+艺人 刮削补充
            // （LRCLIB/lyrics.ovh，服务端免费源，尽力而为不阻塞导入——2026-10-21 用户要求）
            val lyrics = music.lyrics?.takeIf { it.isNotBlank() } ?: run {
                val artistName = music.artists.firstOrNull()?.name.orEmpty()
                if (artistName.isBlank()) "" else {
                    when (val r = fetchScrapedLyricsUseCase(music.title, artistName, music.duration.takeIf { it > 0 })) {
                        is Result.Success -> r.data.orEmpty()
                        else -> ""
                    }
                }
            }

            val artists = if (music.artists.isNotEmpty()) {
                music.artists.map { artist ->
                    val avatar = artist.avatar?.takeIf { it.isNotBlank() }?.let { url ->
                        (uploadRepository.uploadImageByUrl(url) as? Result.Success)?.data ?: url
                    }
                    val cover = artist.coverImage?.takeIf { it.isNotBlank() }?.let { url ->
                        (uploadRepository.uploadImageByUrl(url) as? Result.Success)?.data ?: url
                    }
                    AdminArtistInput(
                        name = artist.name,
                        alias = artist.alias.orEmpty(),
                        avatar = avatar,
                        coverImage = cover,
                    )
                }
            } else {
                listOf(AdminArtistInput(name = "未知歌手"))
            }

            when (
                adminMusicRepository.createAdminMusic(
                    CreateAdminMusicInput(
                        title = music.title.ifBlank { "未知歌曲" },
                        description = music.description.orEmpty(),
                        url = convertedAudio,
                        coverImage = convertedCover,
                        duration = music.duration,
                        language = music.language ?: "未知",
                        genre = music.genre ?: "流行",
                        lyrics = lyrics,
                        lyricsType = if (lyrics.isNotBlank()) "embedded" else "none",
                        artists = artists,
                    )
                )
            ) {
                is Result.Success -> true
                else -> false
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun importPlaylist(url: String) {
        updateState { copy(showPlaylistImport = false) }
        if (!currentState.isLoggedIn) {
            sendEffect(ManageSearchEffect.NavigateToLogin)
            return
        }
        if (url.isBlank()) {
            sendEffect(ManageSearchEffect.ShowMessage("请输入歌单分享链接"))
            return
        }
        updateState {
            copy(importProgress = ImportProgressState(current = 0, total = 1, message = "正在导入歌单..."))
        }
        when (val result = importRepository.importMusic(url)) {
            is Result.Success -> {
                sendEffect(ManageSearchEffect.ShowMessage("歌单导入成功"))
            }
            is Result.Error -> {
                sendEffect(
                    ManageSearchEffect.ShowMessage(
                        result.message ?: result.exception.message ?: "歌单导入失败"
                    )
                )
            }
            Result.Loading -> Unit
        }
        updateState { copy(importProgress = null) }
    }
}
