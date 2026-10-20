package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatchingWithParser
import com.fox.music.core.domain.repository.MusicFeedbackRepository
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.model.ReplaceMusicSourceRequest
import com.fox.music.core.network.model.SubmitMusicFeedbackRequest
import com.fox.music.core.network.util.ErrorParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicFeedbackRepositoryImpl @Inject constructor(
    private val musicApi: MusicApiService,
) : MusicFeedbackRepository {

    override suspend fun submitFeedback(
        musicId: Long,
        content: String?,
    ): Result<Unit> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val response = musicApi.submitMusicFeedback(
            SubmitMusicFeedbackRequest(musicId = musicId, content = content),
        )
        if (response.isSuccess) {
            Unit
        } else {
            throw Exception(response.message.ifBlank { "反馈提交失败" })
        }
    }

    override suspend fun replaceMusicSource(
        musicId: Long,
        url: String,
        referer: String?,
        duration: Long?,
    ): Result<Unit> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val response = musicApi.replaceMusicSource(
            id = musicId,
            body = ReplaceMusicSourceRequest(
                url = url,
                referer = referer,
                duration = duration,
            ),
        )
        if (response.isSuccess) {
            Unit
        } else {
            throw Exception(response.message.ifBlank { "音源替换失败" })
        }
    }
}
