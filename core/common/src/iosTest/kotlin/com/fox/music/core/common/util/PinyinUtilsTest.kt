package com.fox.music.core.common.util

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * PinyinUtils 的 iOS actual 验证。
 *
 * 放 iosTest 而非 commonTest 的原因：Android actual 依赖 android.icu.text.Transliterator，
 * 而 android.jar 在宿主机单测里全是 stub（调用即抛 "not mocked"），
 * 所以这个用例无法在 androidHostTest 里跑，只能各自验证平台实现。
 *
 * 断言值与 Android 侧 "Han-Latin/Names"（不带声调）的预期一致，形成跨端语义对齐。
 */
class PinyinUtilsTest {

    @Test
    fun initialLetter_chinese_returnsPinyinFirstLetter() {
        assertEquals('Z', PinyinUtils.getInitialLetter("张三"))
    }

    @Test
    fun initialLetter_asciiLetter_uppercased() {
        assertEquals('A', PinyinUtils.getInitialLetter("alice"))
        assertEquals('B', PinyinUtils.getInitialLetter("Bob"))
    }

    @Test
    fun initialLetter_digitOrSymbol_returnsHash() {
        assertEquals('#', PinyinUtils.getInitialLetter("123"))
        assertEquals('#', PinyinUtils.getInitialLetter(""))
        assertEquals('#', PinyinUtils.getInitialLetter("   "))
    }

    @Test
    fun sortKey_chinese_toneStrippedLowercasePinyin() {
        // 若声调未被剥离，会得到 "zhāngsān" 而断言失败
        assertEquals("zhangsan", PinyinUtils.getSortKey("张三"))
    }

    @Test
    fun sortKey_mixed_keepsLettersAndDigits() {
        assertEquals("abc123", PinyinUtils.getSortKey("AbC123"))
        assertEquals("", PinyinUtils.getSortKey(""))
    }
}
