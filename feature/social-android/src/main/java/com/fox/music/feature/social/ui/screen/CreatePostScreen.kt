package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.social.PostMedia
import com.fox.music.core.model.social.PostMediaType
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.PostLiveBadge
import com.fox.music.core.ui.component.PostMediaPreviewDialog
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import com.fox.music.core.common.util.MediaUrlResolver
import com.fox.music.core.common.util.extractVideoFrameBitmap
import com.fox.music.feature.social.viewmodel.CreatePostMediaItem
import com.fox.music.feature.social.viewmodel.CreatePostEffect
import com.fox.music.feature.social.viewmodel.CreatePostIntent
import com.fox.music.feature.social.viewmodel.CreatePostViewModel


const val CREATE_POST_ROUTE = "create_post?postId={postId}"

fun createPostRoute(postId: Long = -1L) = "create_post?postId=$postId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onPublished: () -> Unit = {},
    onSelectMusic: () -> Unit = {},
    onSelectTag: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewMedia by remember { mutableStateOf<List<PostMedia>?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(9),
    ) { uris ->
        uris.forEach { uri ->
            val mime = context.contentResolver.getType(uri)
            viewModel.sendIntent(CreatePostIntent.AddMedia(uri, mime))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CreatePostEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                CreatePostEffect.Published,
                CreatePostEffect.QueuedForPublish -> {
                    Toast.makeText(
                        context,
                        when (effect) {
                            CreatePostEffect.QueuedForPublish -> "已加入上传队列"
                            else -> if (state.isEditMode) "更新成功" else "发布成功"
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                    onPublished()
                }
            }
        }
    }

    previewMedia?.let { media ->
        PostMediaPreviewDialog(
            media = media,
            initialIndex = previewIndex,
            onDismiss = { previewMedia = null },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = {
                    if (!state.isEditMode) {
                        viewModel.sendIntent(CreatePostIntent.SaveDraftNow)
                    }
                    onBack()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (state.isEditMode) "编辑动态" else "发布",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(48.dp))
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.sendIntent(CreatePostIntent.SaveDraftNow) },
                    enabled = !state.isEditMode,
                ) {
                    Text("存草稿")
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { viewModel.sendIntent(CreatePostIntent.Publish) },
                    enabled = !state.isPublishing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5A7A)),
                ) {
                    if (state.isPublishing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(if (state.isEditMode) "保存" else "发布")
                    }
                }
            }
        },
    ) { innerPadding ->
        if (state.isLoadingDraft) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CreatePostMediaStrip(
                mediaItems = state.mediaItems,
                isAddingMedia = state.isAddingMedia,
                onPreview = { index ->
                    previewMedia = state.mediaItems.map { it.toPreviewPostMedia() }
                    previewIndex = index
                },
                onRemove = { index ->
                    viewModel.sendIntent(CreatePostIntent.RemoveMedia(index))
                },
                onMove = { from, to ->
                    viewModel.sendIntent(CreatePostIntent.MoveMedia(from, to))
                },
                onAddMedia = {
                    mediaPickerLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageAndVideo,
                        ),
                    )
                },
            )

            OutlinedTextField(
                value = state.title,
                onValueChange = { viewModel.sendIntent(CreatePostIntent.UpdateTitle(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("添加标题") },
                singleLine = true,
            )
            OutlinedTextField(
                value = state.content,
                onValueChange = { viewModel.sendIntent(CreatePostIntent.UpdateContent(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                placeholder = { Text("添加正文") },
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable(onClick = onSelectMusic)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.musicId != null) {
                    CachedImage(
                        imageUrl = state.musicCover,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        shape = RoundedCornerShape(4.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = state.musicTitle.orEmpty(),
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (state.musicIsFavorite) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "已收藏",
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFFE53935),
                                )
                            }
                        }
                        Text(
                            text = state.musicArtist.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { viewModel.sendIntent(CreatePostIntent.ClearMusic) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Icon(Icons.Default.MusicNote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "添加内容",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(">")
                }
            }

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
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.displayTags.forEach { item ->
                        key(item.tag.name.lowercase()) {
                            FilterChip(
                                selected = item.checked,
                                onClick = {
                                    viewModel.sendIntent(CreatePostIntent.ToggleTag(item.tag))
                                },
                                label = { Text("#${item.tag.name}") },
                            )
                        }
                    }
                    val recommend = state.recommendedTags.filter { suggested ->
                        state.selectedTags.none {
                            it.tag.name.equals(suggested.name, ignoreCase = true)
                        }
                    }
                    recommend.forEach { tag ->
                        key(tag.name.lowercase()) {
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.sendIntent(CreatePostIntent.SelectTag(tag)) },
                                label = { Text("#${tag.name}") },
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CreatePostMediaStrip(
    mediaItems: List<CreatePostMediaItem>,
    isAddingMedia: Boolean,
    onPreview: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddMedia: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val itemSizePx = with(density) { 96.dp.toPx() }
    val itemSpacingPx = with(density) { 8.dp.toPx() }
    val itemStepPx = itemSizePx + itemSpacingPx

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState, enabled = draggingIndex < 0),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        mediaItems.forEachIndexed { index, item ->
            key(item.stableKey()) {
                val isDragging = draggingIndex == index
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .zIndex(if (isDragging) 1f else 0f)
                        .offset {
                            if (isDragging) {
                                IntOffset(dragOffsetX.roundToInt(), 0)
                            } else {
                                IntOffset.Zero
                            }
                        }
                        .then(
                            if (isDragging) {
                                Modifier.shadow(8.dp, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            },
                        )
                        .graphicsLayer {
                            if (isDragging) {
                                scaleX = 1.05f
                                scaleY = 1.05f
                            }
                        }
                        .pointerInput(item.stableKey(), mediaItems.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingIndex = index
                                    dragOffsetX = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggingIndex < 0) return@detectDragGesturesAfterLongPress
                                    dragOffsetX += dragAmount.x
                                    var current = draggingIndex
                                    val lastIndex = mediaItems.lastIndex
                                    while (dragOffsetX > itemStepPx / 2 && current < lastIndex) {
                                        onMove(current, current + 1)
                                        current += 1
                                        draggingIndex = current
                                        dragOffsetX -= itemStepPx
                                    }
                                    while (dragOffsetX < -itemStepPx / 2 && current > 0) {
                                        onMove(current, current - 1)
                                        current -= 1
                                        draggingIndex = current
                                        dragOffsetX += itemStepPx
                                    }
                                },
                                onDragEnd = {
                                    draggingIndex = -1
                                    dragOffsetX = 0f
                                },
                                onDragCancel = {
                                    draggingIndex = -1
                                    dragOffsetX = 0f
                                },
                            )
                        },
                ) {
                    CreatePostMediaThumbnail(
                        item = item,
                        onPreview = { onPreview(index) },
                    )
                    when (item.type) {
                        PostMediaType.VIDEO -> {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                        PostMediaType.LIVE -> {
                            PostLiveBadge(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp),
                            )
                        }
                        else -> Unit
                    }
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = "拖动排序",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .size(16.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(1.dp),
                    )
                    IconButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(28.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "删除", tint = Color.White)
                    }
                }
            }
        }
        if (mediaItems.size < 9) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(enabled = !isAddingMedia, onClick = onAddMedia),
                contentAlignment = Alignment.Center,
            ) {
                if (isAddingMedia) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Add, contentDescription = "添加媒体")
                }
            }
        }
    }
}

