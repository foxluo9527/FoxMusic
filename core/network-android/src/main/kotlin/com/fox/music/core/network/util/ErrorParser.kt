package com.fox.music.core.network.util

import com.fox.music.core.network.model.ApiResponse
import com.google.gson.Gson
import kotlinx.serialization.json.JsonElement
import com.google.gson.reflect.TypeToken
import retrofit2.HttpException

object ErrorParser {
    // 泛型扩展函数，更简洁
    inline fun <reified T> Gson.fromJson(json: String): T {
        val type = object : TypeToken<T>() {}.type
        return fromJson(json, type)
    }

    fun parseError(error: Throwable): String {
        if (error is HttpException) {
            error.response()?.errorBody()?.string()?.let { jsonString ->
                val response: ApiResponse<JsonElement> = Gson().fromJson(jsonString)
                return response.message
            }
        }
        return error.message ?: ""
    }
}