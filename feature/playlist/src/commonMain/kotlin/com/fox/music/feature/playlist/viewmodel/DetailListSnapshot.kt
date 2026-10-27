package com.fox.music.feature.playlist.viewmodel

import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 歌单 / 榜单 / 专辑 / 歌手歌曲列表的磁盘快照。
 * 头部和已加载曲目一起存，冷启动或断网时先铺这一份，网络成功后再覆盖。
 */
@Serializable
data class DetailListSnapshot(
    val id: Long,
    val detailType: String,
    val title: String,
    val coverImage: String? = null,
    val description: String? = null,
    val trackCount: Int = 0,
    val creatorName: String? = null,
    val creatorId: Long? = null,
    val createdAt: String? = null,
    val isFavorite: Boolean = false,
    val artists: String? = null,
    val releaseDate: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val tags: List<String> = emptyList(),
    val tracks: List<Music> = emptyList(),
) {
    fun toHeader(): HeaderInfo = HeaderInfo(
        id = id,
        title = title,
        coverImage = coverImage,
        description = description,
        trackCount = trackCount,
        creatorName = creatorName,
        creatorId = creatorId,
        createdAt = createdAt,
        isFavorite = isFavorite,
        detailType = runCatching { DetailType.valueOf(detailType) }.getOrDefault(DetailType.PLAYLIST),
        artists = artists,
        releaseDate = releaseDate,
        publisher = publisher,
        language = language,
        tags = tags,
    )

    fun encode(): String = detailListJson.encodeToString(serializer(), this)

    companion object {
        fun from(header: HeaderInfo, tracks: List<Music>) = DetailListSnapshot(
            id = header.id,
            detailType = header.detailType.name,
            title = header.title,
            coverImage = header.coverImage,
            description = header.description,
            trackCount = header.trackCount,
            creatorName = header.creatorName,
            creatorId = header.creatorId,
            createdAt = header.createdAt,
            isFavorite = header.isFavorite,
            artists = header.artists,
            releaseDate = header.releaseDate,
            publisher = header.publisher,
            language = header.language,
            tags = header.tags,
            tracks = tracks,
        )

        fun decode(raw: String): DetailListSnapshot? =
            runCatching { detailListJson.decodeFromString(serializer(), raw) }.getOrNull()
    }
}

private val detailListJson = Json { ignoreUnknownKeys = true }
