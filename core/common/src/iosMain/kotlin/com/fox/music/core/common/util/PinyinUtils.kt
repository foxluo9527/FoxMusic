package com.fox.music.core.common.util

import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateMutableCopy
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringGetCString
import platform.CoreFoundation.CFStringGetLength
import platform.CoreFoundation.CFStringGetMaximumSizeForEncoding
import platform.CoreFoundation.CFStringTransform
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFStringTransformStripDiacritics
import platform.CoreFoundation.kCFStringTransformToLatin

/**
 * iOS 侧实现：走 CoreFoundation 的 ICU 变换（与 Android 的 android.icu 同源）。
 *
 * 语义与 Android actual 对齐：
 * - Android 用 "Han-Latin/Names"，产出的是【不带声调】的拼音；
 *   iOS 的 kCFStringTransformToLatin 自带声调符号，因此要再接一道
 *   kCFStringTransformStripDiacritics 把声调剥掉，两端分组/排序结果才一致。
 * - CJK 扩展 B 区（U+20000 起，超出 Char 的 BMP 范围）两端都不支持，
 *   与 Android 的 UnicodeBlock.of(char) 对代理对返回 null 的行为一致（落到 '#'）。
 */
@OptIn(ExperimentalForeignApi::class)
actual object PinyinUtils {

    actual fun getInitialLetter(name: String): Char {
        if (name.isBlank()) return '#'
        val trimmed = name.trim()
        val first = trimmed.first()
        return when {
            first in 'A'..'Z' -> first
            first in 'a'..'z' -> first.uppercaseChar()
            isCjk(first) -> {
                val latin = first.toString().toPinyinWithoutTones().trim()
                latin.firstOrNull()
                    ?.uppercaseChar()
                    ?.takeIf { it in 'A'..'Z' }
                    ?: '#'
            }
            else -> '#'
        }
    }

    actual fun getSortKey(name: String): String {
        if (name.isBlank()) return ""
        return buildString {
            name.forEach { char ->
                when {
                    char in 'A'..'Z' || char in 'a'..'z' -> append(char.lowercaseChar())
                    isCjk(char) -> append(char.toString().toPinyinWithoutTones().lowercase())
                    char.isDigit() -> append(char)
                }
            }
        }
    }

    /**
     * 汉字转不带声调的拼音（非汉字输入会原样返回，调用方保证只在 isCjk 后使用）。
     *
     * 注意两个 Kotlin/Native interop 坑：
     * - Kotlin String 与 NSString 是无关类型，`as NSString` 编译只给警告、运行期必崩；
     *   这里绕开 NSString，直接用 CoreFoundation 的 C API。
     * - CFStringRef 系列是 C 指针（CPointer），不受 K/N 自动内存管理，必须手动 CFRelease。
     */
    private fun String.toPinyinWithoutTones(): String {
        val cfString = CFStringCreateWithCString(null, this, kCFStringEncodingUTF8) ?: return this
        val mutable = CFStringCreateMutableCopy(null, 0, cfString)
        CFRelease(cfString)
        if (mutable == null) return this
        try {
            // range 传 null = 整个字符串；false = 不做反向（拉丁→汉字）变换
            CFStringTransform(mutable, null, kCFStringTransformToLatin, false)
            CFStringTransform(mutable, null, kCFStringTransformStripDiacritics, false)

            val length = CFStringGetLength(mutable)
            val maxSize = CFStringGetMaximumSizeForEncoding(length, kCFStringEncodingUTF8) + 1
            memScoped {
                val buffer = allocArray<ByteVar>(maxSize)
                if (CFStringGetCString(mutable, buffer, maxSize, kCFStringEncodingUTF8)) {
                    return buffer.toKString()
                }
            }
            return this
        } finally {
            CFRelease(mutable)
        }
    }

    private fun isCjk(char: Char): Boolean =
        char in '\u4E00'..'\u9FFF' || // CJK Unified Ideographs
            char in '\u3400'..'\u4DBF' || // Extension A
            char in '\uF900'..'\uFAFF' // Compatibility Ideographs
}
