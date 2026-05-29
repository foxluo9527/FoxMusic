package com.fox.music.core.data.sender

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fox.music.core.data.worker.SendMessageWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatMessageSender @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueueSend(localId: String) {
        val request = OneTimeWorkRequestBuilder<SendMessageWorker>()
            .setInputData(workDataOf(SendMessageWorker.KEY_LOCAL_ID to localId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${SendMessageWorker.WORK_NAME_PREFIX}$localId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
