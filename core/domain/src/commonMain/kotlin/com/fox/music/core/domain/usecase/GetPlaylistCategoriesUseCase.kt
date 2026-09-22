package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.music.PlaylistCategory

class GetPlaylistCategoriesUseCase constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend operator fun invoke(categoryType: String? = null): Result<List<PlaylistCategory>> =
        playlistRepository.getCategories(categoryType)
}
