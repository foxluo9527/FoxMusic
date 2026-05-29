package com.fox.music.core.data.download

import android.content.Context
import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.model.app.ApkDownloadState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(AppConstants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AppConstants.Network.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AppConstants.Network.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .build()

    fun getCachedApkFile(versionCode: Int): File? {
        val file = apkFile(versionCode)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    fun download(
        url: String,
        versionCode: Int,
        expectedSha256: String?,
        expectedSize: Long = 0,
    ): Flow<ApkDownloadState> = flow {
        val destFile = apkFile(versionCode)
        apkDir().mkdirs()

        try {
            if (destFile.exists()) {
                destFile.delete()
            }

            val request = Request.Builder().url(url).build()
            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                emit(ApkDownloadState.Failed("下载失败（HTTP ${response.code}）"))
                return@flow
            }

            val body = response.body
            if (body == null) {
                emit(ApkDownloadState.Failed("下载失败：响应体为空"))
                return@flow
            }

            val contentLength = body.contentLength()
            val totalBytes = when {
                contentLength > 0L -> contentLength
                expectedSize > 0L -> expectedSize
                else -> -1L
            }
            var downloadedBytes = 0L
            var lastEmittedProgress = -1
            var chunksSinceEmit = 0

            if (totalBytes > 0L) {
                emit(
                    ApkDownloadState.Downloading(
                        progress = 0,
                        downloadedBytes = 0,
                        totalBytes = totalBytes,
                    ),
                )
            }

            body.byteStream().use { input ->
                destFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        chunksSinceEmit++
                        val progress = if (totalBytes > 0L) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)
                        } else {
                            -1
                        }
                        val shouldEmit = when {
                            progress >= 0 -> progress != lastEmittedProgress
                            else -> chunksSinceEmit >= 16
                        }
                        if (shouldEmit) {
                            emit(
                                ApkDownloadState.Downloading(
                                    progress = progress.coerceAtLeast(0),
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                ),
                            )
                            if (progress >= 0) {
                                lastEmittedProgress = progress
                            }
                            chunksSinceEmit = 0
                            yield()
                        }
                    }
                }
            }

            if (!expectedSha256.isNullOrBlank()) {
                val actualSha256 = destFile.sha256()
                if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                    destFile.delete()
                    emit(ApkDownloadState.Failed("安装包校验失败，请重试"))
                    return@flow
                }
            }

            emit(
                ApkDownloadState.Downloading(
                    progress = 100,
                    downloadedBytes = downloadedBytes,
                    totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                ),
            )
            emit(ApkDownloadState.Completed(destFile))
        } catch (e: Exception) {
            destFile.delete()
            emit(ApkDownloadState.Failed(e.message ?: "下载失败"))
        }
    }.flowOn(Dispatchers.IO)

    private fun apkDir(): File = File(context.cacheDir, "apk")

    private fun apkFile(versionCode: Int): File = File(apkDir(), "foxmusic-$versionCode.apk")

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
