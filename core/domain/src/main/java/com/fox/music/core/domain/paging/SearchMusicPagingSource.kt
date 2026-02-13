package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.Music

class SearchMusicPagingSource(
    private val repo: SearchRepository,
    private val keyword: String,
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            val page = params.key ?: 1

            val result = repo.searchMusic(keyword = keyword, page = page, limit = params.loadSize)
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }

            val data = pagedData?.list ?: emptyList()
            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (data.isNotEmpty() && (pagedData?.hasMore == true)) page + 1 else null

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey
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
