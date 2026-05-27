package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetHotArtistPagingUseCase
import com.fox.music.core.model.music.Artist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

data class HotArtistListState(
    val artists: Flow<PagingData<Artist>> = flowOf(PagingData.empty()),
) : UiState

sealed interface HotArtistListIntent : UiIntent {
    data object Load : HotArtistListIntent
}

sealed interface HotArtistListEffect : UiEffect {
    data class NavigateToArtist(val artistId: Long) : HotArtistListEffect
    data object NavigateToAllArtists : HotArtistListEffect
    data object NavigateBack : HotArtistListEffect
}

@HiltViewModel
class HotArtistListViewModel @Inject constructor(
    private val getHotArtistPagingUseCase: GetHotArtistPagingUseCase,
) : MviViewModel<HotArtistListState, HotArtistListIntent, HotArtistListEffect>(HotArtistListState()) {

    override fun handleIntent(intent: HotArtistListIntent) {
        when (intent) {
            HotArtistListIntent.Load -> loadArtists()
        }
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(HotArtistListEffect.NavigateToArtist(artistId))
    }

    fun onAllArtistsClick() {
        sendEffect(HotArtistListEffect.NavigateToAllArtists)
    }

    fun onBackClick() {
        sendEffect(HotArtistListEffect.NavigateBack)
    }

    private fun loadArtists() {
        updateState {
            copy(
                artists = getHotArtistPagingUseCase().cachedIn(viewModelScope),
            )
        }
    }
}
