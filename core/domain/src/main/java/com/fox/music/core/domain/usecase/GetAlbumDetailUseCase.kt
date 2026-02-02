package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.model.AlbumDetail
import javax.inject.Inject

class GetAlbumDetailUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke(id: Long, page: Int = 1, limit: Int = 20): Result<AlbumDetail> =
        albumRepository.getAlbumDetail(id, page, limit)
}
