package com.fox.music.iosshared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fox.music.core.network.model.PostMediaDto
import com.fox.music.core.network.model.TagDto
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * 发布动态页（iOS）。对应 Android CreatePostScreen + CreatePostViewModel。
 *
 * 媒体 / 歌曲 / 话题的「选择」交互在 iOS 端由 Swift 完成（PHPicker、歌曲选择、话题选择），
 * 结果通过 [FoxCreatePostStore] 写回，本 Composable 只负责渲染与发布。
 * 发布走 [FoxPostPublishQueue]（无 WorkManager，用单例协程后台处理）。
 */
object FoxCreatePostScreen {

    fun makeViewController(
        editPostId: Long = -1L,
        onBack: () -> Unit,
        onPublished: () -> Unit,
        onPickMedia: () -> Unit,
        onSelectMusic: () -> Unit,
        onSelectTag: () -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController {
        FoxCreatePostContent(
            editPostId = editPostId,
            onBack = onBack,
            onPublished = onPublished,
            onPickMedia = onPickMedia,
            onSelectMusic = onSelectMusic,
            onSelectTag = onSelectTag,
            onToast = onToast,
        )
    }
}

/** Swift 侧把选择结果写回这里的单例；Composable 通过 collectAsState 观察。 */
object FoxCreatePostStore {
    private val _media = MutableStateFlow<List<FoxCreatePostMediaItem>>(emptyList())
    val media: StateFlow<List<FoxCreatePostMediaItem>> = _media.asStateFlow()

    private val _music = MutableStateFlow<FoxSelectedMusic?>(null)
    val music: StateFlow<FoxSelectedMusic?> = _music.asStateFlow()

    private val _tags = MutableStateFlow<List<FoxCreatePostTagItem>>(emptyList())
    val tags: StateFlow<List<FoxCreatePostTagItem>> = _tags.asStateFlow()

    fun addMedia(path: String, type: String, videoPath: String? = null) {
        val next = (_media.value + FoxCreatePostMediaItem(path, type, videoPath)).takeLast(9)
        _media.value = next
    }

    /** 编辑态：整体替换媒体列表（回显的远程媒体在前）。 */
    fun setMedia(items: List<FoxCreatePostMediaItem>) {
        _media.value = items.take(9)
    }

    fun removeMedia(index: Int) {
        _media.value = _media.value.filterIndexed { i, _ -> i != index }
    }

    fun setMusic(music: FoxSelectedMusic?) {
        _music.value = music
    }

    fun addTag(name: String, id: Long = 0L) {
        val normalized = name.trim().removePrefix("#")
        if (normalized.isBlank()) return
        if (_tags.value.any { it.name.equals(normalized, ignoreCase = true) }) return
        _tags.value = _tags.value + FoxCreatePostTagItem(name = normalized, id = id, checked = true)
    }

    fun toggleTag(name: String, id: Long = 0L) {
        val normalized = name.trim().removePrefix("#")
        val existing = _tags.value.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
        _tags.value = if (existing != null) {
            _tags.value.filter { it !== existing }
        } else {
            _tags.value + FoxCreatePostTagItem(name = normalized, id = id, checked = true)
        }
    }

