package com.fox.music.core.data.recovery

import com.fox.music.core.data.sender.ChatMessageSender
import com.fox.music.core.database.dao.MessageDao
import com.fox.music.core.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessageRecovery @Inject constructor(
    private val messageDao: MessageDao,
    private val chatMessageSender: ChatMessageSender,
    private val userPreferencesRepository: UserPreferencesRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recoverPendingMessages() {
        scope.launch {
            val currentUserId = userPreferencesRepository.userPreferences.first().userId ?: return@launch
            messageDao.getPendingOutgoingMessages()
                .filter { it.senderId == currentUserId }
                .forEach { entity ->
                    messageDao.updateMessageStatus(
                        localId = entity.localId,
                        status = "sending",
                        errorMessage = null,
                    )
                    val workId = UUID.randomUUID()
                    messageDao.updateTaskUuid(entity.localId, workId.toString())
                    chatMessageSender.enqueueSend(entity.localId, workId)
                }
        }
    }
}
