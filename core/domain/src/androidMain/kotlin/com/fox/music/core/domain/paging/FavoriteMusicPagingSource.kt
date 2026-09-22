package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.FavoriteRepository
import com.fox.music.core.model.music.Music

class FavoriteMusicPagingSource(
    private val favoriteRepository: FavoriteRepository,
    private val knownTotalProvider: () -> Int = { 0 },
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            val page = params.key ?: 1
            val pagedData = loadPagedPreferFull(
                page = page,
                requestedLimit = params.loadSize,
                knownTotal = knownTotalProvider(),
            ) { p, limit ->
                favoriteRepository.getFavoriteMusics(page = p, limit = limit).getOrThrow()
            }

            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (pagedData.hasMore) page + 1 else null

            LoadResult.Page(
                data = pagedData.list,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