    fun clear() {
        _media.value = emptyList()
        _music.value = null
        _tags.value = emptyList()
    }
}

data class FoxCreatePostMediaItem(
    val path: String,
    val type: String,
    val videoPath: String? = null,
    /** 非空表示帖子上已存在的远程媒体（编辑态回显），发布时直接保留无需再上传。 */
    val remoteUrl: String? = null,
    val remoteVideoUrl: String? = null,
    val remoteCoverUrl: String? = null,
)

data class FoxSelectedMusic(
    val id: Long,
    val title: String,
    val cover: String? = null,
    val artist: String? = null,
    val isFavorite: Boolean = false,
)

data class FoxCreatePostTagItem(
    val name: String,
    val id: Long = 0L,
    val checked: Boolean = true,
)

@Composable
private fun FoxCreatePostContent(
    editPostId: Long,
    onBack: () -> Unit,
    onPublished: () -> Unit,
    onPickMedia: () -> Unit,
    onSelectMusic: () -> Unit,
    onSelectTag: () -> Unit,
    onToast: (String) -> Unit,
) {
    val isEdit = editPostId > 0L
    val api = remember { FoxMusicApi() }
    // 必须 remember：之前是裸 MainScope()，每次重组（输入标题/正文时高频发生）
    // 都会泄漏一个永不取消的作用域。rememberCoroutineScope 与本页生命周期绑定。
    val scope = rememberCoroutineScope()

    val media by FoxCreatePostStore.media.collectAsState()
    val music by FoxCreatePostStore.music.collectAsState()
    val tags by FoxCreatePostStore.tags.collectAsState()

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var isPublishing by remember { mutableStateOf(false) }
    var recommendedTags by remember { mutableStateOf<List<TagDto>>(emptyList()) }
    var editLoaded by remember { mutableStateOf(!isEdit) }

    // 页面退出时清掉单例选择态，避免下次发帖带上上次的残留（新建/编辑都需要）。
    DisposableEffect(Unit) {
        onDispose { FoxCreatePostStore.clear() }
    }

    // 编辑态：拉取帖子详情回显标题/正文/媒体/歌曲/话题。
    LaunchedEffect(editPostId) {
        if (!isEdit) return@LaunchedEffect
        runCatching {
            val detail = api.fetchPostDetail(editPostId)
                ?: error("帖子不存在或已删除")
            FoxCreatePostStore.clear()
            title = detail.title.orEmpty()
            content = detail.content
            val existing = detail.media.map { m ->
                FoxCreatePostMediaItem(
                    path = m.url,
                    type = m.type,
                    remoteUrl = m.url,
                    remoteVideoUrl = m.videoUrl,
                    remoteCoverUrl = m.coverUrl,
                )
            }
            // 兼容只有 images 没有 media 结构的旧数据。
            val merged = if (existing.isEmpty()) {
                detail.images.map { FoxCreatePostMediaItem(path = it, type = "image", remoteUrl = it) }
            } else {
                existing
            }
            FoxCreatePostStore.setMedia(merged)
            val musicId = detail.musicId ?: detail.music?.id?.toLongOrNull()
            if (musicId != null) {
                FoxCreatePostStore.setMusic(
                    FoxSelectedMusic(
                        id = musicId,
                        title = detail.music?.title.orEmpty(),
                        cover = detail.music?.coverImage ?: detail.music?.coverUrl,
                        artist = detail.music?.artists?.joinToString("、") { it.name },
                    ),
                )
            }
            val echoTags = if (detail.tagItems.isNotEmpty()) {
                detail.tagItems.map { FoxCreatePostTagItem(name = it.name, id = it.id, checked = true) }
            } else {
                detail.tags.map { FoxCreatePostTagItem(name = it, id = 0L, checked = true) }
            }
            echoTags.forEach { FoxCreatePostStore.addTag(it.name, it.id) }
        }.onFailure {
            onToast(it.message ?: "帖子加载失败")
        }
        editLoaded = true
    }

    LaunchedEffect(title, content, music) {
        runCatching {
            recommendedTags = api.fetchRecommendedPostTags(
                content = listOfNotNull(title, content).joinToString(" ").ifBlank { null },
                musicId = music?.id,
            )
        }
    }

    if (!editLoaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = "关闭")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(if (isEdit) "编辑" else "发布", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(48.dp))
        }

