package com.fox.music.core.model.app

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

// ApkDownloadState 依赖 java.io.File（APK 下载安装属 Android 专有流程），
// 已拆分到 androidMain 的 ApkDownloadState.kt。
