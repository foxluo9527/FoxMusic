package com.fox.music.core.common.util

import com.fox.music.core.common.constants.AppConstants

object MediaUrlResolver {

    fun isLocalMedia(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.startsWith("file://", ignoreCase = true) ||
            url.startsWith("content://", ignoreCase = true) ||
            isDeviceLocalPath(url)
    }

    fun resolve(url: String?): String? {
        if (url.isNullOrEmpty()) return null
        return when {
            url.startsWith("http://", ignoreCase = true) ||
                url.startsWith("https://", ignoreCase = true) -> url
            url.startsWith("file://", ignoreCase = true) -> url
            url.startsWith("content://", ignoreCase = true) -> url
            isDeviceLocalPath(url) -> "file://$url"
            url.startsWith("/") -> "${AppConstants.Network.MEDIA_BASE_URL}$url"
            else -> "${AppConstants.Network.MEDIA_BASE_URL}/$url"
        }
    }

    /** 仅将 Android 本地绝对路径视为 file://，API 返回的 /uploads/... 走 MEDIA_BASE_URL */
    private fun isDeviceLocalPath(url: String): Boolean =
        url.startsWith("/data/") ||
            url.startsWith("/storage/") ||
            url.startsWith("/sdcard/") ||
            url.startsWith("/mnt/")
}
