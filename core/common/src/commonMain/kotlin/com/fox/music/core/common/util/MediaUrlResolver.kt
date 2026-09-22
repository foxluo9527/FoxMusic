package com.fox.music.core.common.util

import com.fox.music.core.common.constants.AppConstants

object MediaUrlResolver {

    fun isLocalMedia(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        val normalized = normalizeFileUri(url)
        return normalized.startsWith("file://", ignoreCase = true) ||
            normalized.startsWith("content://", ignoreCase = true) ||
            isDeviceLocalPath(normalized)
    }

    fun resolve(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        val normalized = normalizeFileUri(url)
        return when {
            normalized.startsWith("http://", ignoreCase = true) ||
                normalized.startsWith("https://", ignoreCase = true) -> normalized
            normalized.startsWith("file://", ignoreCase = true) -> normalized
            normalized.startsWith("content://", ignoreCase = true) -> normalized
            isDeviceLocalPath(normalized) -> "file://$normalized"
            normalized.startsWith("/") -> "${AppConstants.Network.MEDIA_BASE_URL}$normalized"
            else -> "${AppConstants.Network.MEDIA_BASE_URL}/$normalized"
        }
    }

    /**
     * [File.toURI] 在 Android 上常为 `file:/data/...`（双斜杠），
     * 需规范为 `file:///data/...` 才能被识别为本地媒体。
     */
    fun normalizeFileUri(url: String): String {
        if (url.startsWith("file://", ignoreCase = true)) return url
        if (url.startsWith("file:/", ignoreCase = true)) {
            return "file://${url.substring("file:".length)}"
        }
        return url
    }

    /** 仅将 Android 本地绝对路径视为 file://，API 返回的 /uploads/... 走 MEDIA_BASE_URL */
    private fun isDeviceLocalPath(url: String): Boolean =
        url.startsWith("/data/") ||
            url.startsWith("/storage/") ||
            url.startsWith("/sdcard/") ||
            url.startsWith("/mnt/")
}
