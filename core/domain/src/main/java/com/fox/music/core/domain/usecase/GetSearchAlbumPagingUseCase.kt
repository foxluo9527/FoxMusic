package com.fox.music.core.domain.usecase

import androidx.paging.PagingData
import com.fox.music.core.model.music.Album
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSearchAlbumPagingUseCase @Inject constructor(
    private val getAlbumListUseCase: GetAlbumListUseCase,
) {
    operator fun invoke(keyword: String): Flow<PagingData<Album>> {
        return getAlbumListUseCase.getPagingSource(keyword = keyword).flow
    }
}
