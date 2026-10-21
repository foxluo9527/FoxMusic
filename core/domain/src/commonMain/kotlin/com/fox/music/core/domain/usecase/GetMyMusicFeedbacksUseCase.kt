package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.MusicFeedbackRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.feedback.MusicFeedbackRecord

/** 我提交过的音源反馈记录 */
class GetMyMusicFeedbacksUseCase constructor(
    private val musicFeedbackRepository: MusicFeedbackRepository,
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        status: String? = null,
    ): Result<PagedData<MusicFeedbackRecord>> = musicFeedbackRepository.getMyFeedbacks(
        page = page,
        limit = limit,
        status = status,
    )
}
