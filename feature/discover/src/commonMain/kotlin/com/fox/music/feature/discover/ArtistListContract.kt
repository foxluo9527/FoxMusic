package com.fox.music.feature.discover

import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.util.ArtistSection

data class ArtistListState(
    val sections: List<ArtistSection> = emptyList(),
    val availableLetters: List<Char> = emptyList(),
    val indexMap: Map<Char, Int> = emptyMap(),
    val isLoading: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface ArtistListIntent : UiIntent {
    data object Load : ArtistListIntent
}

sealed interface ArtistListEffect : UiEffect {
    data class NavigateToArtist(val artistId: Long) : ArtistListEffect
    data object NavigateBack : ArtistListEffect
}
