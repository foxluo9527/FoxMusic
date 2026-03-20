package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.music.Playlist
import javax.inject.Inject

class GetPlaylistListUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(userId: Long? = null): Result<List<Playlist>> =
        playlistRepository.getPlaylists(userId)
}
