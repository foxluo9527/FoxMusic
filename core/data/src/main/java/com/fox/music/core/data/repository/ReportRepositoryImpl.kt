package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toReport
import com.fox.music.core.domain.repository.ReportRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.report.Report
import com.fox.music.core.network.api.ReportApiService
import com.fox.music.core.network.model.SubmitReportRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportRepositoryImpl @Inject constructor(
    private val reportApi: ReportApiService,
) : ReportRepository {

    override suspend fun submitReport(
        targetType: String,
        targetId: Long,
        reason: String,
        description: String?,
    ): Result<Unit> = suspendRunCatching {
        val response = reportApi.submitReport(
            SubmitReportRequest(
                targetType = targetType,
                targetId = targetId,
                reason = reason,
                description = description,
            ),
        )
        if (response.isSuccess) {
            Unit
        } else {
            throw Exception(response.message.ifBlank { "举报提交失败" })
        }
    }

    override suspend fun getReportHistory(
        page: Int,
        limit: Int,
        status: String?,
    ): Result<PagedData<Report>> = suspendRunCatching {
        val response = reportApi.getReports(page = page, limit = limit, status = status)
        if (response.isSuccess) {
            (response.data ?: com.fox.music.core.network.model.PagedResponse())
                .toPagedData { it.toReport() }
        } else {
            throw Exception(response.message.ifBlank { "获取举报历史失败" })
        }
    }
}
