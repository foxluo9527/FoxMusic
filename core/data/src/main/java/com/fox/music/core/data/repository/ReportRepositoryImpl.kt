package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.domain.repository.ReportRepository
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
}
