package com.fox.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fox.music.core.database.converter.Converters
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.database.dao.DownloadDao
import com.fox.music.core.database.dao.ImportJobDao
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.database.dao.MusicDao
import com.fox.music.core.database.dao.PlaybackQueueDao
import com.fox.music.core.database.dao.PlaylistDao
import com.fox.music.core.database.dao.PostPublishJobDao
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.dao.UserDao
import com.fox.music.core.database.entity.ConversationEntity
import com.fox.music.core.database.entity.DownloadEntity
import com.fox.music.core.database.entity.ImportPlaylistJobEntity
import com.fox.music.core.database.entity.ImportTrackJobEntity
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.database.entity.MusicEntity
import com.fox.music.core.database.entity.PlaybackQueueEntity
import com.fox.music.core.database.entity.PlaylistEntity
import com.fox.music.core.database.entity.PostPublishJobEntity
import com.fox.music.core.database.entity.PostPublishMediaEntity
import com.fox.music.core.database.entity.SearchHistoryEntity
import com.fox.music.core.database.entity.UserEntity

@Database(
    entities = [
        MusicEntity::class,
        PlaylistEntity::class,
        UserEntity::class,
        MessageEntity::class,
        ConversationEntity::class,
        SearchHistoryEntity::class,
        DownloadEntity::class,
        ImportPlaylistJobEntity::class,
        ImportTrackJobEntity::class,
        PlaybackQueueEntity::class,
        PostPublishJobEntity::class,
        PostPublishMediaEntity::class,
    ],
    version = 15,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoxMusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun downloadDao(): DownloadDao
    abstract fun importJobDao(): ImportJobDao
    abstract fun playbackQueueDao(): PlaybackQueueDao

    abstract fun postPublishJobDao(): PostPublishJobDao
}
