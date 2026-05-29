package com.fox.music.core.network.api

import com.fox.music.core.network.model.ApiResponse
import com.fox.music.core.network.model.UploadFileDto
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface UploadApiService {

    @Multipart
    @POST("api/upload/image")
    suspend fun uploadImage(@Part file: MultipartBody.Part): ApiResponse<UploadFileDto>

    @Multipart
    @POST("api/upload/audio")
    suspend fun uploadAudio(@Part file: MultipartBody.Part): ApiResponse<UploadFileDto>

    @Multipart
    @POST("api/upload/video")
    suspend fun uploadVideo(@Part file: MultipartBody.Part): ApiResponse<UploadFileDto>

    @Multipart
    @POST("api/upload/file")
    suspend fun uploadFile(@Part file: MultipartBody.Part): ApiResponse<UploadFileDto>
}
