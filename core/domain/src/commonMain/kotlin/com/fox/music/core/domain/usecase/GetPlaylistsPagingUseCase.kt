package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.PlaylistPagingSource
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.model.music.Playlist
import kotlinx.coroutines.flow.Flow

class GetPlaylistsPagingUseCase constructor(
    private val playlistRepository: PlaylistRepository
) {
    operator fun invoke(
        categoryId: Long? = null,
        onFirstPage: (suspend (List<Playlist>) -> Unit)? = null,
    ): Flow<PagingData<Playlist>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = {
                PlaylistPagingSource(
                    repo = playlistRepository,
                    categoryId = categoryId,
                    onFirstPage = onFirstPage,
                )
            }
        ).flow
    }
}
