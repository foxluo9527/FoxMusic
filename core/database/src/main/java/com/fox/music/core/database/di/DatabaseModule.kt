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
import com.fox.music.core.database.dao.PlaybackQueueDao
import com.fox.music.core.database.dao.PlaylistDao
import com.fox.music.core.database.dao.PostPublishJobDao
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

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playback_queue` (
                  `queueIndex` INTEGER NOT NULL PRIMARY KEY,
                  `musicId` INTEGER NOT NULL,
                  `musicJson` TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `localVideoUri` TEXT")
            db.execSQL("ALTER TABLE `messages` ADD COLUMN `remoteVideoUrl` TEXT")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Rebuild messages so ownerUserId sits in the expected column order
            // and no SQLite DEFAULT remains on the column (Room rejects DEFAULT != undefined).
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `messages_new` (
                    `localId` TEXT NOT NULL,
                    `ownerUserId` INTEGER NOT NULL,
                    `serverId` INTEGER,
                    `conversationId` INTEGER NOT NULL,
                    `senderId` INTEGER NOT NULL,
                    `receiverId` INTEGER NOT NULL,
                    `content` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `localMediaUri` TEXT,
                    `localMediaFileName` TEXT,
                    `remoteMediaUrl` TEXT,
                    `fileType` TEXT,
                    `uploadedAt` INTEGER,
                    `taskUuid` TEXT,
                    `imageSendOriginal` INTEGER NOT NULL,
                    `audioDurationMs` INTEGER,
                    `errorMessage` TEXT,
                    `isRecalled` INTEGER NOT NULL,
                    `isRead` INTEGER NOT NULL,
                    `sentAt` TEXT,
                    `cachedAt` INTEGER NOT NULL,
                    `shareType` TEXT,
                    `shareId` INTEGER,
                    `shareDataJson` TEXT,
                    `localVideoUri` TEXT,
                    `remoteVideoUrl` TEXT,
                    PRIMARY KEY(`localId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `messages_new` (
                    `localId`, `ownerUserId`, `serverId`, `conversationId`, `senderId`, `receiverId`,
                    `content`, `type`, `status`, `localMediaUri`, `localMediaFileName`, `remoteMediaUrl`,
                    `fileType`, `uploadedAt`, `taskUuid`, `imageSendOriginal`, `audioDurationMs`,
                    `errorMessage`, `isRecalled`, `isRead`, `sentAt`, `cachedAt`, `shareType`, `shareId`,
                    `shareDataJson`, `localVideoUri`, `remoteVideoUrl`
                )
                SELECT
                    `localId`,
                    CASE
                        WHEN `conversationId` = `senderId` THEN `receiverId`
                        ELSE `senderId`
                    END,
                    `serverId`, `conversationId`, `senderId`, `receiverId`,
                    `content`, `type`, `status`, `localMediaUri`, `localMediaFileName`, `remoteMediaUrl`,
                    `fileType`, `uploadedAt`, `taskUuid`, `imageSendOriginal`, `audioDurationMs`,
                    `errorMessage`, `isRecalled`, `isRead`, `sentAt`, `cachedAt`, `shareType`, `shareId`,
                    `shareDataJson`, `localVideoUri`, `remoteVideoUrl`
                FROM `messages`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `messages`")
            db.execSQL("ALTER TABLE `messages_new` RENAME TO `messages`")
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_messages_ownerUserId_conversationId_cachedAt`
                ON `messages` (`ownerUserId`, `conversationId`, `cachedAt`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_messages_serverId_ownerUserId`
                ON `messages` (`serverId`, `ownerUserId`)
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `conversations_new` (
                    `ownerUserId` INTEGER NOT NULL,
                    `peerUserId` INTEGER NOT NULL,
                    `peerNickname` TEXT,
                    `peerAvatar` TEXT,
                    `peerMark` TEXT,
                    `lastMessageLocalId` TEXT,
                    `lastMessagePreview` TEXT NOT NULL,
                    `lastMessageStatus` TEXT,
                    `lastMessageAt` INTEGER NOT NULL,
                    `unreadCount` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `isPinned` INTEGER NOT NULL,
                    PRIMARY KEY(`ownerUserId`, `peerUserId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR REPLACE INTO `conversations_new` (
                    `ownerUserId`, `peerUserId`, `peerNickname`, `peerAvatar`, `peerMark`,
                    `lastMessageLocalId`, `lastMessagePreview`, `lastMessageStatus`,
                    `lastMessageAt`, `unreadCount`, `updatedAt`, `isPinned`
                )
                SELECT
                    m.`ownerUserId`,
                    m.`conversationId` AS `peerUserId`,
                    c.`peerNickname`,
                    c.`peerAvatar`,
                    c.`peerMark`,
                    (
                        SELECT m2.`localId` FROM `messages` m2
                        WHERE m2.`ownerUserId` = m.`ownerUserId`
                          AND m2.`conversationId` = m.`conversationId`
                        ORDER BY m2.`cachedAt` DESC LIMIT 1
                    ) AS `lastMessageLocalId`,
                    COALESCE(c.`lastMessagePreview`, '') AS `lastMessagePreview`,
                    c.`lastMessageStatus`,
                    COALESCE(
                        (
                            SELECT m2.`cachedAt` FROM `messages` m2
                            WHERE m2.`ownerUserId` = m.`ownerUserId`
                              AND m2.`conversationId` = m.`conversationId`
                            ORDER BY m2.`cachedAt` DESC LIMIT 1
                        ),
                        c.`lastMessageAt`,
                        0
                    ) AS `lastMessageAt`,
                    COALESCE(c.`unreadCount`, 0) AS `unreadCount`,
                    COALESCE(c.`updatedAt`, 0) AS `updatedAt`,
                    COALESCE(c.`isPinned`, 0) AS `isPinned`
                FROM (
                    SELECT DISTINCT `ownerUserId`, `conversationId` FROM `messages`
                ) m
                LEFT JOIN `conversations` c ON c.`peerUserId` = m.`conversationId`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `conversations`")
            db.execSQL("ALTER TABLE `conversations_new` RENAME TO `conversations`")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `post_publish_jobs` (
                    `jobId` TEXT NOT NULL,
                    `editingPostId` INTEGER,
                    `title` TEXT,
                    `content` TEXT NOT NULL,
                    `tagNames` TEXT NOT NULL,
                    `tagIds` TEXT NOT NULL,
                    `musicId` INTEGER,
                    `status` TEXT NOT NULL,
                    `errorMessage` TEXT,
                    `taskUuid` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`jobId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `post_publish_media` (
                    `mediaId` TEXT NOT NULL,
                    `jobId` TEXT NOT NULL,
                    `sortIndex` INTEGER NOT NULL,
                    `mediaType` TEXT NOT NULL,
                    `localUri` TEXT,
                    `localVideoUri` TEXT,
                    `remoteUrl` TEXT,
                    `remoteVideoUrl` TEXT,
                    `uploadStatus` TEXT NOT NULL,
                    `uploadedUrl` TEXT,
                    `uploadedVideoUrl` TEXT,
                    `errorMessage` TEXT,
                    PRIMARY KEY(`mediaId`),
                    FOREIGN KEY(`jobId`) REFERENCES `post_publish_jobs`(`jobId`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE INDEX IF NOT EXISTS `index_post_publish_media_jobId`
                ON `post_publish_media` (`jobId`)
                """.trimIndent(),
            )
        }
    }

    /** 16：导入曲目表增加原曲歌词缓存（详情页试听对照 + 导入复用） */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `import_track_jobs` ADD COLUMN `lyrics` TEXT")
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
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
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

    @Provides
    fun providePlaybackQueueDao(database: FoxMusicDatabase): PlaybackQueueDao = database.playbackQueueDao()

    @Provides
    fun providePostPublishJobDao(database: FoxMusicDatabase): PostPublishJobDao = database.postPublishJobDao()
}
