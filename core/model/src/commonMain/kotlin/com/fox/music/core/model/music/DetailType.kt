package com.fox.music.core.model.music

enum class DetailType {
    PLAYLIST,  // 歌单
    ALBUM,     // 专辑
    RANK,       // 排行榜
    RECOMMEND,  // 推荐歌单
    NEW_MUSIC,  // 最新音乐
    FAVORITE_MUSIC, // 我的收藏
    PLAY_HISTORY,   // 历史播放
    ARTIST,         // 歌手全部歌曲
    ;

    companion object {
        const val RECOMMEND_ID = Long.MAX_VALUE
        const val NEW_MUSIC_ID = Long.MAX_VALUE - 1L
        const val FAVORITE_MUSIC_ID = Long.MAX_VALUE - 2L
        const val PLAY_HISTORY_ID = Long.MAX_VALUE - 3L
    }
}
