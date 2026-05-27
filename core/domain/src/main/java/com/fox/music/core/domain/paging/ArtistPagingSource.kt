package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.music.Artist

class ArtistPagingSource(
    private val repo: ArtistRepository,
    private val keyword: String? = null,
    private val sort: String? = null,
) : PagingSource<Int, Artist>() {

    private val seenIds = mutableSetOf<Long>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artist> {
        return try {
            val page = params.key ?: 1
            val result = repo.getArtistList(
                page = page,
                limit = params.loadSize,
                keyword = keyword,
                sort = sort,
            )
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }

            val data = (pagedData?.list ?: emptyList()).filter { seenIds.add(it.id) }
            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (data.isNotEmpty() && (pagedData?.hasMore == true)) page + 1 else null

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Artist>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
