package com.fox.music.core.common.cache

import java.io.File

/**
 * Tab / 列表快照的磁盘信封：首行过期时间戳（epoch ms），之后是 payload。
 * 先写临时文件再替换，避免进程被杀时留下半截 JSON。
 */
internal data class SnapshotEnvelope(
    val expiresAtMs: Long,
    val payload: String,
)

internal object SnapshotFiles {
    fun file(dir: File, key: String): File {
        val safe = key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(180)
        return File(dir, "$safe.json")
    }

    fun write(dir: File, key: String, expiresAtMs: Long, payload: String) {
        dir.mkdirs()
        val file = file(dir, key)
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText("$expiresAtMs\n$payload")
        if (file.exists() && !file.delete()) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
            return
        }
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
    }

    fun read(dir: File, key: String, nowMs: Long = System.currentTimeMillis()): SnapshotEnvelope? {
        val file = file(dir, key)
        if (!file.exists()) return null
        val raw = file.readText()
        val nl = raw.indexOf('\n')
        if (nl <= 0) return null
        val expiresAtMs = raw.substring(0, nl).trim().toLongOrNull() ?: return null
        if (expiresAtMs < nowMs) {
            file.delete()
            return null
        }
        return SnapshotEnvelope(expiresAtMs, raw.substring(nl + 1))
    }
}
