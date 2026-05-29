package com.fox.music.core.domain.usecase

import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AppUpdateRepository
import com.fox.music.core.model.app.AppUpdateInfo
import javax.inject.Inject

class CheckAppUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
) {
    suspend operator fun invoke(
        versionCode: Int,
        channel: String = "official",
    ): Result<AppUpdateInfo> {
        return when (val result = appUpdateRepository.checkUpdate(versionCode, channel)) {
            is Result.Success -> {
                val info = result.data
                if (info.hasUpdate && info.latestVersionCode > versionCode) {
                    Result.Success(info)
                } else {
                    Result.Success(
                        info.copy(
                            hasUpdate = false,
                        ),
                    )
                }
            }
            is Result.Error -> result
            is Result.Loading -> result
        }
    }
}
