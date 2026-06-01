package com.fox.music.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.work.WorkManager
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.result.suspendRunCatching
import com.fox.music.core.data.mapper.previewForMessage
import com.fox.music.core.data.mapper.toIncomingMessageEntity
import com.fox.music.core.data.realtime.ActiveChatTracker
import com.fox.music.core.data.realtime.NotificationPeerResolver
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
import com.fox.music.core.model.chat.Notification
import com.fox.music.core.model.chat.SearchResultItem
import com.fox.music.core.model.user.User
import com.fox.music.core.model.PagedData
import com.fox.music.core.network.api.ChatApiService
import com.fox.music.core.network.model.MessageDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatApi: ChatApiService,
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao,
    private val chatMessageSender: ChatMessageSender,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val chatMediaStorage: ChatMediaStorage,
    private val activeChatTracker: ActiveChatTracker,
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
                        unreadCount = maxOf(remote.unreadCount, local?.unreadCount ?: 0),
                    ),
                )
            } else {
                conversationDao.upsertConversation(local)
            }
        }
        conversationDao.deleteGhostConversations()
    }

    override suspend fun syncUnreadMessages(peerUserId: Long): Result<Unit> = suspendRunCatching {
        val response = chatApi.getUnreadMessages(peerUserId)
        val data = response.data
        if (!response.isSuccess || data == null) throw Exception(response.message)
        persistUnreadMessages(data)
    }

    @Deprecated("History API is not available")
    override suspend fun syncChatHistory(userId: Long, page: Int, limit: Int): Result<Unit> =
        suspendRunCatching {
            val response = chatApi.getChatHistory(userId, page, limit)
            val data = response.data
            if (!response.isSuccess || data == null) throw Exception(response.message)
            val entities = data.list.map { it.toMessageEntity(conversationId = userId) }
            messageDao.insertMessages(entities)
        }

    override suspend fun sendTextMessage(
        receiverId: Long,
        content: String,
        peerNickname: String?,
        peerAvatar: String?,
    ): Result<String> =
        enqueueOutgoingMessage(
            receiverId = receiverId,
            type = "text",
            content = content,
            peerNickname = peerNickname,
            peerAvatar = peerAvatar,
        )

    override suspend fun sendMediaMessage(
        receiverId: Long,
        type: String,
        content: String,
        mediaUri: Uri?,
        fileName: String?,
        audioDurationMs: Long?,
        imageSendOriginal: Boolean,
        peerNickname: String?,
        peerAvatar: String?,
    ): Result<String> = suspendRunCatching {
        enqueueOutgoingMessageInternal(
            receiverId = receiverId,
            type = type,
            content = content,
            localMediaUri = mediaUri?.toString(),
            localMediaFileName = fileName,
            audioDurationMs = audioDurationMs,
            imageSendOriginal = imageSendOriginal,
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
        val workId = java.util.UUID.randomUUID()
        messageDao.updateTaskUuid(localId, workId.toString())
        chatMessageSender.enqueueSend(localId, workId)
    }

    override suspend fun recallMessage(messageId: Long): Result<Unit> = suspendRunCatching {
        val response = chatApi.recallMessage(messageId)
        if (response.isSuccess) Unit else throw Exception(response.message)
    }

    override suspend fun recallAndUpdateLocal(messageId: Long, localId: String): Result<Unit> = suspendRunCatching {
        val response = chatApi.recallMessage(messageId)
        if (!response.isSuccess) throw Exception(response.message)
        messageDao.updateMessageStatus(
            localId = localId,
            status = "sent",
            content = "你撤回了一条消息",
        )
    }

    override suspend fun deleteMessage(localId: String): Result<Unit> = suspendRunCatching {
        messageDao.deleteMessageByLocalId(localId)
    }

    override suspend fun cancelSending(localId: String): Result<Unit> = suspendRunCatching {
        val entity = messageDao.getMessageByLocalId(localId) ?: throw Exception("消息不存在")
        if (entity.taskUuid != null) {
            WorkManager.getInstance(context)
                .cancelWorkById(java.util.UUID.fromString(entity.taskUuid))
        }
        messageDao.updateMessageStatus(
            localId = localId,
            status = "failed",
            errorMessage = "已取消发送",
        )
    }

    override suspend fun markAsRead(targetId: Long): Result<Unit> = suspendRunCatching {
        messageDao.markConversationAsRead(targetId)
        conversationDao.clearUnread(targetId)
        val response = chatApi.markAsRead(
            com.fox.music.core.network.model.MarkChatReadRequest(targetId = targetId),
        )
        if (!response.isSuccess) throw Exception(response.message)
    }

    override suspend fun deleteConversation(targetId: Long): Result<Unit> = suspendRunCatching {
        conversationDao.deleteConversation(targetId)
    }

    override suspend fun pinConversation(targetId: Long, isPinned: Boolean): Result<Unit> = suspendRunCatching {
        conversationDao.updatePinStatus(targetId, isPinned)
    }

    override suspend fun ingestIncomingMessage(message: Message): Result<Unit> = suspendRunCatching {
        if (messageDao.getMessageByServerId(message.id) != null) return@suspendRunCatching

        val currentUserId = userPreferencesRepository.userPreferences.first().userId
            ?: throw IllegalStateException("用户未登录")
        val peerId = if (message.senderId == currentUserId) message.receiverId else message.senderId
        val entity = message.toIncomingMessageEntity(conversationId = peerId)
        messageDao.insertMessage(entity)

        val local = conversationDao.getConversation(peerId)
        val isIncoming = message.senderId != currentUserId
        val isActiveChat = activeChatTracker.currentPeerUserId.value == peerId

        conversationDao.upsertConversation(
            ConversationEntity(
                peerUserId = peerId,
                peerNickname = local?.peerNickname,
                peerAvatar = local?.peerAvatar,
                peerMark = local?.peerMark,
                lastMessageLocalId = entity.localId,
                lastMessagePreview = previewForMessage(entity.content, entity.type),
                lastMessageStatus = entity.status,
                lastMessageAt = entity.cachedAt,
                unreadCount = if (isIncoming && !isActiveChat) {
                    messageDao.countIncomingUnread(peerId, currentUserId)
                } else {
                    local?.unreadCount ?: 0
                },
                updatedAt = entity.cachedAt,
            ),
        )

        if (isIncoming && isActiveChat) {
            messageDao.markConversationAsRead(peerId)
            conversationDao.clearUnread(peerId)
        } else if (isIncoming) {
            conversationDao.deleteGhostConversations()
        }
    }

    override suspend fun landMessageNotification(notification: Notification): Result<Unit> =
        suspendRunCatching {
            val messageId = NotificationPeerResolver.resolveMessageId(notification)
            if (messageId != null && messageDao.getMessageByServerId(messageId) != null) {
                return@suspendRunCatching
            }

            val peerId = NotificationPeerResolver.resolvePeerUserId(notification)
            if (peerId <= 0L) {
                syncUnreadMessages(0)
                return@suspendRunCatching
            }

            val local = conversationDao.getConversation(peerId)
            val isActiveChat = activeChatTracker.currentPeerUserId.value == peerId
            val now = System.currentTimeMillis()
            val preview = NotificationPeerResolver.previewFromContent(notification.content)
            val nickname = notification.sender?.nickname
                ?: notification.sender?.username
                ?: NotificationPeerResolver.nicknameFromContent(notification.content)
                ?: local?.peerNickname
            val previewLocalId = messageId?.let { "server_$it" }
                ?: local?.lastMessageLocalId
                ?: "notify_${notification.id}"

            conversationDao.upsertConversation(
                ConversationEntity(
                    peerUserId = peerId,
                    peerNickname = nickname,
                    peerAvatar = local?.peerAvatar ?: notification.sender?.avatar,
                    peerMark = local?.peerMark,
                    lastMessageLocalId = previewLocalId,
                    lastMessagePreview = preview,
                    lastMessageStatus = "sent",
                    lastMessageAt = now,
                    unreadCount = if (isIncomingUnread(isActiveChat)) {
                        (local?.unreadCount ?: 0) + 1
                    } else {
                        local?.unreadCount ?: 0
                    },
                    updatedAt = now,
                ),
            )
            conversationDao.deleteGhostConversations()
        }

    override suspend fun hasIngestedMessage(serverId: Long): Boolean =
        messageDao.getMessageByServerId(serverId) != null

    override fun searchMessages(query: String): Flow<List<SearchResultItem>> =
        messageDao.searchMessages(query).map { entities ->
            entities.groupBy { it.conversationId }.map { (conversationId, messages) ->
                val latestMessage = messages.first()
                val conversation = conversationDao.getConversation(conversationId)
                SearchResultItem(
                    user = User(
                        id = conversationId,
                        username = "",
                        nickname = conversation?.peerNickname ?: "",
                        avatar = conversation?.peerAvatar,
                    ),
                    lastMessage = latestMessage.toDomainMessage(),
                    matchCount = messages.size,
                )
            }
        }

    override fun searchMessagesByUser(peerUserId: Long, query: String): Flow<List<Message>> =
        messageDao.searchMessagesByUser(peerUserId, query).map { entities ->
            entities.map { it.toDomainMessage() }
        }

    override suspend fun countMessagesByUser(peerUserId: Long, query: String): Int =
        messageDao.countMessagesByUser(peerUserId, query)

    private fun isIncomingUnread(isActiveChat: Boolean): Boolean = !isActiveChat

    @Deprecated("Use syncUnreadMessages")
    override suspend fun getUnreadMessages(): Result<List<Message>> = suspendRunCatching {
        val response = chatApi.getUnreadMessages(peerUserId = 0)
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
        imageSendOriginal: Boolean = false,
        peerNickname: String? = null,
        peerAvatar: String? = null,
    ): Result<String> = suspendRunCatching {
        enqueueOutgoingMessageInternal(
            receiverId, type, content, localMediaUri, localMediaFileName,
            audioDurationMs, imageSendOriginal, peerNickname, peerAvatar,
        )
    }

    private suspend fun enqueueOutgoingMessageInternal(
        receiverId: Long,
        type: String,
        content: String,
        localMediaUri: String? = null,
        localMediaFileName: String? = null,
        audioDurationMs: Long? = null,
        imageSendOriginal: Boolean = false,
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
        val persistedMediaUri = persistOutgoingMediaUri(localMediaUri, localMediaFileName, type)
        val entity = MessageEntity(
            localId = localId,
            conversationId = receiverId,
            senderId = senderId,
            receiverId = receiverId,
            content = content,
            type = type,
            status = "sending",
            localMediaUri = persistedMediaUri,
            localMediaFileName = localMediaFileName,
            fileType = resolveOutgoingFileType(type, localMediaFileName),
            imageSendOriginal = imageSendOriginal,
            audioDurationMs = audioDurationMs,
            sentAt = now.toString(),
            cachedAt = now,
        )
        messageDao.insertMessage(entity)
        val workId = java.util.UUID.randomUUID()
        messageDao.updateTaskUuid(localId, workId.toString())
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
        chatMessageSender.enqueueSend(localId, workId)
        return localId
    }

    private suspend fun persistUnreadMessages(data: List<MessageDto>) {
        if (data.isEmpty()) return
        val currentUserId = userPreferencesRepository.userPreferences.first().userId
            ?: throw IllegalStateException("用户未登录")

        val newDtos = data.filter { dto -> messageDao.getMessageByServerId(dto.id) == null }
        if (newDtos.isEmpty()) return

        val entities = newDtos.map { dto ->
            val peerId = if (dto.senderId == currentUserId) dto.receiverId else dto.senderId
            dto.toMessageEntity(conversationId = peerId)
        }
        messageDao.insertMessages(entities)

        newDtos.groupBy { dto ->
            if (dto.senderId == currentUserId) dto.receiverId else dto.senderId
        }.forEach { (peerId, dtos) ->
            val peerEntities = entities.filter { it.conversationId == peerId }
            val latest = peerEntities.maxByOrNull { it.cachedAt } ?: return@forEach
            val local = conversationDao.getConversation(peerId)
            val peerSenderDto = dtos.lastOrNull { it.senderId == peerId }
            val unreadFromDb = messageDao.countIncomingUnread(peerId, currentUserId)
            conversationDao.upsertConversation(
                ConversationEntity(
                    peerUserId = peerId,
                    peerNickname = local?.peerNickname ?: peerSenderDto?.senderNickname,
                    peerAvatar = local?.peerAvatar ?: peerSenderDto?.senderAvatar,
                    peerMark = local?.peerMark ?: peerSenderDto?.senderRemark,
                    lastMessageLocalId = latest.localId,
                    lastMessagePreview = previewForMessage(latest.content, latest.type),
                    lastMessageStatus = latest.status,
                    lastMessageAt = latest.cachedAt,
                    unreadCount = unreadFromDb,
                    updatedAt = latest.cachedAt,
                ),
            )
        }
        conversationDao.deleteGhostConversations()
    }

    private fun persistOutgoingMediaUri(
        localMediaUri: String?,
        localMediaFileName: String?,
        type: String,
    ): String? {
        if (localMediaUri.isNullOrBlank()) return null
        val uri = Uri.parse(localMediaUri)
        if (uri.scheme == "file") return localMediaUri
        val extension = chatMediaStorage.extensionForFile(localMediaFileName, type)
        return chatMediaStorage.persistUri(uri, extension).toString()
    }

    private fun resolveOutgoingFileType(type: String, fileName: String?): String? = when (type.lowercase()) {
        "image" -> "image"
        "video" -> "video"
        "audio", "voice" -> "audio"
        "file" -> {
            val name = fileName.orEmpty().lowercase()
            when {
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") ||
                    name.endsWith(".gif") || name.endsWith(".webp") || name.endsWith(".bmp") -> "image"
                name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".avi") ||
                    name.endsWith(".mkv") || name.endsWith(".webm") -> "video"
                else -> "file"
            }
        }
        else -> null
    }
}
