package com.fox.music.core.database.di

import android.content.Context
import androidx.room.Room
import com.fox.music.core.database.FoxMusicDatabase
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.database.dao.MusicDao
import com.fox.music.core.database.dao.PlaylistDao
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFoxMusicDatabase(
        @ApplicationContext context: Context,
    ): FoxMusicDatabase {
        return Room.databaseBuilder(
            context,
            FoxMusicDatabase::class.java,
            "fox_music.db",
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideMusicDao(database: FoxMusicDatabase): MusicDao = database.musicDao()

    @Provides
    fun providePlaylistDao(database: FoxMusicDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun provideUserDao(database: FoxMusicDatabase): UserDao = database.userDao()

    @Provides
    fun provideMessageDao(database: FoxMusicDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideSearchHistoryDao(database: FoxMusicDatabase): SearchHistoryDao = database.searchHistoryDao()
}
