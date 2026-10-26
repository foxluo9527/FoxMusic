package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toArtist
import com.fox.music.core.data.mapper.toArtistDetail
import com.fox.music.core.data.mapper.toMusic
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.ArtistApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtistRepositoryImpl @Inject constructor(
    private val artistApi: ArtistApiService
) : ArtistRepository {

    override suspend fun getArtistList(
        page: Int,
        limit: Int,
        keyword: String?,
        tagId: Long?,
        sort: String?,
        slim: Boolean,
    ): Result<PagedData<Artist>> = suspendRunCatching {
        val response = artistApi.getArtistList(page, limit, keyword, tagId, sort, slim)
        val data = response.data
        if (response.isSuccess && data != null) {
            // 单页 500 条 DTO→Domain 映射；suspendRunCatching 不切线程，
            // Retrofit suspend 也是恢复到调用方上下文（= 主线程），所以这里显式挪到 Default。
            withContext(Dispatchers.Default) { data.toPagedData { it.toArtist() } }
        } else throw Exception(response.message)
    }

    override suspend fun getArtistDetail(id: Long): Result<ArtistDetail> = suspendRunCatching {
        val response = artistApi.getArtistDetail(id)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toArtistDetail()
        } else throw Exception(response.message)
    }

    override suspend fun getArtistMusics(
        id: Long,
        page: Int,
        limit: Int,
        sort: String?
    ): Result<PagedData<Music>> = suspendRunCatching {
        val response = artistApi.getArtistMusics(id, page, limit, sort)
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPagedData { it.toMusic() }
        } else throw Exception(response.message)
    }

    override suspend fun toggleFavorite(id: Long): Result<Unit> = suspendRunCatching {
        val response = artistApi.toggleFavorite(id)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }
}
