package com.fox.music.core.network.di

import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.network.BuildConfig
import com.fox.music.core.network.api.*
import com.fox.music.core.network.interceptor.AuthInterceptor
import com.fox.music.core.network.interceptor.TokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConstants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(AppConstants.Network.READ_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(AppConstants.Network.WRITE_TIMEOUT, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .addInterceptor(loggingInterceptor)
        .authenticator(tokenAuthenticator)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides
    @Singleton
    fun provideMusicApiService(retrofit: Retrofit): MusicApiService =
        retrofit.create(MusicApiService::class.java)

    @Provides
    @Singleton
    fun provideAlbumApiService(retrofit: Retrofit): AlbumApiService =
        retrofit.create(AlbumApiService::class.java)

    @Provides
    @Singleton
    fun provideArtistApiService(retrofit: Retrofit): ArtistApiService =
        retrofit.create(ArtistApiService::class.java)

    @Provides
    @Singleton
    fun providePlaylistApiService(retrofit: Retrofit): PlaylistApiService =
        retrofit.create(PlaylistApiService::class.java)

    @Provides
    @Singleton
    fun provideSocialApiService(retrofit: Retrofit): SocialApiService =
        retrofit.create(SocialApiService::class.java)

    @Provides
    @Singleton
    fun provideChatApiService(retrofit: Retrofit): ChatApiService =
        retrofit.create(ChatApiService::class.java)

    @Provides
    @Singleton
    fun provideSearchApiService(retrofit: Retrofit): SearchApiService =
        retrofit.create(SearchApiService::class.java)

    @Provides
    @Singleton
    fun provideImportApiService(retrofit: Retrofit): ImportApiService =
        retrofit.create(ImportApiService::class.java)
}
