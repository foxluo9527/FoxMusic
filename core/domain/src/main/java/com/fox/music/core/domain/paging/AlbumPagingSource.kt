package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.model.Album

/**
 *    Author : 罗福林
 *    Date   : 2026/2/13
 *    Desc   :
 */

class AlbumPagingSource(
    private val repo: AlbumRepository,
    private val keyword: String? = "",
    private val artistId: Long? = null,
    private val sort: String? = "",
): PagingSource<Int, Album>() {
    private val mapPageDataList = mutableMapOf<Int, List<Album>>()
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Album> {
        return try {
            // 尝试网络请求
            val page = params.key ?: 1
            val result = repo.getAlbumList(page, params.loadSize, keyword, artistId, sort)
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let {throw it}
            (pagedData?.list ?: emptyList()).let {data ->
                val prevKey = if (page > 1) page - 1 else null
                val nextKey = if (
                    data.isNotEmpty()
                    && page < (pagedData?.totalPages ?: 1) - 1
                ) page + 1 else null
                val list = data.filter {music ->
                    mapPageDataList.isEmpty() || mapPageDataList.toList()
                        .all {(index, list) ->
                            if (sort == "recommend" || index >= page - 2) {
                                list.all {
                                    it.id != music.id
                                }
                            } else {
                                true
                            }
                        }
                }
                mapPageDataList[page] = list
                LoadResult.Page(
                    data = list,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            }
        } catch (e: Exception) {
            // 异常情况回退本地
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Album>): Int? {
        return state.anchorPosition?.let {anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}