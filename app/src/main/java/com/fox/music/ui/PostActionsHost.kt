package com.fox.music.ui

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.FoxConfirmDialog
import com.fox.music.core.ui.component.ReportBottomSheet

@Composable
fun PostActionsHost(
    onNavigateToSelectFriend: () -> Unit,
    onNavigateToEditPost: (Long) -> Unit,
    onPostDeleted: () -> Unit = {},
    viewModel: PostActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is PostActionsEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                PostActionsEffect.NavigateToSelectFriend -> onNavigateToSelectFriend()
                is PostActionsEffect.NavigateToEditPost -> onNavigateToEditPost(effect.postId)
                PostActionsEffect.PostDeleted -> onPostDeleted()
            }
        }
    }

    val post = state.actionPost
    if (state.showReportSheet && post != null) {
        ReportBottomSheet(
            targetTitle = "动态",
            targetName = post.content.take(50),
            reportHeader = "举报动态",
            isSubmitting = state.isReportSubmitting,
            onDismiss = viewModel::dismissReportSheet,
            onSubmit = viewModel::submitReport,
        )
    }

    if (state.showDeleteDialog && post != null) {
        // 全局弹窗扫尾：删除是破坏性操作，统一走 FoxConfirmDialog（主钮 ErrorRed）
        FoxConfirmDialog(
            title = "删除动态？",
            text = "删除后无法恢复，这条动态下的评论也会一并移除。",
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            icon = Icons.Default.DeleteOutline,
            onDismiss = viewModel::dismissDeleteDialog,
            onConfirm = viewModel::deletePost,
        )
    }
}
