package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.Artist
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetArtistListUseCase @Inject constructor(
    private val artistRepository: ArtistRepository
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Artist>> =
        artistRepository.getArtistList(page, limit, keyword, tagId, sort)
}
