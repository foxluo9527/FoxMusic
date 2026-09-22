package com.fox.music.core.common.util

/**
 * 汉字转拼音（用于联系人/艺术家按 A-Z 分组与排序）。
 *
 * 这是平台能力：Android 侧用 [android.icu.text.Transliterator] 实现，iOS 侧需要另行实现
 * （计划用 CoreFoundation 的 CFStringTransform 转 Latin）。因此以 expect/actual 暴露，
 * 让依赖它的业务逻辑（如 core:domain 的 ArtistGrouping）能留在 commonMain 被跨端复用。
 */
expect object PinyinUtils {

    /**
     * 取名称的首字母，用于 A-Z 分组。
     * 非字母且非汉字时返回 '#'。
     */
    fun getInitialLetter(name: String): Char

    /**
     * 取排序键（拼音字母 + 数字），用于同一分组内的排序。
     */
    fun getSortKey(name: String): String
}
