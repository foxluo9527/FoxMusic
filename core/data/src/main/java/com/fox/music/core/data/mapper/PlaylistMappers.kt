package com.fox.music.core.data.mapper

import com.fox.music.core.model.PagedData
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.PlaylistCategory
import com.fox.music.core.model.PlaylistDetail
import com.fox.music.core.model.Music
import com.fox.music.core.network.model.PlaylistCategoryDto
import com.fox.music.core.network.model.PlaylistDetailDto
import com.fox.music.core.network.model.PlaylistDto

fun PlaylistDto.toPlaylist(): Playlist = Playlist(
    id = id,
    title = title,
    description = description,
    coverImage = coverImage,
    isPublic = isPublic,
    creatorId = creatorId,
    trackCount = trackCount,
    playCount = playCount,
    favoriteCount = favoriteCount,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isFavorite = isFavorite,
    tags = tags.map { it.toTag() },
    creator = creator?.toUser()
)

fun PlaylistDetailDto.toPlaylistDetail(): PlaylistDetail = PlaylistDetail(
    playlist = Playlist(
        id = id,
        title = title,
        description = description,
        coverImage = coverImage,
        isPublic = isPublic,
        creatorId = creatorId,
        trackCount = trackCount,
        playCount = playCount,
        favoriteCount = favoriteCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isFavorite = isFavorite,
        tags = tags.map { it.toTag() },
        creator = creator?.toUser()
    ),
    tracks = tracks.toPagedData { it.toMusic() }
)

fun PlaylistCategoryDto.toPlaylistCategory(): PlaylistCategory = PlaylistCategory(
    id = id,
    name = name,
    description = description,
    coverImage = coverImage,
    categoryType = categoryType,
    playlistCount = playlistCount
)
