package com.fox.music.core.domain.usecase

import androidx.paging.PagingData
import com.fox.music.core.model.music.Album
import kotlinx.coroutines.flow.Flow

class GetSearchAlbumPagingUseCase constructor(
    private val getAlbumListUseCase: GetAlbumListUseCase,
) {
    operator fun invoke(keyword: String): Flow<PagingData<Album>> {
        return getAlbumListUseCase.getPagingSource(keyword = keyword).flow
    }
}
