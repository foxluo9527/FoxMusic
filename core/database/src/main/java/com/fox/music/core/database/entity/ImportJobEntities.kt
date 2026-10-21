package com.fox.music.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray

@Entity(
    tableName = "import_playlist_jobs",
    indices = [Index(value = ["status"]), Index(value = ["createdAt"])],
)
data class ImportPlaylistJobEntity(
    @PrimaryKey val jobId: String,
    val playlistId: Long,
    val playlistTitle: String,
    val status: String,
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    val platform: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "import_track_jobs",
    indices = [
        Index(value = ["jobId", "trackIndex"]),
        Index(value = ["status"]),
    ],
)
data class ImportTrackJobEntity(
    @PrimaryKey val trackJobId: String,
    val jobId: String,
    val playlistId: Long,
    val trackIndex: Int,
    val title: String,
    val artistNames: String,
    val durationMs: Long = 0,
    val coverUrl: String? = null,
    val albumName: String? = null,
    val albumCover: String? = null,
    val platform: String? = null,
    val externalId: String? = null,
    val status: String,
    val detailUrl: String? = null,
    val matchScore: Int = 0,
    val titleRaw: String? = null,
    val errorMessage: String? = null,
    val musicId: Long? = null,
    val rejectedLinksJson: String? = null,
    /**
     * 原曲歌词（LRC 原文）。
     *
     * 导入入队时就用 platform/externalId 预取并存在这里：既给导入流程复用，
     * 也让详情页的手动换源能立刻拿到「原曲歌词」做对照——失败的曲目走不到
     * 导入成功分支，实时拉取又要等网络，落库后离线也有。
     */
    val lyrics: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun rejectedLinks(): Set<String> = parseRejectedLinks(rejectedLinksJson)
}

fun parseRejectedLinks(json: String?): Set<String> {
    if (json.isNullOrBlank()) return emptySet()
    return runCatching {
        val arr = JSONArray(json)
        buildSet {
            for (i in 0 until arr.length()) {
                arr.optString(i).trim().takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }.getOrDefault(emptySet())
}

fun mergeRejectedLinks(existingJson: String?, extra: Collection<String>): String? {
    val merged = parseRejectedLinks(existingJson).toMutableSet()
    extra.map { it.trim() }.filter { it.isNotBlank() }.forEach { merged.add(it) }
    if (merged.isEmpty()) return existingJson
    val arr = JSONArray()
    merged.forEach { arr.put(it) }
    return arr.toString()
}
