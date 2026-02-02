package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toAlbum
import com.fox.music.core.data.mapper.toAlbumDetail
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.model.Album
import com.fox.music.core.model.AlbumDetail
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.AlbumApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlbumRepositoryImpl @Inject constructor(
    private val albumApi: AlbumApiService
) : AlbumRepository {

    override suspend fun getAlbumList(
        page: Int,
        limit: Int,
        keyword: String?,
        artistId: Long?,
        sort: String?
    ): Result<PagedData<Album>> = suspendRunCatching {
        val response = albumApi.getAlbumList(page, limit, keyword, artistId, sort)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toAlbum() }
        } else throw Exception(response.message)
    }

    override suspend fun getAlbumDetail(
        id: Long,
        page: Int,
        limit: Int
    ): Result<AlbumDetail> = suspendRunCatching {
        val response = albumApi.getAlbumDetail(id, page, limit)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toAlbumDetail()
        } else throw Exception(response.message)
    }

    override suspend fun toggleFavorite(id: Long): Result<Unit> = suspendRunCatching {
        val response = albumApi.toggleFavorite(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }
}
