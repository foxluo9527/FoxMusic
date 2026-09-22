package com.fox.music.core.data.mapper

import com.fox.music.core.model.music.FavoriteType


fun FavoriteType.toApiString(): String {
    return when (this) {
        FavoriteType.MUSIC -> "music"
        FavoriteType.VIDEO -> "video"
        FavoriteType.NOVEL -> "novel"
        FavoriteType.POST -> "post"
        FavoriteType.ARTIST -> "artist"
        FavoriteType.ALBUM -> "album"
        FavoriteType.PLAYLIST -> "playlist"
    }
}
