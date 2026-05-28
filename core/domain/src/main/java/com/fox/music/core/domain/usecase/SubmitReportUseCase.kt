package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ReportRepository
import javax.inject.Inject

class SubmitReportUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(
        targetType: String,
        targetId: Long,
        reason: String,
        description: String? = null,
    ): Result<Unit> = reportRepository.submitReport(
        targetType = targetType,
        targetId = targetId,
        reason = reason,
        description = description,
    )
}
