package com.fox.music.iosshared.ui

import com.fox.music.core.network.model.PostMediaDto
import com.fox.music.iosshared.FoxMusicApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fwrite

/**
 * iOS 端「发布动态」后台任务队列（对应 Android 的 PostPublishRepository + WorkManager）。
 *
 * iOS 没有 WorkManager，这里用单例 + 协程顺序处理每个 job：上传媒体 → 调 createPost。
 * 状态与 Android 一致：QUEUED / UPLOADING / PUBLISHING / FAILED / DONE。
 * DONE 表示已发布成功（保留在列表里展示「已发布」，用户可手动关闭页面）。
 *
 * 持久化：任务列表以 JSON 存于 Application Support，App 重启/被杀后恢复未完成任务
 * （UPLOADING / PUBLISHING 重置为 QUEUED 重跑）。
 */
object FoxPostPublishQueue {

    private val api = FoxMusicApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _jobs = MutableStateFlow<List<FoxPostPublishJob>>(emptyList())
    val jobs: StateFlow<List<FoxPostPublishJob>> = _jobs.asStateFlow()

    private val store = PostQueuePersistence(json)

    init {
        // 启动时从磁盘恢复，并把中断态（上传中/发布中）重置为排队重跑。
        val restored = store.load().map { job ->
            if (job.status == FoxPostJobStatus.UPLOADING || job.status == FoxPostJobStatus.PUBLISHING) {
                job.copy(status = FoxPostJobStatus.QUEUED, errorMessage = null, updatedAt = nowMillis())
            } else job
        }
        _jobs.value = restored
        persist()
        // 重新处理所有排队任务。
        restored.filter { it.status == FoxPostJobStatus.QUEUED }.forEach {
            scope.launch { process(it.jobId) }
        }
    }

    fun observeJobs(): StateFlow<List<FoxPostPublishJob>> = jobs

    /** 入队一条发布任务，立即在后台开始处理，返回 jobId。 */
    fun enqueue(
        title: String,
        content: String,
        media: List<FoxPostPublishMedia>,
        tagNames: List<String>,
        tagIds: List<Long>,
        musicId: Long?,
    ): String {
        val id = "ios_${nowMillis()}_${(0..9999).random()}"
        val job = FoxPostPublishJob(
            jobId = id,
            title = title,
            content = content,
            media = media,
            tagNames = tagNames,
            tagIds = tagIds,
            musicId = musicId,
            status = FoxPostJobStatus.QUEUED,
            errorMessage = null,
            createdAt = nowMillis(),
            updatedAt = nowMillis(),
        )
        _jobs.value = _jobs.value + job
        persist()
        scope.launch { process(id) }
        return id
    }

    fun retry(jobId: String) {
        val job = _jobs.value.firstOrNull { it.jobId == jobId } ?: return
        if (job.status != FoxPostJobStatus.FAILED) return
        setStatus(jobId, FoxPostJobStatus.QUEUED, null)
        scope.launch { process(jobId, force = true) }
    }

    fun cancel(jobId: String) {
        _jobs.value = _jobs.value.filter { it.jobId != jobId }
        persist()
    }

    private suspend fun process(jobId: String, force: Boolean = false) {
        val job = _jobs.value.firstOrNull { it.jobId == jobId } ?: return
        if (!force && job.status != FoxPostJobStatus.QUEUED) return

        runCatching {
            setStatus(jobId, FoxPostJobStatus.UPLOADING, null)
            val uploaded = mutableListOf<PostMediaDto>()
            job.media.forEach { m ->
                when (m.type.lowercase()) {
                    "video" -> {
                        val url = api.uploadVideo(m.localPath)
                        uploaded.add(PostMediaDto("video", url, videoUrl = url))
                    }
                    "live" -> {
                        val still = api.uploadImage(m.localPath)
                        val motion = api.uploadVideo(m.localVideoPath ?: m.localPath)
                        uploaded.add(PostMediaDto("live", still, videoUrl = motion))
                    }
                    else -> {
                        val url = api.uploadImage(m.localPath)
                        uploaded.add(PostMediaDto("image", url))
                    }
                }
            }
            setStatus(jobId, FoxPostJobStatus.PUBLISHING, null)
            api.createPost(
                title = job.title,
                content = job.content,
                media = uploaded.ifEmpty { null },
                tagNames = job.tagNames.ifEmpty { null },
                tagIds = job.tagIds.ifEmpty { null },
                musicId = job.musicId,
            )
        }.onSuccess {
            updateJob(jobId) { it.copy(status = FoxPostJobStatus.DONE, updatedAt = nowMillis()) }
        }.onFailure { e ->
            setStatus(jobId, FoxPostJobStatus.FAILED, e.message ?: "发布失败")
        }
    }

    private fun setStatus(jobId: String, status: FoxPostJobStatus, error: String?) {
        updateJob(jobId) { it.copy(status = status, errorMessage = error, updatedAt = nowMillis()) }
    }

    private fun updateJob(jobId: String, transform: (FoxPostPublishJob) -> FoxPostPublishJob) {
        _jobs.value = _jobs.value.map { if (it.jobId == jobId) transform(it) else it }
        persist()
    }

    private fun persist() {
        store.save(_jobs.value)
    }

    private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
}

/** 发布任务 JSON 持久化（Application Support/post_publish_queue.json）。 */
@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
private class PostQueuePersistence(private val json: Json) {

    private val fileUrl: NSURL? by lazy {
        runCatching {
            NSFileManager.defaultManager.URLForDirectory(
                NSApplicationSupportDirectory,
                NSUserDomainMask,
                null,
                true,
                null,
            )?.URLByAppendingPathComponent("post_publish_queue.json", isDirectory = false)
        }.getOrNull()
    }

    @OptIn(ExperimentalForeignApi::class)
    fun load(): List<FoxPostPublishJob> {
        val path = fileUrl?.path ?: return emptyList()
        val data = NSFileManager.defaultManager.contentsAtPath(path) ?: return emptyList()
        val ptr = data.bytes ?: return emptyList()
        val bytes = ptr.reinterpret<kotlinx.cinterop.ByteVar>().readBytes(data.length.toInt())
        val text = bytes.decodeToString()
        return runCatching { json.decodeFromString<List<FoxPostPublishJob>>(text) }
            .getOrElse { emptyList() }
    }

    @OptIn(ExperimentalForeignApi::class)
    fun save(jobs: List<FoxPostPublishJob>) {
        val path = fileUrl?.path ?: return
        runCatching {
            val bytes = json.encodeToString(jobs).encodeToByteArray()
            val file = fopen(path, "w") ?: return@runCatching
            try {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                }
            } finally {
                fclose(file)
            }
        }
    }
}

@Serializable
enum class FoxPostJobStatus {
    QUEUED, UPLOADING, PUBLISHING, FAILED, DONE;

    val label: String
        get() = when (this) {
            QUEUED -> "排队中"
            UPLOADING -> "上传中"
            PUBLISHING -> "发布中"
            FAILED -> "失败"
            DONE -> "已发布"
        }
}

@Serializable
data class FoxPostPublishMedia(
    val type: String,
    val localPath: String,
    val localVideoPath: String? = null,
)

@Serializable
data class FoxPostPublishJob(
    val jobId: String,
    val title: String,
    val content: String,
    val media: List<FoxPostPublishMedia>,
    val tagNames: List<String>,
    val tagIds: List<Long>,
    val musicId: Long?,
    val status: FoxPostJobStatus,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)
