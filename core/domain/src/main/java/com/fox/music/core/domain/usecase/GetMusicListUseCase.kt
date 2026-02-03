package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.paging.MusicPagingSource
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Music
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class GetMusicListUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null
    ): Result<PagedData<Music>> = musicRepository.getMusicList(page, limit, keyword, tagId, sort)

    fun getPagingSource(
        limit: Int = 20,
        keyword: String? = null,
        tagId: Long? = null,
        sort: String? = null,
    ) = Pager(
        config = PagingConfig(
            pageSize = limit,
            prefetchDistance = 5,
            initialLoadSize = limit
        ),
        pagingSourceFactory = {MusicPagingSource(musicRepository, keyword, tagId, sort)}
    )
}
