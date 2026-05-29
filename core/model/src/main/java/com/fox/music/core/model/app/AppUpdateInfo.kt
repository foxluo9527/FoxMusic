package com.fox.music.core.model.app

import java.io.File

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val forceUpdate: Boolean,
    val apkUrl: String,
    val apkSize: Long,
    val apkSha256: String?,
    val changelog: List<String>,
    val upgradeTitle: String?,
    val upgradeContent: String?,
) {
    fun shouldForceUpdate(localVersionCode: Int): Boolean =
        forceUpdate || localVersionCode < minSupportedVersionCode
}

sealed interface ApkDownloadState {
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
    ) : ApkDownloadState
    data class Completed(val file: File) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}
