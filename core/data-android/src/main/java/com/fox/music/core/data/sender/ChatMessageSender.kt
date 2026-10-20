package com.fox.music.core.data.sender

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fox.music.core.data.worker.SendMessageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessageSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueSend(localId: String, workId: UUID = UUID.randomUUID()): UUID {
        val request = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setId(workId)
            .setInputData(workDataOf(SendMessageWorker.KEY_LOCAL_ID to localId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
        return workId
    }
}
