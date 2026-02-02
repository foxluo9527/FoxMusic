package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Artist
import com.fox.music.core.model.ArtistDetail
import com.fox.music.core.model.Music
import com.fox.music.core.model.PagedData

interface ArtistRepository {

    suspend fun getArtistList(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Artist>>

    suspend fun getArtistDetail(id: Long): Result<ArtistDetail>

    suspend fun getArtistMusics(
        id: Long,
        page: Int = 1,
        limit: Int = 20,
        sort: String? = null
    ): Result<PagedData<Music>>

    suspend fun toggleFavorite(id: Long): Result<Unit>
}
