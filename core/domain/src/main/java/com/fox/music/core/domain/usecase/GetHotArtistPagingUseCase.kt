package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.ArtistPagingSource
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.model.music.Artist
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetHotArtistPagingUseCase @Inject constructor(
    private val artistRepository: ArtistRepository,
) {
    operator fun invoke(): Flow<PagingData<Artist>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20,
            ),
            pagingSourceFactory = {
                ArtistPagingSource(
                    repo = artistRepository,
                    sort = "hot",
                )
            },
        ).flow
    }
}
