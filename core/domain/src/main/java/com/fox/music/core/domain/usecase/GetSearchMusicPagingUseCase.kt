package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.SearchMusicPagingSource
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.music.Music
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchMusicPagingUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(keyword: String): Flow<PagingData<Music>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = {
                SearchMusicPagingSource(
                    repo = searchRepository,
                    keyword = keyword
                )
            }
        ).flow
    }
}
