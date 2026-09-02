package com.fox.music.core.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.model.user.User

class PostLikerPagingSource(
    private val socialRepository: SocialRepository,
    private val postId: Long,
) : PagingSource<Int, User>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, User> {
        return try {
            val page = params.key ?: 1
            val result = socialRepository.getPostLikes(postId, page, params.loadSize)
            val pagedData = result.getOrNull()
            result.exceptionOrNull()?.let { throw it }
            val data = pagedData?.list ?: emptyList()
            val prevKey = if (page > 1) page - 1 else null
            val nextKey = if (data.isNotEmpty() && (pagedData?.hasMore == true)) page + 1 else null
            LoadResult.Page(data = data, prevKey = prevKey, nextKey = nextKey)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, User>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}
