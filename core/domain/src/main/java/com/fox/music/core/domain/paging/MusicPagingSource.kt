package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Music

class MusicPagingSource(
    private val repo: MusicRepository,
    private val keyword: String? = "",
    private val tagId: Long? = null,
    private val sort: String? = "",
): PagingSource<Int, Music>() {
    private val mapPageDataList = mutableMapOf<Int, List<Music>>()
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            // 尝试网络请求
            val page = params.key ?: 1
            val result = repo.getMusicList(page, params.loadSize, keyword, tagId, sort)
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

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? {
        return state.anchorPosition?.let {anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}