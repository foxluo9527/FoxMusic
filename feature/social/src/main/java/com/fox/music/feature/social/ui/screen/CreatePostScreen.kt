package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.core.ui.component.PostImagePreviewDialog
import com.fox.music.feature.social.viewmodel.CreatePostEffect
import com.fox.music.feature.social.viewmodel.CreatePostIntent
import com.fox.music.feature.social.viewmodel.CreatePostViewModel

const val CREATE_POST_ROUTE = "create_post?postId={postId}"

fun createPostRoute(postId: Long = -1L) = "create_post?postId=$postId"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreatePostScreen(
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onPublished: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var previewImages by remember { mutableStateOf<List<String>?>(null) }
    var previewIndex by remember { mutableIntStateOf(0) }
    val draftImageUrls = remember(state.remoteImageUrls, state.localImageUris) {
        state.remoteImageUrls + state.localImageUris.map { it.toString() }
    }

    previewImages?.let { images ->
        PostImagePreviewDialog(
            images = images,
            initialIndex = previewIndex,
            onDismiss = { previewImages = null },
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            viewModel.sendIntent(CreatePostIntent.AddImage(uri))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CreatePostEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                CreatePostEffect.Published -> {
                    Toast.makeText(
                        context,
                        if (state.isEditMode) "更新成功" else "发布成功",
                        Toast.LENGTH_SHORT,
                    ).show()
                    onPublished()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (!state.isEditMode) {
                viewModel.sendIntent(CreatePostIntent.SaveDraftNow)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FoxTopBar(
                title = if (state.isEditMode) "编辑动态" else "发布动态",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    TextButton(
                        onClick = { viewModel.sendIntent(CreatePostIntent.Publish) },
                        enabled = !state.isPublishing,
                    ) {
                        if (state.isPublishing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(if (state.isEditMode) "保存" else "发布")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoadingDraft) {
            LoadingIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                useLottie = false,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.content,
                onValueChange = { viewModel.sendIntent(CreatePostIntent.UpdateContent(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("分享你的想法...") },
                minLines = 6,
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                state.remoteImageUrls.forEachIndexed { index, url ->
                    Box(modifier = Modifier.size(104.dp)) {
                        CachedImage(
                            imageUrl = url,
                            contentDescription = "帖子图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    previewIndex = index
                                    previewImages = draftImageUrls
                                },
                            contentScale = ContentScale.Crop,
                        )
                        PostDraftImageRemoveButton(
                            onClick = { viewModel.sendIntent(CreatePostIntent.RemoveRemoteImage(url)) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        )
                    }
                }

                state.localImageUris.forEachIndexed { index, uri ->
                    val imageIndex = state.remoteImageUrls.size + index
                    Box(
                        modifier = Modifier.size(104.dp),
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "已选图片",
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable {
                                    previewIndex = imageIndex
                                    previewImages = draftImageUrls
                                },
                            contentScale = ContentScale.Crop,
                        )
                        PostDraftImageRemoveButton(
                            onClick = { viewModel.sendIntent(CreatePostIntent.RemoveImage(uri)) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                        )
                    }
                }

                if (state.localImageUris.size + state.remoteImageUrls.size < 9) {
                    Box(
                        modifier = Modifier
                            .size(104.dp)
                            .clickable {
                                imagePickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!state.isEditMode) {
                Text(
                    text = "草稿将自动保存到本地",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PostDraftImageRemoveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(18.dp)
            .background(Color.White, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "移除图片",
            modifier = Modifier.size(10.dp),
            tint = Color.Black.copy(alpha = 0.65f),
        )
    }
}
