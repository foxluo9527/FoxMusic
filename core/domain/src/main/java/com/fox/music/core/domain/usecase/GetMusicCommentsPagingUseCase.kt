package com.fox.music.core.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fox.music.core.domain.paging.CommentPagingSource
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.Comment
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMusicCommentsPagingUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    operator fun invoke(musicId: Long): Flow<PagingData<Comment>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false,
                initialLoadSize = 20
            ),
            pagingSourceFactory = {
                CommentPagingSource(
                    repo = musicRepository,
                    musicId = musicId
                )
            }
        ).flow
    }
}

