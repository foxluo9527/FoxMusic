package com.fox.music.core.network.websocket

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 直接测 commonMain 的 [WebSocketMessageParser] —— Android 与 iOS 走的就是这一份实现，
 * 因此这里的用例同时约束两端行为。
 */
class WebSocketMessageParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private fun parse(text: String): WebSocketMessage? = WebSocketMessageParser.parse(json, text)

    @Test
    fun `parses comment notification from websocket doc`() {
        val body = """
            {
              "id": 123,
              "user_id": 456,
              "type": "comment",
              "title": "新评论提醒",
              "content": "用户评论了你的帖子",
              "target_type": "post",
              "target_id": 789,
              "is_read": 0,
              "is_pushed": 1,
              "created_at": "2024-01-15T10:30:00Z"
            }
        """.trimIndent()

        val result = parse(body)
        assertTrue(result is WebSocketMessage.Notification)
        assertEquals("comment", (result as WebSocketMessage.Notification).notification.type)
    }

    @Test
    fun `parses chat type as notification not chat message dto`() {
        val body = """
            {
              "id": 99,
              "user_id": 1,
              "type": "chat",
              "title": "聊天消息提醒",
              "content": "你好",
              "target_type": "user",
              "target_id": 42,
              "is_read": 0
            }
        """.trimIndent()

        val result = parse(body)
        assertTrue(result is WebSocketMessage.Notification)
        assertEquals("chat", (result as WebSocketMessage.Notification).notification.type)
    }

    @Test
    fun `parses wrapped notification envelope on reconnect replay`() {
        val body = """
            {
              "type": "notification",
              "data": {
                "id": 292,
                "user_id": 3,
                "type": "chat",
                "title": "聊天消息提醒",
                "content": "乖乖: test",
                "target_type": "user",
                "target_id": 224,
                "is_read": 0
              }
            }
        """.trimIndent()

        val result = parse(body)
        assertTrue(result is WebSocketMessage.Notification)
        val notification = (result as WebSocketMessage.Notification).notification
        assertEquals(292L, notification.id)
        assertEquals("chat", notification.type)
    }

    @Test
    fun `parses raw chat message with sender and receiver ids`() {
        val body = """
            {
              "id": 1,
              "sender_id": 10,
              "receiver_id": 20,
              "content": "hello",
              "type": "text"
            }
        """.trimIndent()

        val result = parse(body)
        assertTrue(result is WebSocketMessage.ChatMessage)
        assertEquals(10L, (result as WebSocketMessage.ChatMessage).message.senderId)
    }
}
