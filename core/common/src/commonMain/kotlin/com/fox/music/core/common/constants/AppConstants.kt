package com.fox.music.core.common.constants

import com.fox.music.core.common.FoxBuildConfig

object AppConstants {
    const val PAGE_SIZE = 20
    const val INITIAL_PAGE = 1

    object Network {
        const val CONNECT_TIMEOUT = 60L
        const val READ_TIMEOUT = 60L
        const val WRITE_TIMEOUT = 60L

        /**
         * 媒体资源基地址。原先取自 AGP 的 BuildConfig（Android 专有产物，
         * KMP 的 commonMain 不可用），现改由 Gradle 任务生成为 FoxBuildConfig。
         */
        val MEDIA_BASE_URL: String = FoxBuildConfig.MEDIA_BASE_URL
    }

    object Database {
        const val DATABASE_NAME = "fox_music.db"
        const val DATABASE_VERSION = 9
    }

    object Preferences {
        const val PREFERENCES_NAME = "fox_preferences"
    }

    object Player {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "fox_music_playback_channel"
        const val CHANNEL_NAME = "Music Playback"
    }
}
