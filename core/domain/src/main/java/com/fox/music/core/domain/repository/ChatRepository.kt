package com.fox.music.core.domain.repository

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.SearchResultItem
import com.fox.music.core.model.PagedData
import kotlinx.coroutines.flow.Flow

interface ChatRepository {

    fun observeMessages(peerUserId: Long): Flow<List<Message>>

    fun observeConversations(): Flow<List<ChatConversation>>

    suspend fun syncConversations(): Result<Unit>

    /** @param peerUserId 对方用户 ID；传 0 同步全部未读（对话列表刷新） */
    suspend fun syncUnreadMessages(peerUserId: Long = 0): Result<Unit>

    @Deprecated("History API is not available; use syncUnreadMessages on conversation list refresh")
    suspend fun syncChatHistory(userId: Long, page: Int = 1, limit: Int = 50): Result<Unit>

    suspend fun sendTextMessage(
        receiverId: Long,
        content: String,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): Result<String>

    suspend fun sendMediaMessage(
        receiverId: Long,
        type: String,
        content: String = "",
        mediaUri: Uri? = null,
        fileName: String? = null,
        audioDurationMs: Long? = null,
        imageSendOriginal: Boolean = false,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): Result<String>

    suspend fun retryMessage(localId: String): Result<Unit>

    suspend fun recallMessage(messageId: Long): Result<Unit>

    /** 撤回消息并在本地更新内容 */
    suspend fun recallAndUpdateLocal(messageId: Long, localId: String): Result<Unit>

    /** 删除本地消息记录 */
    suspend fun deleteMessage(localId: String): Result<Unit>

    /** 取消发送中的消息，状态变更为发送失败 */
    suspend fun cancelSending(localId: String): Result<Unit>

    suspend fun markAsRead(targetId: Long): Result<Unit>

    suspend fun deleteConversation(targetId: Long): Result<Unit>

    suspend fun pinConversation(targetId: Long, isPinned: Boolean): Result<Unit>

    suspend fun ingestIncomingMessage(message: Message): Result<Unit>

    /** 聊天类推送落地：本地更新；无法解析对方 ID 时回退拉取未读消息 */
    suspend fun landMessageNotification(notification: Notification): Result<Unit>

    suspend fun hasIngestedMessage(serverId: Long): Boolean

    fun searchMessages(query: String): Flow<List<SearchResultItem>>

    fun searchMessagesByUser(peerUserId: Long, query: String): Flow<List<Message>>

    suspend fun countMessagesByUser(peerUserId: Long, query: String): Int

    @Deprecated("Use syncUnreadMessages", ReplaceWith("syncUnreadMessages()"))
    suspend fun getUnreadMessages(): Result<List<Message>>

    @Deprecated("Use observeMessages + syncUnreadMessages", ReplaceWith("observeMessages(userId)"))
    suspend fun getConversations(): Result<List<ChatConversation>>

    @Deprecated("Use observeMessages", ReplaceWith("observeMessages(userId)"))
    suspend fun getChatHistory(userId: Long, page: Int = 1, limit: Int = 20): Result<PagedData<Message>>

    @Deprecated("Use sendTextMessage", ReplaceWith("sendTextMessage(receiverId, content)"))
    suspend fun sendMessage(receiverId: Long, content: String, type: String = "text"): Result<Message>
}
