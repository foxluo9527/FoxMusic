package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.model.Album
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetAlbumListUseCase @Inject constructor(
    private val albumRepository: AlbumRepository
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        artistId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Album>> =
        albumRepository.getAlbumList(page, limit, keyword, artistId, sort)
}
