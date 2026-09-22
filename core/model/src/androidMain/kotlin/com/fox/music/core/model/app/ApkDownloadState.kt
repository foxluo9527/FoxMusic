package com.fox.music.core.model.app

import java.io.File

/**
 * APK 下载状态。因 [Completed] 携带 [java.io.File]，且 APK 下载安装是 Android 专有流程，
 * 本类型放在 androidMain；纯数据的 [AppUpdateInfo] 则留在 commonMain 供跨端复用。
 */
sealed interface ApkDownloadState {
    data class Downloading(
        val progress: Int,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0,
    ) : ApkDownloadState
    data class Completed(val file: File) : ApkDownloadState
    data class Failed(val message: String) : ApkDownloadState
}
