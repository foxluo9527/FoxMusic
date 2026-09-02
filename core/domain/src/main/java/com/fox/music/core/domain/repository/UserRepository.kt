package com.fox.music.core.domain.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.user.BlacklistUser
import com.fox.music.core.model.user.PrivacySettings
import com.fox.music.core.model.user.UserHomeProfile

interface UserRepository {

    suspend fun getUserHomeProfile(userId: Long): Result<UserHomeProfile>

    suspend fun getPrivacySettings(): Result<PrivacySettings>

    suspend fun updatePrivacySettings(
        isPrivate: Boolean? = null,
        isSearchable: Boolean? = null,
    ): Result<PrivacySettings>

    suspend fun getBlacklist(page: Int = 1, limit: Int = 50): Result<PagedData<BlacklistUser>>

    suspend fun blockUser(userId: Long): Result<Unit>

    suspend fun unblockUser(userId: Long): Result<Unit>

    suspend fun updateHomepageBackground(url: String?): Result<UserHomeProfile>
}
