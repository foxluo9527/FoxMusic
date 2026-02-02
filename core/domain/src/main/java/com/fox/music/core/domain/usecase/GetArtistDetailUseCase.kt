package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.ArtistDetail
import javax.inject.Inject

class GetArtistDetailUseCase @Inject constructor(
    private val artistRepository: ArtistRepository
) {
    suspend operator fun invoke(id: Long): Result<ArtistDetail> =
        artistRepository.getArtistDetail(id)
}
