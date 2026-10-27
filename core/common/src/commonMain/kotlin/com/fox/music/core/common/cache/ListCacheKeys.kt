package com.fox.music.core.common.cache

/**
 * 列表 / 详情磁盘快照的 key。
 *
 * 账号维度放在最前（`u{userId}_`，未登录为 `uanon_`），后面再拼场景、id、分页，
 * 避免不同列表、不同页、不同账号写进同一个文件。前缀带下划线，这样按账号清理时
 * `u1_` 不会误删 `u12_`。
 */
object ListCacheKeys {
    /** 列表快照有效期：正常每次成功请求都会重写，TTL 只兜底过期数据。 */
    const val LIST_TTL_MS = 7L * 24 * 60 * 60 * 1000

    fun ownerPrefix(ownerUserId: Long?): String {
        val owner = ownerUserId?.toString() ?: "anon"
        return "u${owner}_"
    }

    fun key(ownerUserId: Long?, vararg parts: String): String {
        val raw = buildString {
            append(ownerPrefix(ownerUserId))
            parts.forEachIndexed { index, part ->
                if (index > 0) append('_')
                append(part)
            }
        }
        return raw.replace(Regex("[^A-Za-z0-9_]+"), "_").take(180)
    }
}
