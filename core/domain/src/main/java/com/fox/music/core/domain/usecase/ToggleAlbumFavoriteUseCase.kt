package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import javax.inject.Inject

class ToggleAlbumFavoriteUseCase @Inject constructor(
    private val albumRepository: AlbumRepository,
) {
    suspend operator fun invoke(albumId: Long): Result<Unit> = albumRepository.toggleFavorite(albumId)
}
