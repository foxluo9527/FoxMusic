package com.fox.music.core.network.websocket

import com.fox.music.core.network.model.MessageDto
import com.fox.music.core.network.model.NotificationDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * WebSocket 报文解析：Android 与 iOS 共用同一份实现。
 *
 * 原先这段逻辑写在 :core:network-android 的 WebSocketManagerImpl 里（OkHttp 回调中），
 * iOS 若另写一套必然与 Android 跑偏，因此下沉到 commonMain，两端只负责传输层。
 *
 * 服务端补发格式有两种，都必须支持：
 *   1. 裸实体：{"type":"comment", "title":...} / {"sender_id":..,"receiver_id":..}
 *   2. 信封：{"type":"notification","data":{...}}
 */
object WebSocketMessageParser {

    private val NOTIFICATION_TYPES = setOf(
        "comment",
        "like",
        "follow",
        "mention",
        "chat",
        "system",
        "friend_request",
        "music",
        "message",
    )

    fun parse(json: Json, text: String): WebSocketMessage? {
        return try {
            when {
                text == "pong" -> WebSocketMessage.Pong
                else -> parseJsonMessage(json, text)
            }
        } catch (e: Exception) {
            WebSocketMessage.Error(e.message ?: "Parse error")
        }
    }

    private fun parseJsonMessage(json: Json, text: String): WebSocketMessage? {
        val root = json.parseToJsonElement(text).jsonObject
        val typeField = root["type"]?.jsonPrimitive?.content?.lowercase()

        return when {
            typeField == "error" -> {
                val msg = root["message"]?.jsonPrimitive?.content ?: "Server error"
                WebSocketMessage.Error(msg)
            }
            typeField == "notification" -> parseNotificationEnvelope(json, text, root)
            typeField in NOTIFICATION_TYPES -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
            }
            else -> parseAsChatMessage(json, text, root)
        }
    }

    private fun parseNotificationEnvelope(
        json: Json,
        text: String,
        root: JsonObject,
    ): WebSocketMessage? {
        val dataElement = root["data"] ?: return decodeRootNotification(json, text)
        val payload = dataElement.toString()
        val inner = dataElement.jsonObject
        val innerType = inner["type"]?.jsonPrimitive?.content?.lowercase()
        return when {
            inner.containsKey("sender_id") && inner.containsKey("receiver_id") -> {
                WebSocketMessage.ChatMessage(json.decodeFromString<MessageDto>(payload))
            }
            innerType in NOTIFICATION_TYPES || inner.containsKey("title") -> {
                WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(payload))
            }
            else -> decodeRootNotification(json, text)
        }
    }

    private fun decodeRootNotification(json: Json, text: String): WebSocketMessage? {
        return try {
            WebSocketMessage.Notification(json.decodeFromString<NotificationDto>(text))
        } catch (_: Exception) {
            null
        }
    }

    private fun parseAsChatMessage(json: Json, text: String, root: JsonObject): WebSocketMessage? {
        if (!root.containsKey("sender_id") || !root.containsKey("receiver_id")) {
            return null
        }
        val message = json.decodeFromString<MessageDto>(text)
        return WebSocketMessage.ChatMessage(message)
    }
}
