package com.fox.music.core.network.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class PagedResponseDeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `parses music list pagination with camelCase fields`() {
        val body = """
            {
              "code": 200,
              "data": {
                "list": [],
                "total": 1488,
                "current": 1,
                "pageSize": 20,
                "totalPages": 75,
                "recommendSeed": "214da2ce-8446-4471-b18c-aa695f9f42a9"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<PagedResponse<MusicDto>>>(body)
        val data = response.data!!

        assertEquals(20, data.pageSize)
        assertEquals(75, data.totalPages)
        assertEquals("214da2ce-8446-4471-b18c-aa695f9f42a9", data.recommendSeed)
    }

    @Test
    fun `parses pagination with snake_case fields`() {
        val body = """
            {
              "code": 200,
              "data": {
                "list": [],
                "total": 1,
                "current": 1,
                "page_size": 50,
                "total_pages": 1
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<PagedResponse<MusicDto>>>(body)
        val data = response.data!!

        assertEquals(50, data.pageSize)
        assertEquals(1, data.totalPages)
    }
}
