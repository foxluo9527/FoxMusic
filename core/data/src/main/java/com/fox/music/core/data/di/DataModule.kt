package com.fox.music.core.data.di

import com.fox.music.core.data.repository.AlbumRepositoryImpl
import com.fox.music.core.data.repository.ArtistRepositoryImpl
import com.fox.music.core.data.repository.AuthRepositoryImpl
import com.fox.music.core.data.repository.ChatRepositoryImpl
import com.fox.music.core.data.repository.MusicRepositoryImpl
import com.fox.music.core.data.repository.PlaylistRepositoryImpl
import com.fox.music.core.data.repository.SearchRepositoryImpl
import com.fox.music.core.data.repository.SocialRepositoryImpl
import com.fox.music.core.data.repository.UserPreferencesRepositoryImpl
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.ArtistRepository
import com.fox.music.core.domain.repository.AuthRepository
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.MusicRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository

    @Binds
    @Singleton
    abstract fun bindAlbumRepository(impl: AlbumRepositoryImpl): AlbumRepository

    @Binds
    @Singleton
    abstract fun bindArtistRepository(impl: ArtistRepositoryImpl): ArtistRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(impl: SearchRepositoryImpl): SearchRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        impl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: ChatRepositoryImpl): ChatRepository

    @Binds
    @Singleton
    abstract fun bindSocialRepository(impl: SocialRepositoryImpl): SocialRepository
}
