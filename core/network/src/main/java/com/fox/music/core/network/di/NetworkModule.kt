package com.fox.music.core.network.di

import com.fox.music.core.common.constants.AppConstants
import com.fox.music.core.network.BuildConfig
import com.fox.music.core.network.api.AlbumApiService
import com.fox.music.core.network.api.AppUpdateApiService
import com.fox.music.core.network.api.ArtistApiService
import com.fox.music.core.network.api.AuthApiService
import com.fox.music.core.network.api.ChatApiService
import com.fox.music.core.network.api.FavoriteApiService
import com.fox.music.core.network.api.ImportApiService
import com.fox.music.core.network.api.MusicApiService
import com.fox.music.core.network.api.PlaylistApiService
import com.fox.music.core.network.api.ReportApiService
import com.fox.music.core.network.api.SearchApiService
import com.fox.music.core.network.api.SocialApiService
import com.fox.music.core.network.api.UploadApiService
import com.fox.music.core.network.interceptor.AuthInterceptor
import com.fox.music.core.network.interceptor.SafeHttpLoggingInterceptor
import com.fox.music.core.network.interceptor.TokenAuthenticator
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.fox.music.core.network.di.WebSocketClient

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
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): SafeHttpLoggingInterceptor = SafeHttpLoggingInterceptor()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: SafeHttpLoggingInterceptor,
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

    /** WebSocket 专用：无 read/write 超时，避免后台长连接被 OkHttp 主动断开 */
    @Provides
    @Singleton
    @WebSocketClient
    fun provideWebSocketOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(AppConstants.Network.CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
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

    @Provides
    @Singleton
    fun provideFavoriteApiService(retrofit: Retrofit): FavoriteApiService =
        retrofit.create(FavoriteApiService::class.java)

    @Provides
    @Singleton
    fun provideUploadApiService(retrofit: Retrofit): UploadApiService =
        retrofit.create(UploadApiService::class.java)

    @Provides
    @Singleton
    fun provideReportApiService(retrofit: Retrofit): ReportApiService =
        retrofit.create(ReportApiService::class.java)

    @Provides
    @Singleton
    fun provideAppUpdateApiService(retrofit: Retrofit): AppUpdateApiService =
        retrofit.create(AppUpdateApiService::class.java)
}
