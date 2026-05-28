package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result

interface ReportRepository {

    suspend fun submitReport(
        targetType: String,
        targetId: Long,
        reason: String,
        description: String? = null,
    ): Result<Unit>
}
