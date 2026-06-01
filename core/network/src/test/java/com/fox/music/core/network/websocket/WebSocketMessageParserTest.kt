package com.fox.music.core.network.websocket

import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSocketMessageParserTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    private val notificationTypes = setOf(
        "comment", "like", "follow", "mention", "chat", "system",
        "friend_request", "music", "message",
    )

    private fun parse(text: String): WebSocketMessage? {
        if (text == "pong") return WebSocketMessage.Pong
        val root = json.parseToJsonElement(text).jsonObject
        val typeField = root["type"]?.jsonPrimitive?.content?.lowercase()
        return when {
            typeField == "notification" -> parseNotificationEnvelope(text, root)
            typeField in notificationTypes -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
            }
            root.containsKey("sender_id") && root.containsKey("receiver_id") -> {
                WebSocketMessage.ChatMessage(json.decodeFromString<MessageDto>(text))
            }
            else -> null
        }
    }

    private fun parseNotificationEnvelope(text: String, root: kotlinx.serialization.json.JsonObject): WebSocketMessage? {
        val dataElement = root["data"] ?: return decodeRootNotification(text)
        val payload = dataElement.toString()
        val inner = dataElement.jsonObject
        val innerType = inner["type"]?.jsonPrimitive?.content?.lowercase()
        return when {
            inner.containsKey("sender_id") && inner.containsKey("receiver_id") -> {
                WebSocketMessage.ChatMessage(json.decodeFromString<MessageDto>(payload))
            }
            innerType in notificationTypes || inner.containsKey("title") -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(payload))
            }
            else -> decodeRootNotification(text)
        }
    }

    private fun decodeRootNotification(text: String): WebSocketMessage? = try {
        WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
    } catch (_: Exception) {
        null
    }

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
