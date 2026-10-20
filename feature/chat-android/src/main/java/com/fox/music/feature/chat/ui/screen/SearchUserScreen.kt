package com.fox.music.feature.chat.ui.screen

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.feature.chat.viewmodel.SearchUserEffect
import com.fox.music.feature.chat.viewmodel.SearchUserIntent
import com.fox.music.feature.chat.viewmodel.SearchUserViewModel

/**
 * Android 侧「搜索用户」入口：只做 ViewModel/Effect 接线，
 * 渲染交给 :feature:chat commonMain 的 [SearchUserContent]。
 */
@Composable
fun SearchUserScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchUserViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onNavigateToUserProfile: (
        userId: Long,
        nickname: String?,
        avatar: String?,
        signature: String?,
        isFriend: Boolean,
        isRequested: Boolean,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                SearchUserEffect.NavigateBack -> onBack()
                is SearchUserEffect.ShowMessage -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                is SearchUserEffect.NavigateToUserProfile -> {
                    onNavigateToUserProfile(
                        effect.userId,
                        effect.nickname,
                        effect.avatar,
                        effect.signature,
                        effect.isFriend,
                        effect.isRequested,
                    )
                }
            }
        }
    }

    SearchUserContent(
        state = state,
        onQueryChange = { viewModel.sendIntent(SearchUserIntent.QueryChange(it)) },
        onSearch = { viewModel.sendIntent(SearchUserIntent.Search) },
        onBack = viewModel::onBackClick,
        onUserClick = viewModel::onUserClick,
        modifier = modifier,
    )
}
