package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import javax.inject.Inject

class DeletePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(id: Long): Result<Unit> = playlistRepository.deletePlaylist(id)
}
