package com.fox.music

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.network.token.TokenManager
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.home.HOME_ROUTE
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    val musicController: MusicController,
    private val tokenManager: TokenManager,
    private val playlistRepository: com.fox.music.core.domain.repository.PlaylistRepository,
    private val importRepository: com.fox.music.core.domain.repository.ImportRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState = _authState.asStateFlow()

    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState = _playlistState.asStateFlow()

    
    init {
        observeAuthState()
    }
    
    private fun observeAuthState() {
        tokenManager.isLoggedIn
            .onEach {isLoggedIn ->
                _authState.update {it.copy(isLoggedIn = isLoggedIn) }
            }
            .launchIn(viewModelScope)
    }
    
    data class AuthState(
        val isLoggedIn: Boolean = true
    )

    data class PlaylistState(
        val isPlaylistCreated: Boolean = false,
        val isPlaylistImported: Boolean = false,
        val error: String? = null
    )

    // 创建歌单
    fun createPlaylist(title: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(
                title = title,
                description = null,
                coverImage = null,
                isPublic = true,
                tagIds = null
            ).onSuccess {
                _playlistState.update { it.copy(isPlaylistCreated = true) }
            }.onError { _, msg ->
                _playlistState.update { it.copy(error = msg ?: "创建歌单失败") }
            }
        }
    }

    // 导入歌单
    fun importPlaylist(url: String) {
        viewModelScope.launch {
            importRepository.importMusic(url)
                .onSuccess {
                    _playlistState.update { it.copy(isPlaylistImported = true) }
                }
                .onError { _, msg ->
                    _playlistState.update { it.copy(error = msg ?: "导入歌单失败") }
                }
        }
    }

    // 重置歌单状态
    fun resetPlaylistState() {
        _playlistState.update { PlaylistState() }
    }
}
