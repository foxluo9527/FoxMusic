package com.fox.music.core.database.converter

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.let { json.decodeFromString<List<String>>(it) }
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String? {
        return value?.let { json.encodeToString(it) }
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long>? {
        return value?.let { json.decodeFromString<List<Long>>(it) }
    }
}
