package com.fox.music.core.domain.util

import com.fox.music.core.common.util.PinyinUtils
import com.fox.music.core.model.music.Artist

data class ArtistSection(
    val letter: Char,
    val artists: List<Artist>,
)

object ArtistGrouping {

    fun groupArtistsByInitial(artists: List<Artist>): List<ArtistSection> {
        val grouped = artists
            .distinctBy { it.id }
            .groupBy { PinyinUtils.getInitialLetter(it.name) }

        return grouped.keys
            .sortedWith(compareBy({ if (it == '#') 1 else 0 }, { it }))
            .map { letter ->
                ArtistSection(
                    letter = letter,
                    artists = grouped.getValue(letter)
                        .sortedBy { PinyinUtils.getSortKey(it.name) },
                )
            }
    }

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
