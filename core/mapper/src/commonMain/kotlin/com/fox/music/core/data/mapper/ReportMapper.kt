package com.fox.music.core.data.mapper

import com.fox.music.core.model.report.Report
import com.fox.music.core.network.model.ReportDto

fun ReportDto.toReport(): Report = Report(
    id = id,
    targetType = targetType.orEmpty(),
    targetId = targetId,
    reason = reason.orEmpty(),
    description = description,
    status = status.orEmpty(),
    createdAt = createdAt,
)
