package com.fox.music.core.data.lyric

import com.fox.music.core.domain.lyric.OpenLyricHttp
import com.fox.music.core.domain.lyric.OpenLyricResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OkHttpOpenLyricHttp @Inject constructor() : OpenLyricHttp {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun getText(
        url: String,
        headers: Map<String, String>,
    ): OpenLyricResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            OpenLyricResponse(
                code = response.code,
                body = response.body.string(),
            )
        }
    }
}
