package com.fox.music.core.network.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationDtoDeserializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Test
    fun `parses system notification from API shape`() {
        val body = """
            {
              "code": 200,
              "message": "操作成功",
              "data": {
                "list": [
                  {
                    "id": 254,
                    "user_id": 1,
                    "type": "system",
                    "title": "这是测试公告",
                    "content": "如果你看到这里，那就没问题了",
                    "is_read": 0,
                    "created_at": "2025-01-01T00:00:00Z"
                  }
                ],
                "total": 1,
                "current": 1,
                "page_size": 50,
                "total_pages": 1
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<PagedResponse<NotificationDto>>>(body)
        assertTrue(response.isSuccess)
        val notification = response.data?.list?.first()
        assertEquals(1, response.data?.list?.size)
        assertEquals("这是测试公告", notification?.title)
        assertFalse(notification?.isRead == true)
    }

    @Test
    fun `parses notification with partial sender`() {
        val body = """
            {
              "code": 200,
              "data": {
                "list": [
                  {
                    "id": 1,
                    "type": "comment",
                    "title": "评论",
                    "content": "内容",
                    "sender": { "id": 2, "nickname": "用户A" }
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<PagedResponse<NotificationDto>>>(body)
        assertEquals(1, response.data?.list?.size)
        assertEquals("用户A", response.data?.list?.first()?.sender?.nickname)
    }

    @Test
    fun `maps chat message notification without treating as system`() {
        val body = """
            {
              "code": 200,
              "data": {
                "list": [
                  {
                    "id": 99,
                    "type": "message",
                    "title": "新消息",
                    "content": "你好",
                    "target_type": "user",
                    "is_read": 0
                  }
                ]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<ApiResponse<PagedResponse<NotificationDto>>>(body)
        assertEquals("message", response.data?.list?.first()?.type)
    }
}
