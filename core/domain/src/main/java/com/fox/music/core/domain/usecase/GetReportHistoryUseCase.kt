package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.ReportRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.report.Report
import javax.inject.Inject

class GetReportHistoryUseCase @Inject constructor(
    private val reportRepository: ReportRepository,
) {
    suspend operator fun invoke(
        page: Int = 1,
        limit: Int = 20,
        status: String? = null,
    ): Result<PagedData<Report>> = reportRepository.getReportHistory(
        page = page,
        limit = limit,
        status = status,
    )
}
