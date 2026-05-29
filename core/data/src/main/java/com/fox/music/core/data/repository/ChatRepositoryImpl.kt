package com.fox.music.core.data.repository

import android.net.Uri
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.data.mapper.toChatConversation
import com.fox.music.core.data.mapper.toConversationEntity
import com.fox.music.core.data.mapper.toDomainMessage
import com.fox.music.core.data.mapper.toMessage
import com.fox.music.core.data.mapper.toMessageEntity
import com.fox.music.core.data.mapper.toPagedData
import com.fox.music.core.data.sender.ChatMessageSender
import com.fox.music.core.data.util.ChatMediaStorage
import com.fox.music.core.database.dao.ConversationDao
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.database.entity.ConversationEntity
import com.fox.music.core.database.entity.MessageEntity
import com.fox.music.core.domain.repository.ChatRepository
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.ChatConversation
import com.fox.music.core.model.chat.Message
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.ChatApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatApi: ChatApiService,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatMessageSender: ChatMessageSender,
    private val chatMediaStorage: ChatMediaStorage,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ChatRepository {

    override fun observeMessages(peerUserId: Long): Flow<List<Message>> =
        messageDao.observeMessages(peerUserId).map { entities ->
            entities.map { it.toDomainMessage() }
        }

    override fun observeConversations(): Flow<List<ChatConversation>> =
        conversationDao.observeConversations().map { entities ->
            entities.map { it.toChatConversation() }
        }

    override suspend fun syncConversations(): Result<Unit> = suspendRunCatching {
        val response = chatApi.getConversations()
        val data = response.data
        if (!response.isSuccess || data == null) throw Exception(response.message)
        data.forEach { dto ->
            val remote = dto.toConversationEntity()
            val local = conversationDao.getConversation(remote.peerUserId)
            if (local == null || remote.lastMessageAt >= local.lastMessageAt) {
                conversationDao.upsertConversation(
                    remote.copy(
                        lastMessageStatus = local?.lastMessageStatus
                            ?.takeIf { it == "failed" || it == "sending" }
                            ?: remote.lastMessageStatus,
                        lastMessagePreview = local?.lastMessagePreview
                            ?.takeIf { local.lastMessageStatus == "failed" || local.lastMessageStatus == "sending" }
                            ?: remote.lastMessagePreview,
                        lastMessageAt = maxOf(remote.lastMessageAt, local?.lastMessageAt ?: 0L),
                    ),
                )
            } else {
                conversationDao.upsertConversation(local)
            }
        }
    }

    override suspend fun syncChatHistory(userId: Long, page: Int, limit: Int): Result<Unit> =
        suspendRunCatching {
            val response = chatApi.getChatHistory(userId, page, limit)
            val data = response.data
            if (!response.isSuccess || data == null) throw Exception(response.message)
            val entities = data.list.map { it.toMessageEntity(conversationId = userId) }
            messageDao.insertMessages(entities)
        }

    override suspend fun sendTextMessage(receiverId: Long, content: String): Result<String> =
        enqueueOutgoingMessage(
            receiverId = receiverId,
            type = "text",
            content = content,
        )

    override suspend fun sendMediaMessage(
        receiverId: Long,
        type: String,
        content: String,
        mediaUri: Uri?,
        fileName: String?,
        audioDurationMs: Long?,
        peerNickname: String?,
        peerAvatar: String?,
    ): Result<String> = suspendRunCatching {
        val persistedUri = mediaUri?.let {
            chatMediaStorage.persistUri(it, chatMediaStorage.extensionForType(type))
        }
        enqueueOutgoingMessageInternal(
            receiverId = receiverId,
            type = type,
            content = content,
            localMediaUri = persistedUri?.toString(),
            localMediaFileName = fileName,
            audioDurationMs = audioDurationMs,
            peerNickname = peerNickname,
            peerAvatar = peerAvatar,
        )
    }

    override suspend fun retryMessage(localId: String): Result<Unit> = suspendRunCatching {
        val entity = messageDao.getMessageByLocalId(localId) ?: throw Exception("消息不存在")
        messageDao.updateMessageStatus(
            localId = localId,
            status = "sending",
            errorMessage = null,
        )
        conversationDao.updateLastMessage(
            peerUserId = entity.conversationId,
            preview = previewForMessage(entity.content, entity.type),
            status = "sending",
            at = System.currentTimeMillis(),
            localId = localId,
        )
        chatMessageSender.enqueueSend(localId)
    }

    override suspend fun recallMessage(messageId: Long): Result<Unit> = suspendRunCatching {
        val response = chatApi.recallMessage(messageId)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun markAsRead(targetId: Long): Result<Unit> = suspendRunCatching {
        messageDao.markConversationAsRead(targetId)
        conversationDao.clearUnread(targetId)
        val response = chatApi.markAsRead(
            com.fox.music.core.network.model.MarkChatReadRequest(targetId = targetId),
        )
        if (!response.isSuccess) throw Exception(response.message)
    }

    override suspend fun getUnreadMessages(): Result<List<Message>> = suspendRunCatching {
        val response = chatApi.getUnreadMessages()
        val data = response.data
        if (response.isSuccess && data != null) {
            data.map { it.toMessage() }
        } else throw Exception(response.message)
    }

    @Deprecated("Use observeMessages + syncChatHistory")
    override suspend fun getConversations(): Result<List<ChatConversation>> = suspendRunCatching {
        when (val sync = syncConversations()) {
            is Result.Error -> throw sync.exception
            is Result.Loading -> Unit
            is Result.Success -> Unit
        }
        observeConversations().first()
    }

    @Deprecated("Use observeMessages + syncChatHistory")
    override suspend fun getChatHistory(userId: Long, page: Int, limit: Int): Result<PagedData<Message>> =
        suspendRunCatching {
            when (val sync = syncChatHistory(userId, page, limit)) {
                is Result.Error -> throw sync.exception
                is Result.Loading -> Unit
                is Result.Success -> Unit
            }
            val messages = messageDao.getRecentMessages(userId, limit).map { it.toDomainMessage() }
            PagedData(list = messages, total = messages.size, current = page, pageSize = limit)
        }

    @Deprecated("Use sendTextMessage")
    override suspend fun sendMessage(receiverId: Long, content: String, type: String): Result<Message> =
        suspendRunCatching {
            val localId = when (val result = sendTextMessage(receiverId, content)) {
                is Result.Success -> result.data
                is Result.Error -> throw result.exception
                is Result.Loading -> throw IllegalStateException("发送中")
            }
            messageDao.getMessageByLocalId(localId)?.toDomainMessage()
                ?: throw Exception("发送失败")
        }

    private suspend fun enqueueOutgoingMessage(
        receiverId: Long,
        type: String,
        content: String,
        localMediaUri: String? = null,
        localMediaFileName: String? = null,
        audioDurationMs: Long? = null,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): Result<String> = suspendRunCatching {
        enqueueOutgoingMessageInternal(
            receiverId, type, content, localMediaUri, localMediaFileName,
            audioDurationMs, peerNickname, peerAvatar,
        )
    }

    private suspend fun enqueueOutgoingMessageInternal(
        receiverId: Long,
        type: String,
        content: String,
        localMediaUri: String? = null,
        localMediaFileName: String? = null,
        audioDurationMs: Long? = null,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): String {
        val senderId = userPreferencesRepository.userPreferences.first().userId
            ?: throw IllegalStateException("用户未登录")
        val localId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val preview = previewForMessage(
            content.ifBlank { localMediaFileName.orEmpty() },
            type,
        )
        val entity = MessageEntity(
            localId = localId,
            conversationId = receiverId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            type = type,
            status = "sending",
            localMediaUri = localMediaUri,
            localMediaFileName = localMediaFileName,
            audioDurationMs = audioDurationMs,
            sentAt = now.toString(),
            cachedAt = now,
        )
        messageDao.insertMessage(entity)
        val existingConversation = conversationDao.getConversation(receiverId)
        conversationDao.upsertConversation(
            ConversationEntity(
                peerUserId = receiverId,
                peerNickname = peerNickname ?: existingConversation?.peerNickname,
                peerAvatar = peerAvatar ?: existingConversation?.peerAvatar,
                peerMark = existingConversation?.peerMark,
                lastMessageLocalId = localId,
                lastMessagePreview = preview,
                lastMessageStatus = "sending",
                lastMessageAt = now,
                unreadCount = existingConversation?.unreadCount ?: 0,
                updatedAt = now,
            ),
        )
        chatMessageSender.enqueueSend(localId)
        return localId
    }
}
