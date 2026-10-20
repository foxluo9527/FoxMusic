package com.fox.music.core.data.repository

import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatchingWithParser
import com.fox.music.core.data.mapper.toBlacklistUser
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.mapper.toPrivacySettings
import com.fox.music.core.data.mapper.toUserHomeProfile
import com.fox.music.core.domain.repository.UserRepository
import com.fox.music.core.model.PagedData
import com.fox.music.core.model.user.BlacklistUser
import com.fox.music.core.model.user.PrivacySettings
import com.fox.music.core.model.user.UserHomeProfile
import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.api.UserApiService
import com.fox.music.core.network.model.UpdatePrivacyRequest
import com.fox.music.core.network.model.UpdateProfileRequest
import com.fox.music.core.network.util.ErrorParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepositoryImpl @Inject constructor(
    private val userApi: UserApiService,
    private val authApi: AuthApiService,
) : UserRepository {

    override suspend fun getUserHomeProfile(userId: Long): Result<UserHomeProfile> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = userApi.getUserHomeProfile(userId)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.toUserHomeProfile()
            } else throw Exception(response.message)
        }

    override suspend fun getPrivacySettings(): Result<PrivacySettings> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = authApi.getPrivacySettings()
            val data = response.data
            if (response.isSuccess && data != null) {
                data.toPrivacySettings()
            } else throw Exception(response.message)
        }

    override suspend fun updatePrivacySettings(
        isPrivate: Boolean?,
        isSearchable: Boolean?,
    ): Result<PrivacySettings> = suspendRunCatchingWithParser(ErrorParser::parseError) {
        val response = authApi.updatePrivacySettings(
            UpdatePrivacyRequest(isPrivate = isPrivate, isSearchable = isSearchable),
        )
        val data = response.data
        if (response.isSuccess && data != null) {
            data.toPrivacySettings()
        } else throw Exception(response.message)
    }

    override suspend fun getBlacklist(page: Int, limit: Int): Result<PagedData<BlacklistUser>> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = authApi.getBlacklist(page, limit)
            val data = response.data
            if (response.isSuccess && data != null) {
                data.toPagedData { it.toBlacklistUser() }
            } else throw Exception(response.message)
        }

    override suspend fun blockUser(userId: Long): Result<Unit> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = userApi.blockUser(userId)
            if (response.isSuccess) {
                Unit
            } else throw Exception(response.message)
        }

    override suspend fun unblockUser(userId: Long): Result<Unit> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = authApi.unblockUser(userId)
            if (response.isSuccess) {
                Unit
            } else throw Exception(response.message)
        }

    override suspend fun updateHomepageBackground(url: String?): Result<UserHomeProfile> =
        suspendRunCatchingWithParser(ErrorParser::parseError) {
            val response = authApi.updateProfile(UpdateProfileRequest(homepageBackground = url))
            val data = response.data
            if (response.isSuccess && data != null) {
                getUserHomeProfile(data.id).getOrThrow()
            } else throw Exception(response.message)
        }
}
