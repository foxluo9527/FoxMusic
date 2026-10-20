package com.fox.music.core.data.recovery

import com.fox.music.core.data.postpublish.PostPublishScheduler
import com.fox.music.core.database.dao.PostPublishJobDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostPublishRecovery @Inject constructor(
    private val postPublishJobDao: PostPublishJobDao,
    private val postPublishScheduler: PostPublishScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun recoverPendingJobs() {
        scope.launch {
            val pending = postPublishJobDao.getPendingJobs()
            pending.forEach { job ->
                if (job.status == "uploading" || job.status == "publishing") {
                    postPublishJobDao.updateJobStatus(job.jobId, "queued", System.currentTimeMillis())
                    postPublishJobDao.resetLocalMediaUploads(job.jobId)
                }
            }
            postPublishScheduler.kickNextPendingJob()
        }
    }
}
