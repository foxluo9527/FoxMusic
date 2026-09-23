package com.fox.music.feature.player.lyric.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.feature.player.lyric.data.CustomLyricItem
import com.fox.music.feature.player.lyric.paging.CustomLyricSearchPagingSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SearchCustomLyricPagingUseCase @Inject constructor(
    private val searchRepository: SearchRepository,
) {
    operator fun invoke(keyword: String, targetTitle: String): Flow<PagingData<CustomLyricItem>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = {
                CustomLyricSearchPagingSource(
                    repo = searchRepository,
                    keyword = keyword,
                    targetTitle = targetTitle
                )
            }
        ).flow
    }
}
