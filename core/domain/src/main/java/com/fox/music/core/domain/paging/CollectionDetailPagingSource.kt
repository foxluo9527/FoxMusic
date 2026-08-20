package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music

class CollectionDetailPagingSource(
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val detailId: Long,
    private val detailType: DetailType,
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
        return when (detailType) {
            DetailType.PLAYLIST -> {
                val detail = playlistRepository.getPlaylistDetail(detailId, page, limit).getOrThrow()
                detail.tracks.withTotalFallback(detail.playlist.trackCount)
            }
            DetailType.ALBUM -> {
                val detail = albumRepository.getAlbumDetail(detailId, page, limit).getOrThrow()
                detail.tracks.withTotalFallback(detail.album.trackCount)
            }
            DetailType.RANK -> {
                val detail = playlistRepository.getRankDetail(detailId, page, limit).getOrThrow()
                detail.tracks.withTotalFallback(detail.playlist.trackCount)
            }
            else -> throw IllegalArgumentException("Invalid detail type")
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Music>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
