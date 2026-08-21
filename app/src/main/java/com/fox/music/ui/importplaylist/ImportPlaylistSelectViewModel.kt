package com.fox.music.ui.importplaylist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.result.Result
import com.fox.music.core.data.importqueue.ImportLog
import com.fox.music.core.data.importqueue.PlaylistImportScheduler
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.repository.ParsedTrack
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.UploadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class ImportSelectUiState(
    val url: String = "",
    val loading: Boolean = false,
    val confirming: Boolean = false,
    val title: String = "",
    val cover: String? = null,
    val description: String = "",
    val tracks: List<ParsedTrack> = emptyList(),
    val selectedIndexes: Set<Int> = emptySet(),
    val platform: String = "",
    val error: String? = null,
)

sealed interface ImportSelectEffect {
    data class NavigateToPlaylist(val playlistId: Long) : ImportSelectEffect
    data class ShowMessage(val message: String) : ImportSelectEffect
}

@HiltViewModel
class ImportPlaylistSelectViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val importRepository: ImportRepository,
    private val playlistRepository: PlaylistRepository,
    private val uploadRepository: UploadRepository,
    private val importScheduler: PlaylistImportScheduler,
) : ViewModel() {

    private val encodedUrl: String = savedStateHandle.get<String>("url").orEmpty()
    private val url: String = runCatching {
        URLDecoder.decode(encodedUrl, Charsets.UTF_8.name())
    }.getOrDefault(encodedUrl)

    private val _state = MutableStateFlow(ImportSelectUiState(url = url, loading = true))
    val state: StateFlow<ImportSelectUiState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<ImportSelectEffect>()
    val effect: SharedFlow<ImportSelectEffect> = _effect.asSharedFlow()

    init {
        parse()
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title, error = null) }
    }

    fun toggle(index: Int) {
        _state.update { s ->
            val set = s.selectedIndexes.toMutableSet()
            if (index in set) set.remove(index) else set.add(index)
            s.copy(selectedIndexes = set)
        }
    }

    fun selectAll(selected: Boolean) {
        _state.update { s ->
            s.copy(
                selectedIndexes = if (selected) s.tracks.indices.toSet() else emptySet(),
            )
        }
    }

    fun confirm() {
        val s = _state.value
        val selected = s.selectedIndexes.sorted().mapNotNull { s.tracks.getOrNull(it) }
        if (selected.isEmpty()) {
            viewModelScope.launch {
                _effect.emit(ImportSelectEffect.ShowMessage("请至少选择一首歌"))
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(confirming = true, error = null) }
            ImportLog.i(
                "确认导入: 歌单「${s.title}」选中${selected.size}/${s.tracks.size} " +
                    "platform=${s.platform} url=${s.url}",
            )
            val coverRemote = s.cover?.takeIf { it.isNotBlank() }?.let { cover ->
                when (val up = uploadRepository.uploadImageByUrl(cover)) {
                    is Result.Success -> up.data.also { ImportLog.i("歌单封面上传成功") }
                    else -> {
                        ImportLog.w("歌单封面上传失败，继续无封面创建")
                        null
                    }
                }
            }
            playlistRepository.createPlaylist(
                title = s.title.ifBlank { "导入歌单" },
                description = s.description.ifBlank { null },
                coverImage = coverRemote,
                isPublic = true,
            ).onSuccess { playlist ->
                ImportLog.i("创建歌单成功 id=${playlist.id} title=${playlist.title}")
                importRepository.updateImportSession(
                    playlistId = playlist.id,
                    totalTracks = selected.size,
                    importedTracks = 0,
                    status = "in_progress",
                )
                importScheduler.enqueuePlaylistImport(
                    playlistId = playlist.id,
                    playlistTitle = playlist.title,
                    tracks = selected,
                    platform = s.platform,
                )
                _state.update { it.copy(confirming = false) }
                _effect.emit(ImportSelectEffect.NavigateToPlaylist(playlist.id))
            }.onError { _, msg ->
                val tip = msg?.takeIf { it.isNotBlank() } ?: "创建歌单失败"
                ImportLog.w("创建歌单失败: $tip")
                _state.update { it.copy(confirming = false, error = tip) }
                _effect.emit(ImportSelectEffect.ShowMessage(tip))
            }
        }
    }

    private fun parse() {
        viewModelScope.launch {
            ImportLog.i("开始解析第三方歌单: $url")
            when (val result = importRepository.parsePlaylist(url)) {
                is Result.Success -> {
                    val data = result.data
                    ImportLog.i(
                        "解析成功: 「${data.title}」platform=${data.platform} " +
                            "曲目=${data.tracks.size} sourceId=${data.sourceId}",
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            title = data.title,
                            cover = data.cover,
                            description = data.description,
                            tracks = data.tracks,
                            selectedIndexes = data.tracks.indices.toSet(),
                            platform = data.platform,
                        )
                    }
                }
                is Result.Error -> {
                    ImportLog.e("解析失败: ${result.message}", result.exception)
                    _state.update {
                        it.copy(loading = false, error = result.message ?: "解析失败")
                    }
                }
                Result.Loading -> Unit
            }
        }
    }
}
