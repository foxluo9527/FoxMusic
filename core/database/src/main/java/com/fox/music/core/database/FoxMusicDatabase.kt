package com.fox.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fox.music.core.database.converter.Converters
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.database.dao.MusicDao
import com.fox.music.core.database.dao.PlaylistDao
import com.fox.music.core.database.dao.SearchHistoryDao
import com.fox.music.core.database.dao.UserDao
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.database.entity.MusicEntity
import com.fox.music.core.database.entity.PlaylistEntity
import com.fox.music.core.database.entity.SearchHistoryEntity
import com.fox.music.core.database.entity.UserEntity

@Database(
    entities = [
        MusicEntity::class,
        PlaylistEntity::class,
        UserEntity::class,
        MessageEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FoxMusicDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun searchHistoryDao(): SearchHistoryDao
}
