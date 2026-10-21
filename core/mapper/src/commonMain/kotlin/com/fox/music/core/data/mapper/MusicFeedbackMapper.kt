package com.fox.music.core.data.mapper

import com.fox.music.core.model.feedback.MusicFeedbackRecord
import com.fox.music.core.network.model.MyMusicFeedbackDto

fun MyMusicFeedbackDto.toMusicFeedbackRecord(): MusicFeedbackRecord = MusicFeedbackRecord(
    id = id,
    musicId = musicId,
    musicTitle = musicTitle.orEmpty(),
    musicArtists = musicArtists.orEmpty(),
    musicCover = musicCover,
    musicDuration = musicDuration,
    content = content,
    status = status.orEmpty(),
    createdAt = createdAt,
    handledAt = handledAt,
    handleNote = handleNote,
    autoRepairStatus = autoRepairStatus,
    autoRepairVia = autoRepairVia,
    autoRepairDetail = autoRepairDetail,
    autoRepairAt = autoRepairAt,
)
