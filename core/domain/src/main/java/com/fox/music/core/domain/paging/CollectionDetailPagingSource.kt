package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.DetailType
import com.fox.music.core.model.Music

class CollectionDetailPagingSource(
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val detailId: Long,
    private val detailType: DetailType
) : PagingSource<Int, Music>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Music> {
        return try {
            val page = params.key ?: 1
            val pageSize = params.loadSize

            val result = when (detailType) {
                DetailType.PLAYLIST -> playlistRepository.getPlaylistDetail(detailId, page, pageSize)
                    .map { it.tracks }
                DetailType.ALBUM -> albumRepository.getAlbumDetail(detailId, page, pageSize)
                    .map { it.tracks }
                DetailType.RANK -> playlistRepository.getRankDetail(detailId, page, pageSize)
                    .map { it.tracks }
                else->throw IllegalArgumentException("Invalid detail type")
            }

            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }

            val data = pagedData?.list ?: emptyList()
            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (data.isNotEmpty() && pagedData?.hasMore == true) page + 1 else null

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
