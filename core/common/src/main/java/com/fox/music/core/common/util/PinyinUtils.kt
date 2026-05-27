package com.fox.music.core.common.util

import android.icu.text.Transliterator
import java.util.Locale

object PinyinUtils {

    private val transliterator: Transliterator by lazy {
        Transliterator.getInstance("Han-Latin/Names")
    }

    fun getInitialLetter(name: String): Char {
        if (name.isBlank()) return '#'
        val trimmed = name.trim()
        val first = trimmed.first()
        return when {
            first in 'A'..'Z' -> first
            first in 'a'..'z' -> first.uppercaseChar()
            isCjk(first) -> {
                val latin = transliterator.transliterate(first.toString()).trim()
                latin.firstOrNull()
                    ?.uppercaseChar()
                    ?.takeIf { it in 'A'..'Z' }
                    ?: '#'
            }
            else -> '#'
        }
    }

    fun getSortKey(name: String): String {
        if (name.isBlank()) return ""
        return buildString {
            name.forEach { char ->
                when {
                    char in 'A'..'Z' || char in 'a'..'z' -> append(char.lowercaseChar())
                    isCjk(char) -> append(transliterator.transliterate(char.toString()).lowercase(Locale.US))
                    char.isDigit() -> append(char)
                }
            }
        }
    }

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
    }
}
