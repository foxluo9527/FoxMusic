package com.fox.music.core.player.lyric

import androidx.media3.common.MediaMetadata
import com.fox.music.core.model.music.Music

object CarBluetoothLyricMetadata {
    fun build(
        music: Music,
        positionMs: Long,
        enabled: Boolean,
        base: MediaMetadata,
    ): MediaMetadata {
        if (!enabled) {
            return base.buildUpon()
                .setTitle(music.title)
                .setArtist(music.artists.joinToString(", ") { it.name })
                .build()
        }
        val lyric = music.getCurrentLyric(positionMs)?.takeIf { it.isNotBlank() } ?: music.title
        val artist = "${music.title}-${music.artists.joinToString(", ") { it.name }}"
        return base.buildUpon()
            .setTitle(lyric)
            .setArtist(artist)
            .build()
    }
}
