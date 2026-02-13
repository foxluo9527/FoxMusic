package com.fox.music.core.data.mapper

import com.fox.music.core.model.Favorite
import com.fox.music.core.model.FavoriteType
import com.fox.music.core.network.model.FavoriteDto


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
