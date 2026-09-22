package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository

class TogglePlaylistFavoriteUseCase constructor(
    private val playlistRepository: PlaylistRepository,
) {
    suspend operator fun invoke(playlistId: Long): Result<Unit> =
        playlistRepository.toggleFavorite(playlistId)
}
