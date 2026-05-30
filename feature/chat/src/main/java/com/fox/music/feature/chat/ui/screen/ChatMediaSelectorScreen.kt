package com.fox.music.feature.chat.ui.screen

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fox.music.feature.chat.ui.component.ChatMediaPreviewType
import com.fox.music.feature.chat.ui.model.ChatMediaItem
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Size

@Composable
fun ChatMediaSelectorScreen(
    onDismiss: () -> Unit,
    onMediaSelected: (ChatMediaItem) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        ChatMediaSelectorContent(
            onBack = onDismiss,
            onMediaSelected = onMediaSelected,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatMediaSelectorContent(
    onBack: () -> Unit,
    onMediaSelected: (ChatMediaItem) -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMediaReadPermission()) }
    var loading by remember { mutableStateOf(false) }
    var mediaList by remember { mutableStateOf<List<ChatMediaItem>>(emptyList()) }

    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        loading = true
        mediaList = loadMediaItems(context)
        loading = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("选择媒体") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                modifier = Modifier.statusBarsPadding(),
            )
        },
    ) { paddingValues ->
        when {
            !granted -> {
                PermissionPlaceholder(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    onRequest = {
                        XXPermissions.with(context)
                            .permission(requiredMediaPermissions().toList())
                            .request(object : OnPermissionCallback {
                                override fun onGranted(
                                    permissions: MutableList<String>,
                                    allGranted: Boolean,
                                ) {
                                    granted = context.hasMediaReadPermission()
                                    if (!granted) {
                                        Toast.makeText(
                                            context,
                                            "未授予媒体访问权限",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }

                                override fun onDenied(
                                    permissions: MutableList<String>,
                                    doNotAskAgain: Boolean,
                                ) {
                                    granted = context.hasMediaReadPermission()
                                    Toast.makeText(
                                        context,
                                        if (doNotAskAgain) "媒体权限被永久拒绝，请前往设置开启" else "未授予媒体访问权限",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            })
                    },
                )
            }

            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            mediaList.isEmpty() -> {
                EmptyMediaPlaceholder(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(mediaList, key = { it.uri.toString() }) { item ->
                        MediaGridItem(
                            item = item,
                            onClick = { onMediaSelected(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "需要授权访问图片和视频",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            text = "授权后可在应用内预览并发送媒体",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )
        Button(onClick = onRequest) {
            Text("立即授权")
        }
    }
}

@Composable
private fun EmptyMediaPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "未找到图片或视频",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MediaGridItem(
    item: ChatMediaItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val thumbnailBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = item.uri) {
        value = if (item.type == ChatMediaPreviewType.VIDEO) {
            extractVideoFrameBitmap(context, item.uri, Size(512, 512))
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clip(MaterialTheme.shapes.extraSmall)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val model = remember(item.uri, item.type, context) {
            if (item.type == ChatMediaPreviewType.VIDEO && thumbnailBitmap == null) {
                ImageRequest.Builder(context)
                    .data(item.uri)
                    .setParameter("coil#video_frame_millis", 0L)
                    .crossfade(true)
                    .build()
            } else {
                item.uri
            }
        }
        if (thumbnailBitmap != null) {
            Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        } else {
            SubcomposeAsyncImage(
                model = model,
                contentDescription = item.displayName,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
            )
        }
        if (item.type == ChatMediaPreviewType.VIDEO) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

private suspend fun loadMediaItems(context: Context): List<ChatMediaItem> = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.Files.FileColumns.DATE_ADDED,
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
    )
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

    val result = mutableListOf<ChatMediaItem>()
    context.contentResolver.query(
        MediaStore.Files.getContentUri("external"),
        projection,
        selection,
        selectionArgs,
        sortOrder,
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val mediaTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
        val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
        val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idIndex)
            val mediaType = cursor.getInt(mediaTypeIndex)
            val type = when (mediaType) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> ChatMediaPreviewType.VIDEO
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> ChatMediaPreviewType.IMAGE
                else -> continue
            }
            val baseUri = if (type == ChatMediaPreviewType.VIDEO) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            result.add(
                ChatMediaItem(
                    uri = ContentUris.withAppendedId(baseUri, id),
                    type = type,
                    displayName = cursor.getString(nameIndex).orEmpty(),
                    sizeBytes = cursor.getLong(sizeIndex),
                    mimeType = cursor.getString(mimeTypeIndex),
                    dateAddedSec = cursor.getLong(dateAddedIndex),
                ),
            )
        }
    }
    result
}

private suspend fun extractVideoFrameBitmap(
    context: Context,
    uri: android.net.Uri,
    thumbSize: Size,
): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.loadThumbnail(uri, thumbSize, null)
    }.getOrNull() ?: runCatching {
        val retriever = MediaMetadataRetriever()
        retriever.use { mmr ->
            mmr.setDataSource(context, uri)
            mmr.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: mmr.frameAtTime
        }
    }.getOrNull()
}

private fun Context.hasMediaReadPermission(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val hasImages = checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        val hasVideos = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        val hasSelected = checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
        return hasImages || hasVideos || hasSelected
    }
    val permissions = requiredMediaPermissions()
    return permissions.any { permission ->
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requiredMediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
} else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
