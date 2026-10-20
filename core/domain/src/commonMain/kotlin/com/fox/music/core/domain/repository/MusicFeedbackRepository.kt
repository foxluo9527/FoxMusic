package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result

/** 音源反馈与纠错替换 */
interface MusicFeedbackRepository {

    /** 提交「音乐不匹配」反馈 */
    suspend fun submitFeedback(musicId: Long, content: String? = null): Result<Unit>

    /** 替换音乐音源（仅管理员；url 可为站内 temp 路径或第三方直链，服务端负责落 OSS） */
    suspend fun replaceMusicSource(
        musicId: Long,
        url: String,
        referer: String? = null,
        duration: Long? = null,
    ): Result<Unit>
}
