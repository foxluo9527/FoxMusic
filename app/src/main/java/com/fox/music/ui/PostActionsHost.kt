package com.fox.music.ui

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
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
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = { Text("删除动态") },
            text = { Text("确定要删除这条动态吗？") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::deletePost,
                    enabled = !state.isDeleting,
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) {
                    Text("取消")
                }
            },
        )
    }
}
