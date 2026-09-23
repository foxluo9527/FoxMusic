package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fox.music.core.domain.usecase.ToggleMusicFavoriteUseCase
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PlayerEffect {
    data class ShowToast(val message: String) : PlayerEffect
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val toggleMusicFavoriteUseCase: ToggleMusicFavoriteUseCase,
    private val musicController: MusicController,
) : ViewModel() {

    private val _effects = Channel<PlayerEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var isTogglingFavorite = false

    fun toggleFavorite(musicId: Long?) {
        val id = musicId ?: return
        if (isTogglingFavorite) return
        viewModelScope.launch {
            isTogglingFavorite = true
            val wasFavorite = musicController.playerState.value.let { state ->
                state.currentMusic?.takeIf { it.id == id }?.isFavorite ?: state.isFavorite
            }
            toggleMusicFavoriteUseCase(id)
                .onSuccess {
                    musicController.updateCurrentMusicFavorite(!wasFavorite)
                    _effects.send(
                        PlayerEffect.ShowToast(
                            if (!wasFavorite) "已收藏" else "已取消收藏"
                        )
                    )
                }
                .onError { _, msg ->
                    _effects.send(PlayerEffect.ShowToast(msg ?: "操作失败"))
                }
            isTogglingFavorite = false
        }
    }
}
