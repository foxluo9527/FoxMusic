package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toApiString
import com.fox.music.core.data.mapper.toFavorite
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.music.FavoriteType
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.FavoriteApiService
import javax.inject.Inject

class FavoriteRepositoryImpl @Inject constructor(
    private val favoriteApiService: FavoriteApiService
) : FavoriteRepository {

    override suspend fun getFavorites(
        type: FavoriteType?,
        page: Int,
        limit: Int
    ): Result<PagedData<Favorite>> {
        return suspendRunCatching {
            val response = favoriteApiService.getFavorites(
                type = type?.toApiString(),
                page = page,
                limit = limit
            )
            response.data?.toPagedData { it.toFavorite() } ?: PagedData(emptyList(), 0, 0)
        }
    }
}
