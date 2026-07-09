package com.fox.music.core.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fox.music.core.model.music.RecommendedMusicCache
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

private val Context.recommendedMusicCacheStore: DataStore<Preferences> by preferencesDataStore(
    name = "recommended_music_cache",
)

@Singleton
class RecommendedMusicCacheStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun load(): RecommendedMusicCache? {
        val raw = context.recommendedMusicCacheStore.data.first()[CACHE_JSON] ?: return null
        return runCatching {
            json.decodeFromString(RecommendedMusicCache.serializer(), raw)
        }.getOrNull()
    }

    suspend fun save(cache: RecommendedMusicCache) {
        context.recommendedMusicCacheStore.edit { prefs ->
            prefs[CACHE_JSON] = json.encodeToString(RecommendedMusicCache.serializer(), cache)
        }
    }

    suspend fun clear() {
        context.recommendedMusicCacheStore.edit { it.remove(CACHE_JSON) }
    }

    private companion object {
        val CACHE_JSON = stringPreferencesKey("cache_json")
    }
}
