package com.fox.music.core.data.mapper

import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Artist
import com.fox.music.core.model.social.Comment
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.music.Tag
import com.fox.music.core.model.user.User
import com.fox.music.core.model.music.AlbumDetail
import com.fox.music.core.model.music.ArtistDetail
import com.fox.music.core.model.music.PlayHistory
import com.fox.music.core.network.model.AlbumDto
import com.fox.music.core.network.model.AlbumDetailDto
import com.fox.music.core.network.model.ArtistDetailDto
import com.fox.music.core.network.model.ArtistDto
import com.fox.music.core.network.model.CommentDto
import com.fox.music.core.network.model.MusicDto
import com.fox.music.core.network.model.PagedResponse
import com.fox.music.core.network.model.PlayHistoryDto
import com.fox.music.core.network.model.TagDto
import com.fox.music.core.network.model.UserDto

fun TagDto.toTag(): Tag = Tag(
    id = id,
    name = name,
    type = type,
    category = category
)

fun UserDto.toUser(): User = User(
    id = id,
    username = username,
    email = email,
    nickname = nickname,
    avatar = avatar,
    signature = signature,
    role = role,
    status = status,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastLogin = lastLogin
)

fun ArtistDto.toArtist(): Artist = Artist(
    id = id,
    name = name,
    alias = alias,
    avatar = avatar,
    coverImage = coverImage,
    description = description,
    region = region,
    birthDate = birthDate,
    gender = gender,
    debutDate = debutDate,
    viewCount = viewCount,
    favoriteCount = favoriteCount,
    isVerified = isVerified,
    createdAt = createdAt,
    updatedAt = updatedAt,
    musicCount = musicCount,
    albumCount = albumCount,
    isFavorite = isFavorite,
    tags = tags.map { it.toTag() }
)

fun AlbumDto.toAlbum(): Album = Album(
    id = id,
    title = title,
    coverImage = coverImage,
    description = description,
    releaseDate = releaseDate,
    type = type,
    language = language,
    publisher = publisher,
    creatorId = creatorId,
    isPublic = isPublic,
    duration = duration,
    trackCount = trackCount,
    viewCount = viewCount,
    likeCount = likeCount,
    collectionCount = collectionCount,
    isFeatured = isFeatured,
    createdAt = createdAt,
    updatedAt = updatedAt,
    favoriteCount = favoriteCount,
    playCount = playCount,
    isFavorite = isFavorite,
    artists = artists.map { it.toArtist() }
)

fun MusicDto.toMusic(): Music = Music(
    id = id,
    title = title,
    description = description,
    url = url,
    coverImage = coverImage,
    duration = duration,
    trackNumber = trackNumber,
    discNumber = discNumber,
    genre = genre,
    language = language,
    lyrics = lyrics,
    lyricsTrans = lyricsTrans,
    lyricsUrl = lyricsUrl,
    playCount = playCount,
    likeCount = likeCount,
    commentCount = commentCount,
    collectionCount = collectionCount,
    avgPlayProgress = avgPlayProgress,
    completionRate = completionRate,
    isExplicit = isExplicit,
    isFeatured = isFeatured,
    createdBy = createdBy,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isFavorite = isFavorite,
    tags = tags.map { it.toTag() },
    artists = artists.map { it.toArtist() },
    album = album?.toAlbum()
)

fun CommentDto.toComment(): Comment = Comment(
    id = id,
    userId = userId,
    content = content,
    parentId = parentId,
    likeCount = likeCount,
    replyCount = replyCount,
    isLiked = isLiked,
    createdAt = createdAt,
    user = user?.toUser(),
    replies = replies.map { it.toComment() }
)

fun PlayHistoryDto.toPlayHistory(): PlayHistory = PlayHistory(
    id = id,
    music = music.toMusic(),
    playTime = playTime,
    progress = progress,
    duration = duration
)

fun <T, R> PagedResponse<T>.toPagedData(transform: (T) -> R): PagedData<R> = PagedData(
    list = list.map(transform),
    total = total,
    current = current,
    pageSize = pageSize,
    totalPages = totalPages
)

fun ArtistDetailDto.toArtistDetail(): ArtistDetail = ArtistDetail(
    artist = ArtistDto(
        id = id,
        name = name,
        alias = alias,
        avatar = avatar,
        coverImage = coverImage,
        description = description,
        region = region,
        birthDate = birthDate,
        gender = gender,
        debutDate = debutDate,
        viewCount = viewCount,
        favoriteCount = favoriteCount,
        isVerified = isVerified,
        createdAt = createdAt,
        updatedAt = updatedAt,
        musicCount = musicCount,
        albumCount = albumCount,
        isFavorite = isFavorite,
        tags = tags
    ).toArtist(),
    hotMusics = hotMusics.map { it.toMusic() },
    albums = albums.map { it.toAlbum() }
)

fun AlbumDetailDto.toAlbumDetail(): AlbumDetail = AlbumDetail(
    album = Album(
        id = id,
        title = title,
        coverImage = coverImage,
        description = description,
        releaseDate = releaseDate,
        type = type,
        language = language,
        publisher = publisher,
        creatorId = creatorId,
        isPublic = isPublic,
        duration = duration,
        trackCount = trackCount,
        viewCount = viewCount,
        likeCount = likeCount,
        collectionCount = collectionCount,
        isFeatured = isFeatured,
        createdAt = createdAt,
        updatedAt = updatedAt,
        favoriteCount = favoriteCount,
        playCount = playCount,
        isFavorite = isFavorite,
        artists = artists.map { it.toArtist() }
    ),
    tracks = tracks.toPagedData { it.toMusic() }
)
