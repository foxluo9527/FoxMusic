package com.fox.music.core.common.post

object PostPublishMessages {

    fun successMessage(
        isEdit: Boolean,
        skippedCount: Int,
        skippedHasImage: Boolean,
        skippedHasVideo: Boolean,
    ): String {
        val base = if (isEdit) "更新成功" else "发布成功"
        val suffix = skippedUploadSuffix(skippedCount, skippedHasImage, skippedHasVideo) ?: return base
        return "$base（$suffix）"
    }

    private fun skippedUploadSuffix(
        skippedCount: Int,
        skippedHasImage: Boolean,
        skippedHasVideo: Boolean,
    ): String? {
        if (skippedCount <= 0) return null
        return when {
            skippedHasImage && skippedHasVideo -> "${skippedCount}个图片与视频未能成功上传"
            skippedHasVideo -> "${skippedCount}个视频未能成功上传"
            else -> "${skippedCount}个图片未能成功上传"
        }
    }
}
