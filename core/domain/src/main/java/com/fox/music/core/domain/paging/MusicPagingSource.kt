package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.music.Music

class MusicPagingSource(
    private val repo: MusicRepository,
    private val keyword: String? = "",
    private val tagId: Long? = null,
    private val sort: String? = "",
    private val maxTotalItems: Int? = null,
    private val onRecommendSeedLoaded: ((String) -> Unit)? = null,
) : PagingSource<Int, Music>() {
    private val mapPageDataList = mutableMapOf<Int, List<Music>>()
    private val accumulatedExcludeIds = mutableListOf<Long>()
    private var currentSeed: String? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            val page = params.key ?: 1
            val excludeIdsParam = accumulatedExcludeIds.takeIf { it.isNotEmpty() }
                ?.joinToString(",")
            val result = repo.getMusicList(
                page, params.loadSize, keyword, tagId, sort,
                seed = currentSeed,
                excludeIds = excludeIdsParam,
            )
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }

            if (page == 1 && pagedData?.recommendSeed != null) {
                currentSeed = pagedData.recommendSeed
                onRecommendSeedLoaded?.invoke(pagedData.recommendSeed ?: "")
            }

            (pagedData?.list ?: emptyList()).let { data ->
                val prevKey = if (page > 1) page - 1 else null
                val nextKey =
                    if (data.isNotEmpty() && (pagedData?.hasMore == true)) page + 1 else null
                val list = if (currentSeed != null) {
                    data
                } else {
                    data.filter { music ->
                        mapPageDataList.isEmpty() || mapPageDataList.toList()
                            .all { (index, list) ->
                                if (sort == "recommend" || index >= page - 2) {
                                    list.all { it.id != music.id }
                                } else {
                                    true
                                }
                            }
                    }
                }
                accumulatedExcludeIds.addAll(list.map { it.id })
                val max = maxTotalItems
                val previousTotal = if (max != null) {
                    mapPageDataList.filterKeys { it < page }.values.sumOf { it.size }
                } else {
                    0
                }
                val cappedList = if (max != null) {
                    val remaining = max - previousTotal
                    when {
                        remaining <= 0 -> emptyList()
                        list.size > remaining -> list.take(remaining)
                        else -> list
                    }
                } else {
                    list
                }
                val cappedNextKey =
                    if (max != null && (previousTotal + cappedList.size >= max || cappedList.size < list.size)) {
                        null
                    } else {
                        nextKey
                    }
                mapPageDataList[page] = cappedList
                LoadResult.Page(
                    data = cappedList,
                    prevKey = prevKey,
                    nextKey = cappedNextKey
                )
            }
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