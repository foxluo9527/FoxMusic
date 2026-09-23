package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.model.music.Music
import com.fox.music.core.player.controller.MusicController
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import com.fox.music.feature.player.lyric.usecase.SearchCustomLyricPagingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

data class CustomLyricSelectorState(
    val searchKeyword: String = "",
    val currentMusicTitle: String = "",
    val currentMusicArtist: String = "",
    val pagingData: Flow<PagingData<CustomLyricItem>> = emptyFlow(),
    val currentPage: Int = 0,
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
    private val musicController: MusicController,
) : MviViewModel<CustomLyricSelectorState, CustomLyricSelectorIntent, CustomLyricSelectorEffect>(
    CustomLyricSelectorState()
) {

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
    }
}
