package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.Favorite
import com.fox.music.core.model.FavoriteType
import com.fox.music.core.model.PagedData

interface FavoriteRepository {

    suspend fun getFavorites(
        type: FavoriteType? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Favorite>>
}
