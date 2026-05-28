package com.fox.music.feature.playlist.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.fox.music.feature.playlist.ui.util.PlaylistImageCropContract
import com.fox.music.feature.playlist.ui.util.toCroppedPlaylistCover
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.playlist.viewmodel.PlaylistEditEffect
import com.fox.music.feature.playlist.viewmodel.PlaylistEditIntent
import com.fox.music.feature.playlist.viewmodel.PlaylistEditViewModel

const val PLAYLIST_EDIT_ROUTE = "playlist_edit/{playlistId}"

fun playlistEditRoute(playlistId: Long) = "playlist_edit/$playlistId"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistEditScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistEditViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSaved: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val imageCropLauncher = rememberLauncherForActivityResult(
        contract = PlaylistImageCropContract(),
    ) { croppedUri ->
        val cropped = croppedUri?.let { toCroppedPlaylistCover(context, it) }
        if (cropped != null) {
            viewModel.sendIntent(PlaylistEditIntent.SetCoverPreview(cropped.uri, cropped.file))
            viewModel.sendIntent(PlaylistEditIntent.UploadCover(cropped.uri))
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            imageCropLauncher.launch(uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PlaylistEditEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                PlaylistEditEffect.Saved -> {
                    onSaved()
                    onBack()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            FoxTopBar(
                title = "编辑歌单",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    TextButton(
                        onClick = { viewModel.sendIntent(PlaylistEditIntent.Save) },
                        enabled = !state.isSaving && !state.isLoading,
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("保存")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                useLottie = false,
            )

            state.error != null -> ErrorView(
                message = state.error!!,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                onRetry = { viewModel.sendIntent(PlaylistEditIntent.Load) },
            )

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlaylistCoverPicker(
                        coverImage = state.coverImage,
                        previewCoverUri = state.previewCoverUri,
                        previewCoverFile = state.previewCoverFile,
                        isUploading = state.isUploadingCover,
                        onPickCover = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = state.title,
                        onValueChange = {
                            viewModel.sendIntent(PlaylistEditIntent.UpdateTitle(it))
                        },
                        label = { Text("歌单名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = state.description,
                        onValueChange = {
                            viewModel.sendIntent(PlaylistEditIntent.UpdateDescription(it))
                        },
                        label = { Text("歌单描述") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        minLines = 4,
                        maxLines = 6,
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    PlaylistPublicSetting(
                        isPublic = state.isPublic,
                        onIsPublicChange = {
                            viewModel.sendIntent(PlaylistEditIntent.UpdateIsPublic(it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistPublicSetting(
    isPublic: Boolean,
    onIsPublicChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "公开歌单",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "开启后其他用户可搜索并查看此歌单",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = isPublic,
            onCheckedChange = onIsPublicChange,
        )
    }
}

@Composable
private fun PlaylistCoverPicker(
    coverImage: String?,
    previewCoverUri: android.net.Uri?,
    previewCoverFile: java.io.File?,
    isUploading: Boolean,
    onPickCover: () -> Unit,
) {
    val context = LocalContext.current
    val previewModel = when {
        previewCoverFile?.exists() == true -> previewCoverFile
        previewCoverUri != null -> previewCoverUri
        else -> null
    }
    Box(
        modifier = Modifier
            .size(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !isUploading, onClick = onPickCover),
        contentAlignment = Alignment.Center,
    ) {
        when {
            previewModel != null -> {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(previewModel)
                        .crossfade(false)
                        .build(),
                    contentDescription = "歌单封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = { CoverPlaceholder() },
                    error = { CoverPlaceholder() },
                )
            }

            !coverImage.isNullOrBlank() -> {
                CachedImage(
                    imageUrl = coverImage,
                    contentDescription = "歌单封面",
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    placeholderIcon = Icons.AutoMirrored.Filled.QueueMusic,
                )
            }

            else -> CoverPlaceholder()
        }

        if (isUploading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "更换封面",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "点击上传封面",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CoverPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
