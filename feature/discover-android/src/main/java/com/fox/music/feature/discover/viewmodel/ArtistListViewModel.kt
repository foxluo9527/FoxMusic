package com.fox.music.feature.discover

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.GetAllArtistsUseCase
import com.fox.music.core.domain.util.ArtistGrouping
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistListViewModel @Inject constructor(
    private val getAllArtistsUseCase: GetAllArtistsUseCase,
) : MviViewModel<ArtistListState, ArtistListIntent, ArtistListEffect>(ArtistListState()) {

    override fun handleIntent(intent: ArtistListIntent) {
        when (intent) {
            ArtistListIntent.Load -> loadArtists()
        }
    }

    fun onArtistClick(artistId: Long) {
        sendEffect(ArtistListEffect.NavigateToArtist(artistId))
    }

    fun onBackClick() {
        sendEffect(ArtistListEffect.NavigateBack)
    }

    private fun loadArtists() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when (val result = getAllArtistsUseCase()) {
                is Result.Success -> {
                    val sections = ArtistGrouping.groupArtistsByInitial(result.data)
                    val (letters, indexMap) = ArtistGrouping.buildIndexMap(sections)
                    updateState {
                        copy(
                            sections = sections,
                            availableLetters = letters,
                            indexMap = indexMap,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is Result.Error -> {
                    updateState {
                        copy(
                            isLoading = false,
                            error = result.message ?: "加载失败",
                        )
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }
}
