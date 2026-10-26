package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Music

/**
 * 歌手全部歌曲的分页源：复用歌单详情的分页机制，按歌手 id 拉取。
 */
class ArtistMusicPagingSource(
    private val artistRepository: ArtistRepository,
    private val artistId: Long,
    private val knownTotalProvider: () -> Int = { 0 },
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            val page = params.key ?: 1
            val pagedData = loadPagedPreferFull(
                page = page,
                requestedLimit = params.loadSize,
                knownTotal = knownTotalProvider(),
                fetch = ::fetchTracks,
            )

            val data = pagedData.list
            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (data.isNotEmpty() && pagedData.hasMore) page + 1 else null

            LoadResult.Page(
                data = data,
                prevKey = prevKey,
                nextKey = nextKey,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private suspend fun fetchTracks(page: Int, limit: Int): PagedData<Music> {
        return artistRepository.getArtistMusics(artistId, page, limit).getOrThrow()
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
