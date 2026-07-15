package com.fox.music.core.data.download

/**
 * 断点续传策略：服务端若忽略 Range 并以 200 返回完整正文，必须清空本地再写入，
 * 否则会在已有字节后追加完整文件导致损坏。
 */
internal object DownloadResumePolicy {
    fun shouldRestartFromBeginning(httpCode: Int, existingBytes: Long): Boolean =
        httpCode == 200 && existingBytes > 0L
}
