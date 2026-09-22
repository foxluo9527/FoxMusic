package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.report.Report

interface ReportRepository {

    suspend fun submitReport(
        targetType: String,
        targetId: Long,
        reason: String,
        description: String? = null,
    ): Result<Unit>

    suspend fun getReportHistory(
        page: Int = 1,
        limit: Int = 20,
        status: String? = null,
    ): Result<PagedData<Report>>
}
