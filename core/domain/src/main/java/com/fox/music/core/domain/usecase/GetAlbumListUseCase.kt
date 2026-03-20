package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.paging.AlbumPagingSource
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.model.music.Album
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

    fun getPagingSource(
        limit: Int = 20,
        keyword: String? = null,
        artistId: Long? = null,
        sort: String? = null,
    ) = Pager(
        config = PagingConfig(
            pageSize = limit,
            prefetchDistance = 20,
            initialLoadSize = limit
        ),
        pagingSourceFactory = {AlbumPagingSource(albumRepository, keyword, artistId, sort)}
    )
}
