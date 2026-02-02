package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Playlist
import javax.inject.Inject

class GetCategoryPlaylistsUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(
        categoryId: Long,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Playlist>> =
        playlistRepository.getCategoryPlaylists(categoryId, page, limit)
}
