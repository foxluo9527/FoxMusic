package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.domain.usecase.GetHotArtistPagingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

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
