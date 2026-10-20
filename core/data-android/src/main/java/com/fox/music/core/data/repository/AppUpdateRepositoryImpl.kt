package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.download.ApkDownloadManager
import com.fox.music.core.data.mapper.toAppUpdateInfo
import com.fox.music.core.domain.repository.AppUpdateRepository
import com.fox.music.core.model.app.ApkDownloadState
import com.fox.music.core.model.app.AppUpdateInfo
import com.fox.music.core.network.api.AppUpdateApiService
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val appUpdateApi: AppUpdateApiService,
    private val apkDownloadManager: ApkDownloadManager,
) : AppUpdateRepository {

    override suspend fun checkUpdate(versionCode: Int, channel: String): Result<AppUpdateInfo> =
        suspendRunCatching {
            val response = appUpdateApi.checkUpdate(versionCode = versionCode, channel = channel)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.toAppUpdateInfo()
            } else {
                throw Exception(response.message.ifBlank { "检查更新失败" })
            }
        }

    override fun downloadApk(
        url: String,
        versionCode: Int,
        expectedSha256: String?,
        expectedSize: Long,
    ): Flow<ApkDownloadState> = apkDownloadManager.download(
        url = url,
        versionCode = versionCode,
        expectedSha256 = expectedSha256,
        expectedSize = expectedSize,
    )

    override fun getCachedApkFile(versionCode: Int): File? =
        apkDownloadManager.getCachedApkFile(versionCode)
}