@Composable
private fun CreatePostMediaThumbnail(
    item: CreatePostMediaItem,
    onPreview: () -> Unit,
) {
    when (item.type) {
        PostMediaType.VIDEO -> {
            LocalVideoThumbnail(
                videoUri = (item.videoUri ?: item.uri).toString(),
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onPreview),
            )
        }
        else -> {
            val thumbUrl = item.remote?.displayUrl ?: item.uri.toString()
            CachedImage(
                imageUrl = thumbUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onPreview),
                shape = RoundedCornerShape(8.dp),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

private fun CreatePostMediaItem.stableKey(): String = buildString {
    append(uri)
    append('|')
    append(videoUri ?: "")
    append('|')
    append(remote?.url ?: "")
}

@Composable
private fun LocalVideoThumbnail(
    videoUri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resolved = remember(videoUri) { MediaUrlResolver.resolve(videoUri) ?: videoUri }
    val frameBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, resolved) {
        value = extractVideoFrameBitmap(context, resolved)
    }
    if (frameBitmap != null) {
        Image(
            bitmap = frameBitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = modifier.background(Color.Black.copy(alpha = 0.85f)),
        )
    }
}

private fun CreatePostMediaItem.toPreviewPostMedia(): PostMedia {
    remote?.let { return it }
    return when (type) {
        PostMediaType.VIDEO -> PostMedia(
            type = type,
            url = uri.toString(),
            videoUrl = (videoUri ?: uri).toString(),
            coverUrl = null,
        )
        PostMediaType.LIVE -> PostMedia(
            type = type,
            url = uri.toString(),
            videoUrl = videoUri?.toString(),
        )
        PostMediaType.IMAGE -> PostMedia(type = type, url = uri.toString())
    }
}
