package com.fox.music.core.domain.util

import com.fox.music.core.common.util.PinyinUtils
import com.fox.music.core.model.music.Artist

data class ArtistSection(
    val letter: Char,
    val artists: List<Artist>,
)

object ArtistGrouping {

    /**
     * decorate-sort-undecorate：每条歌手只算一次拼音排序键，再按预计算的键排序。
     *
     * 旧实现是 `groupBy { getInitialLetter }` + `sortedBy { getSortKey }`：
     * `sortedBy` 的 selector 在**每次比较**时都会重新求值，5500 人时 `getSortKey`
     * 被调用约 2·N·log2N ≈ 13.6 万次；而 `getSortKey` 内部每个汉字还要过一次 ICU
     * Transliterator（3~4 字的名字 = 每次 3~4 次 transliterate），接近 50 万次 ICU 调用
     * 直接压在主线程上 —— 这就是「全部歌手」页冻住十几秒的根因。
     */
    fun groupArtistsByInitial(artists: List<Artist>): List<ArtistSection> {
        val keyed = ArrayList<Keyed>(artists.size)
        val seenIds = HashSet<Long>(artists.size)
        for (artist in artists) {
            // 上游 GetAllArtistsUseCase 已按 id 去重，这里保留一层防御。
            // 用 HashSet 判定而不是 distinctBy，避免再产生一份全量中间 List。
            if (!seenIds.add(artist.id)) continue
            val sortKey = PinyinUtils.getSortKey(artist.name)
            keyed += Keyed(
                artist = artist,
                letter = letterOf(artist.name, sortKey),
                sortKey = sortKey,
            )
        }

        return keyed
            .groupBy { it.letter }
            .entries
            .sortedWith(compareBy({ if (it.key == '#') 1 else 0 }, { it.key }))
            .map { (letter, items) ->
                ArtistSection(
                    letter = letter,
                    // id 只作同键兜底，保证顺序稳定（相同拼音时不会随数据顺序漂移）
                    artists = items.sortedWith(ITEM_ORDER).map { it.artist },
                )
            }
    }

    /**
     * 首字母直接取自已算好的排序键，不再为分组单独 transliterate 一遍。
     *
     * 与 [PinyinUtils.getInitialLetter] 结果对齐：ASCII 字母原样大写，其余（汉字取拼音首字母、
     * 数字与符号）落到 '#'。
     */
    private fun letterOf(name: String, sortKey: String): Char {
        if (name.isBlank()) return '#'
        val first = name.trim().first()
        return when {
            first in 'A'..'Z' -> first
            first in 'a'..'z' -> first.uppercaseChar()
            else -> sortKey.firstOrNull()
                ?.uppercaseChar()
                ?.takeIf { it in 'A'..'Z' }
                ?: '#'
        }
    }

    private data class Keyed(
        val artist: Artist,
        val letter: Char,
        val sortKey: String,
    )

    private val ITEM_ORDER = compareBy<Keyed>({ it.sortKey }, { it.artist.id })

    fun buildIndexMap(sections: List<ArtistSection>): Pair<List<Char>, Map<Char, Int>> {
        val availableLetters = sections.map { it.letter }
        var index = 0
        val indexMap = mutableMapOf<Char, Int>()
        sections.forEach { section ->
            indexMap[section.letter] = index
            index += 1 + section.artists.size
        }
        return availableLetters to indexMap
    }
}
