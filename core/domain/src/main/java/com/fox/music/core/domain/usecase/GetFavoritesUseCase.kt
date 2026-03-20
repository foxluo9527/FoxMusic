package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.model.music.Favorite
import com.fox.music.core.model.music.FavoriteType
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetFavoritesUseCase @Inject constructor(
    private val favoriteRepository: FavoriteRepository
) {
    suspend operator fun invoke(
        type: FavoriteType? = null,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Favorite>> {
        return favoriteRepository.getFavorites(type, page, limit)
    }
}
