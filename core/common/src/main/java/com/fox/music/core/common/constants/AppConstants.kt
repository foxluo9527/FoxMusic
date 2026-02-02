package com.fox.music.core.common.constants

object AppConstants {
    const val PAGE_SIZE = 20
    const val INITIAL_PAGE = 1

    object Network {
        const val CONNECT_TIMEOUT = 30L
        const val READ_TIMEOUT = 30L
        const val WRITE_TIMEOUT = 30L
    }

    object Database {
        const val DATABASE_NAME = "fox_music_db"
        const val DATABASE_VERSION = 1
    }

    object Preferences {
        const val PREFERENCES_NAME = "fox_music_preferences"
    }

    object Player {
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "fox_music_playback_channel"
        const val CHANNEL_NAME = "Music Playback"
    }
}
