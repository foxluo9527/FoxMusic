package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.Playlist
import javax.inject.Inject

class UpdatePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(
        id: Long,
        title: String? = null,
        description: String? = null,
        coverImage: String? = null,
        isPublic: Boolean? = null,
        tagIds: List<Long>? = null
    ): Result<Playlist> = playlistRepository.updatePlaylist(
        id, title, description, coverImage, isPublic, tagIds
    )
}
