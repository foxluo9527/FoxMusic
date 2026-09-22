package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.app.ApkDownloadState
import com.fox.music.core.model.app.AppUpdateInfo
import kotlinx.coroutines.flow.Flow
import java.io.File

interface AppUpdateRepository {
    suspend fun checkUpdate(versionCode: Int, channel: String): Result<AppUpdateInfo>

    fun downloadApk(
        url: String,
        versionCode: Int,
        expectedSha256: String?,
        expectedSize: Long = 0,
    ): Flow<ApkDownloadState>

    fun getCachedApkFile(versionCode: Int): File?
}
