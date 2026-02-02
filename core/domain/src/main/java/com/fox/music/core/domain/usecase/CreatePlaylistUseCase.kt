package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.Playlist
import javax.inject.Inject

class CreatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(
        title: String,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean = true,
        tagIds: List<Long>? = null
    ): Result<Playlist> = playlistRepository.createPlaylist(
        title, description, coverImage, isPublic, tagIds
    )
}
