package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Album
import com.fox.music.core.model.AlbumDetail
import com.fox.music.core.model.PagedData

interface AlbumRepository {

    suspend fun getAlbumList(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        artistId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Album>>

    suspend fun getAlbumDetail(id: Long, page: Int = 1, limit: Int = 20): Result<AlbumDetail>

    suspend fun toggleFavorite(id: Long): Result<Unit>
}
