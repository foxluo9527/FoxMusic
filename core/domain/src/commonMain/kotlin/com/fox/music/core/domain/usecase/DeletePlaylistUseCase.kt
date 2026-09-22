package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository

class DeletePlaylistUseCase constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = playlistRepository.deletePlaylist(id)
}
