package com.fox.music.core.network.api

import com.fox.music.core.network.model.*
import retrofit2.http.*

interface AuthApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<LoginResponse>

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<LoginResponse>

    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @GET("api/auth/profile")
    suspend fun getProfile(): ApiResponse<UserDto>

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserDto>

    @GET("api/auth/privacy")
    suspend fun getPrivacySettings(): ApiResponse<com.fox.music.core.network.model.PrivacySettingsDto>

    @PUT("api/auth/privacy")
    suspend fun updatePrivacySettings(
        @Body request: com.fox.music.core.network.model.UpdatePrivacyRequest,
    ): ApiResponse<com.fox.music.core.network.model.PrivacySettingsDto>

    @GET("api/auth/blacklist")
    suspend fun getBlacklist(
        @retrofit2.http.Query("page") page: Int = 1,
        @retrofit2.http.Query("limit") limit: Int = 50,
    ): ApiResponse<PagedResponse<com.fox.music.core.network.model.BlacklistUserDto>>

    @DELETE("api/auth/blacklist/{userId}")
    suspend fun unblockUser(
        @Path("userId") userId: Long,
    ): ApiResponse<Unit?>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ApiResponse<Unit>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiResponse<Unit>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiResponse<Unit>

    @POST("api/auth/refresh-token")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): ApiResponse<TokenResponse>
}
