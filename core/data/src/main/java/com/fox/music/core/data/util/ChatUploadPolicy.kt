package com.fox.music.core.data.util

object ChatUploadPolicy {
    /** 上传链接有效期：8 小时内未入库则需重新上传 */
    const val UPLOAD_URL_VALIDITY_MS = 8 * 60 * 60 * 1000L

    fun isUploadStillValid(uploadedAt: Long?): Boolean {
        if (uploadedAt == null || uploadedAt <= 0L) return false
        return System.currentTimeMillis() - uploadedAt < UPLOAD_URL_VALIDITY_MS
    }
}
