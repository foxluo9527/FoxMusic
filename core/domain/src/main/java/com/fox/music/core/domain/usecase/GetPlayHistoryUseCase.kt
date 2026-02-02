package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.PlayHistory
import javax.inject.Inject

class GetPlayHistoryUseCase @Inject constructor(
    private val musicRepository: MusicRepository
) {
    suspend operator fun invoke(page: Int = 1, limit: Int = 20): Result<PagedData<PlayHistory>> =
        musicRepository.getPlayHistory(page, limit)
}
