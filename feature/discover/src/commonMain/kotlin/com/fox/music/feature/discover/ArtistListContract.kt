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
    /** 进入页面：有缓存就先渲染，再按需回源 */
    data object Load : ArtistListIntent

    /** 手动刷新（重试 / 下拉）：强制回源，忽略节流 */
    data object Refresh : ArtistListIntent
}

sealed interface ArtistListEffect : UiEffect {
    data class NavigateToArtist(val artistId: Long) : ArtistListEffect
    data object NavigateBack : ArtistListEffect
}
