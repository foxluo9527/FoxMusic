package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.model.Music
import com.fox.music.core.model.PagedData
import javax.inject.Inject

class SearchMusicUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        keyword: String,
        page: Int = 1,
        limit: Int = 20
    ): Result<PagedData<Music>> = searchRepository.searchMusic(keyword, page, limit)
}
