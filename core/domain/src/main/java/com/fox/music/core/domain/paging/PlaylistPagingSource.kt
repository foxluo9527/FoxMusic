package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.music.Playlist

class PlaylistPagingSource(
    private val repo: PlaylistRepository,
    private val categoryId: Long? = null, // null 表示加载推荐歌单
): PagingSource<Int, Playlist>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Playlist> {
        return try {
            val page = params.key ?: 1

            // 根据 categoryId 决定加载推荐歌单还是分类歌单
            val result = if (categoryId == null) {
                repo.getRecommendedPlaylists(page = page, limit = params.loadSize)
            } else {
                repo.getCategoryPlaylists(categoryId = categoryId, page = page, limit = params.loadSize)
            }

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

    override fun getRefreshKey(state: PagingState<Int, Playlist>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
