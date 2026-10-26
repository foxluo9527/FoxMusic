package com.fox.music.core.common.util

import android.icu.text.Transliterator

actual object PinyinUtils {

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names")
    }

    /**
     * transliterator 与两个 LRU 缓存共用一把锁：
     * - `android.icu.text.Transliterator` **不是线程安全**的，而分组排序现在跑在
     *   `Dispatchers.Default` 上，可能被多个线程同时调用；
     * - `LinkedHashMap(accessOrder = true)` 本身也不允许并发读写。
     * 锁内只做一次整串 transliterate，粒度足够粗、代价远低于重复 ICU 调用。
     */
    private val lock = Any()

    private const val SORT_KEY_CACHE_LIMIT = 4096
    private const val INITIAL_CACHE_LIMIT = 512

    /** 有界 LRU：5000+ 艺人里重名很常见，且用户反复进出页面，缓存能省掉大量 ICU 调用 */
    private val sortKeyCache = object : LinkedHashMap<String, String>(
        SORT_KEY_CACHE_LIMIT,
        0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > SORT_KEY_CACHE_LIMIT
    }

    private val initialCache = object : LinkedHashMap<Char, Char>(
        INITIAL_CACHE_LIMIT,
        0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Char, Char>?): Boolean =
            size > INITIAL_CACHE_LIMIT
    }

    actual fun getInitialLetter(name: String): Char {
        if (name.isBlank()) return '#'
        val trimmed = name.trim()
        val first = trimmed.first()
        return when {
            first in 'A'..'Z' -> first
            first in 'a'..'z' -> first.uppercaseChar()
            isCjk(first) -> synchronized(lock) {
                initialCache[first] ?: transliterateInitial(first).also { initialCache[first] = it }
            }
            else -> '#'
        }
    }

    /**
     * 排序键：整串**一次** transliterate，只对结果做必要的清洗。
     *
     * 旧的逐字实现会对每个汉字单独调用一次 Transliterator（3~4 字的名字 = 3~4 次 ICU），
     * 而 `Han-Latin/Names` 本身就是整串可用的；ICU 会在音节之间插空格，所以下面只保留
     * 字母/数字，得到与旧实现等价的「拼音连写」结果。
     */
    actual fun getSortKey(name: String): String {
        if (name.isBlank()) return ""
        synchronized(lock) {
            sortKeyCache[name]?.let { return it }
            val latin = transliterator.transliterate(name)
            val key = buildString(latin.length) {
                for (ch in latin) {
                    if (ch.isLetterOrDigit()) append(ch.lowercaseChar())
                }
            }
            sortKeyCache[name] = key
            return key
        }
    }

    /** 调用方必须已持有 [lock] */
    private fun transliterateInitial(char: Char): Char =
        transliterator.transliterate(char.toString())
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.takeIf { it in 'A'..'Z' }
            ?: '#'

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
