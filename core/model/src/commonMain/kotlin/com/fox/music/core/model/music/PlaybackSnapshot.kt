package com.fox.music.core.model.music

data class PlaybackSnapshot(
    val playingKey: String? = null,
    val playlist: List<Music> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val repeatMode: RepeatMode = RepeatMode.ALL,
    val playlistSource: PlaybackPlaylistSource? = null,
    /** 本会话用户应用的歌词。恢复队列后继续压过曲库旧歌词。 */
    val lyricOverrides: Map<Long, LyricOverride> = emptyMap(),
) {
    val isValid: Boolean
        get() = playlist.isNotEmpty() && currentIndex in playlist.indices
}
