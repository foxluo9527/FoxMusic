package com.fox.music.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fox.music.core.database.FoxMusicDatabase
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.database.dao.DownloadDao
import com.fox.music.core.database.dao.ImportJobDao
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

    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `downloads` (
                  `musicId` INTEGER NOT NULL PRIMARY KEY,
                  `title` TEXT NOT NULL,
                  `artistNames` TEXT NOT NULL,
                  `coverUrl` TEXT,
                  `sourceUrl` TEXT NOT NULL,
                  `filePath` TEXT,
                  `status` TEXT NOT NULL,
                  `progress` INTEGER NOT NULL,
                  `totalBytes` INTEGER NOT NULL,
                  `downloadedBytes` INTEGER NOT NULL,
                  `createdAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `messages`")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages` (
                  `localId` TEXT NOT NULL PRIMARY KEY,
                  `serverId` INTEGER,
                  `conversationId` INTEGER NOT NULL,
                  `senderId` INTEGER NOT NULL,
                  `receiverId` INTEGER NOT NULL,
                  `content` TEXT NOT NULL,
                  `type` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `localMediaUri` TEXT,
                  `localMediaFileName` TEXT,
                  `audioDurationMs` INTEGER,
                  `errorMessage` TEXT,
                  `isRecalled` INTEGER NOT NULL,
                  `isRead` INTEGER NOT NULL,
                  `sentAt` TEXT,
                  `cachedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_conversationId_cachedAt` " +
                    "ON `messages` (`conversationId`, `cachedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_messages_serverId` ON `messages` (`serverId`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations` (
                  `peerUserId` INTEGER NOT NULL PRIMARY KEY,
                  `peerNickname` TEXT,
                  `peerAvatar` TEXT,
                  `peerMark` TEXT,
                  `lastMessageLocalId` TEXT,
                  `lastMessagePreview` TEXT NOT NULL,
                  `lastMessageStatus` TEXT,
                  `lastMessageAt` INTEGER NOT NULL,
                  `unreadCount` INTEGER NOT NULL,
                  `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `messages` ADD COLUMN `imageSendOriginal` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `taskUuid` TEXT")
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `remoteMediaUrl` TEXT")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `fileType` TEXT")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `uploadedAt` TEXT")
            db.execSQL(
                "ALTER TABLE `conversations` ADD COLUMN `isPinned` INTEGER NOT NULL DEFAULT 0",
            )
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) = Unit
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `shareType` TEXT")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `shareId` INTEGER")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `shareDataJson` TEXT")
        }
    }

    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `downloads` ADD COLUMN `lyrics` TEXT")
            db.execSQL("ALTER TABLE `downloads` ADD COLUMN `lyricsTrans` TEXT")
        }
    }

    private val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `import_playlist_jobs` (
                  `jobId` TEXT NOT NULL PRIMARY KEY,
                  `playlistId` INTEGER NOT NULL,
                  `playlistTitle` TEXT NOT NULL,
                  `status` TEXT NOT NULL,
                  `total` INTEGER NOT NULL,
                  `done` INTEGER NOT NULL,
                  `failed` INTEGER NOT NULL,
                  `createdAt` INTEGER NOT NULL,
                  `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_import_playlist_jobs_status` ON `import_playlist_jobs` (`status`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_import_playlist_jobs_createdAt` ON `import_playlist_jobs` (`createdAt`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `import_track_jobs` (
                  `trackJobId` TEXT NOT NULL PRIMARY KEY,
                  `jobId` TEXT NOT NULL,
                  `playlistId` INTEGER NOT NULL,
                  `trackIndex` INTEGER NOT NULL,
                  `title` TEXT NOT NULL,
                  `artistNames` TEXT NOT NULL,
                  `durationMs` INTEGER NOT NULL,
                  `coverUrl` TEXT,
                  `externalId` TEXT,
                  `status` TEXT NOT NULL,
                  `detailUrl` TEXT,
                  `matchScore` INTEGER NOT NULL,
                  `titleRaw` TEXT,
                  `errorMessage` TEXT,
                  `musicId` INTEGER,
                  `createdAt` INTEGER NOT NULL,
                  `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_import_track_jobs_jobId_trackIndex` ON `import_track_jobs` (`jobId`, `trackIndex`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_import_track_jobs_status` ON `import_track_jobs` (`status`)",
            )
        }
    }

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `import_playlist_jobs` ADD COLUMN `platform` TEXT")
            db.execSQL("ALTER TABLE `import_track_jobs` ADD COLUMN `albumName` TEXT")
            db.execSQL("ALTER TABLE `import_track_jobs` ADD COLUMN `albumCover` TEXT")
            db.execSQL("ALTER TABLE `import_track_jobs` ADD COLUMN `platform` TEXT")
            db.execSQL("ALTER TABLE `import_track_jobs` ADD COLUMN `rejectedLinksJson` TEXT")
        }
    }

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
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
            )
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
    fun provideConversationDao(database: FoxMusicDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideSearchHistoryDao(database: FoxMusicDatabase): SearchHistoryDao = database.searchHistoryDao()

    @Provides
    fun provideDownloadDao(database: FoxMusicDatabase): DownloadDao = database.downloadDao()

    @Provides
    fun provideImportJobDao(database: FoxMusicDatabase): ImportJobDao = database.importJobDao()
}
