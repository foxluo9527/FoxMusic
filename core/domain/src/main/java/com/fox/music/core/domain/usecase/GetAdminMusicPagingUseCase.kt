package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.AdminMusicPagingSource
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.model.music.Music
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetAdminMusicPagingUseCase @Inject constructor(
    private val adminMusicRepository: AdminMusicRepository,
) {
    operator fun invoke(keyword: String? = null): Flow<PagingData<Music>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20,
            ),
            pagingSourceFactory = {
                AdminMusicPagingSource(
                    repo = adminMusicRepository,
                    keyword = keyword?.takeIf { it.isNotBlank() },
                )
            }
        ).flow
    }
}