        // 媒体
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(media, key = { it.path }) { item ->
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (item.type == "video" || item.type == "live") {
                            Icons.Default.PlayArrow
                        } else {
                            Icons.Default.Image
                        },
                        contentDescription = null,
                        tint = Color.White,
                    )
                    IconButton(
                        onClick = {
                            val idx = media.indexOf(item)
                            if (idx >= 0) FoxCreatePostStore.removeMedia(idx)
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White)
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(onClick = onPickMedia),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加媒体")
                }
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("添加标题") },
            singleLine = true,
        )
        OutlinedTextField(
            value = content,
            onValueChange = { content = it },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            placeholder = { Text("添加正文") },
        )

        // 歌曲
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .clickable(onClick = onSelectMusic)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (music != null) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = music!!.title)
                    if (!music!!.artist.isNullOrBlank()) {
                        Text(
                            text = music!!.artist!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(
                    onClick = { FoxCreatePostStore.setMusic(null) },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Default.MusicNote, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "添加歌曲",
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(">")
            }
        }

        // 话题
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = false,
                onClick = onSelectTag,
                label = { Text("话题 >") },
            )
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tags) { item ->
                    FilterChip(
                        selected = item.checked,
                        onClick = { FoxCreatePostStore.toggleTag(item.name, item.id) },
                        label = { Text("#${item.name}") },
                    )
                }
                items(recommendedTags) { tag ->
                    if (tags.none { it.name.equals(tag.name, ignoreCase = true) }) {
                        FilterChip(
                            selected = false,
                            onClick = { FoxCreatePostStore.addTag(tag.name, tag.id) },
                            label = { Text("#${tag.name}") },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = {
                if (title.isBlank() && content.isBlank() && media.isEmpty()) {
                    onToast("请填写内容或添加媒体")
                    return@Button
                }
                isPublishing = true
                scope.launch {
                    runCatching {
                        if (isEdit) {
                            // 编辑态：旧媒体直接保留，新媒体先上传，同步提交 PUT（对齐 Android）。
                            val finalMedia = media.map { item ->
                                if (!item.remoteUrl.isNullOrBlank()) {
                                    PostMediaDto(
                                        type = item.type,
                                        url = item.remoteUrl,
                                        videoUrl = item.remoteVideoUrl,
                                        coverUrl = item.remoteCoverUrl,
                                    )
                                } else {
                                    when (item.type.lowercase()) {
                                        "video" -> {
                                            val url = api.uploadVideo(item.path)
                                            PostMediaDto("video", url, videoUrl = url)
                                        }
                                        "live" -> {
                                            val still = api.uploadImage(item.path)
                                            val motion = api.uploadVideo(item.videoPath ?: item.path)
                                            PostMediaDto("live", still, videoUrl = motion)
                                        }
                                        else -> {
                                            val url = api.uploadImage(item.path)
                                            PostMediaDto("image", url)
                                        }
                                    }
                                }
                            }
                            api.updatePost(
                                postId = editPostId,
                                title = title.ifBlank { null },
                                content = content.ifBlank { title },
                                media = finalMedia.ifEmpty { null },
                                tagNames = tags.filter { it.id <= 0L }.map { it.name }.ifEmpty { null },
                                tagIds = tags.filter { it.id > 0L }.map { it.id }.ifEmpty { null },
                                musicId = music?.id,
                            )
                            // 通知栈下的详情页/广场刷新。
                            FoxPostChangeNotifier.notifyPostChanged(editPostId)
                        } else {
                            FoxPostPublishQueue.enqueue(
                                title = title,
                                content = content.ifBlank { title },
                                media = media.map { FoxPostPublishMedia(it.type, it.path, it.videoPath) },
                                tagNames = tags.filter { it.id <= 0L }.map { it.name },
                                tagIds = tags.filter { it.id > 0L }.map { it.id },
                                musicId = music?.id,
                            )
                        }
                    }.onSuccess {
                        isPublishing = false
                        onToast(if (isEdit) "已保存" else "已加入上传队列")
                        onPublished()
                    }.onFailure {
                        isPublishing = false
                        onToast(it.message ?: if (isEdit) "保存失败" else "发布失败")
                    }
                }
            },
            enabled = !isPublishing && editLoaded,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A7A)),
        ) {
            if (isPublishing || !editLoaded) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(if (isEdit) "保存" else "发布")
            }
        }
    }
}
