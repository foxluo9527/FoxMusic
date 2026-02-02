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

    @POST("api/auth/refresh-token")
    suspend fun refreshToken(): ApiResponse<TokenResponse>

    @GET("api/auth/profile")
    suspend fun getProfile(): ApiResponse<UserDto>

    @PUT("api/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserDto>

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ApiResponse<Unit>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): ApiResponse<Unit>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiResponse<Unit>
}
