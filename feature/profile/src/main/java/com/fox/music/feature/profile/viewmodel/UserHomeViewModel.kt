package com.fox.music.feature.profile.viewmodel

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.usecase.BlockUserUseCase
import com.fox.music.core.domain.usecase.GetPlaylistListUseCase
import com.fox.music.core.domain.usecase.GetUserHomeProfileUseCase
import com.fox.music.core.domain.usecase.GetUserPostsPagingUseCase
import com.fox.music.core.domain.usecase.SubmitReportUseCase
import com.fox.music.core.domain.usecase.UpdateHomepageBackgroundUseCase
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.model.social.Post
import com.fox.music.core.model.user.UserHomeProfile
import com.fox.music.core.player.controller.MusicController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserHomeState(
    val userId: Long = 0L,
    val profile: UserHomeProfile? = null,
    val playlists: List<Playlist> = emptyList(),
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val isPlaylistsLoading: Boolean = false,
    val error: String? = null,
    val showMoreSheet: Boolean = false,
    val showBlockDialog: Boolean = false,
    val showReportSheet: Boolean = false,
    val isReporting: Boolean = false,
    val isBlocking: Boolean = false,
    val isUploadingBackground: Boolean = false,
) : UiState

sealed interface UserHomeIntent : UiIntent {
    data object Load : UserHomeIntent
    data class SelectTab(val index: Int) : UserHomeIntent
    data object ShowMoreSheet : UserHomeIntent
    data object DismissMoreSheet : UserHomeIntent
    data object ShowBlockDialog : UserHomeIntent
    data object DismissBlockDialog : UserHomeIntent
    data object ConfirmBlock : UserHomeIntent
    data object ShowReportSheet : UserHomeIntent
    data object DismissReportSheet : UserHomeIntent
    data class SubmitReport(val reason: String, val description: String?) : UserHomeIntent
    data class UploadBackground(val uri: Uri) : UserHomeIntent
}

sealed interface UserHomeEffect : UiEffect {
    data object NavigateBack : UserHomeEffect
    data class ShowMessage(val message: String) : UserHomeEffect
    data object NavigateToEditProfile : UserHomeEffect
    data object NavigateToPrivacySettings : UserHomeEffect
    data class NavigateToChat(val userId: Long, val peerNickname: String?, val peerAvatar: String?) : UserHomeEffect
    data class NavigateToAddFriend(val userId: Long, val nickname: String?) : UserHomeEffect
    data class NavigateToPlaylist(val playlistId: Long) : UserHomeEffect
    data class NavigateToPostDetail(val postId: Long) : UserHomeEffect
    data object PickBackgroundImage : UserHomeEffect
    data object NavigateToPlayer : UserHomeEffect
}

@HiltViewModel
class UserHomeViewModel @Inject constructor(
    private val getUserHomeProfileUseCase: GetUserHomeProfileUseCase,
    private val getPlaylistListUseCase: GetPlaylistListUseCase,
    getUserPostsPagingUseCase: GetUserPostsPagingUseCase,
    private val blockUserUseCase: BlockUserUseCase,
    private val submitReportUseCase: SubmitReportUseCase,
    private val updateHomepageBackgroundUseCase: UpdateHomepageBackgroundUseCase,
    private val musicController: MusicController,
    savedStateHandle: SavedStateHandle,
) : MviViewModel<UserHomeState, UserHomeIntent, UserHomeEffect>(
    UserHomeState(userId = savedStateHandle.get<Long>("userId") ?: 0L),
) {

    private val userId: Long = savedStateHandle.get<Long>("userId") ?: 0L
    val postsPagingFlow: Flow<PagingData<Post>> =
        getUserPostsPagingUseCase(userId).flow.cachedIn(viewModelScope)

    override fun handleIntent(intent: UserHomeIntent) {
        when (intent) {
            UserHomeIntent.Load -> load()
            is UserHomeIntent.SelectTab -> updateState { copy(selectedTab = intent.index) }
            UserHomeIntent.ShowMoreSheet -> updateState { copy(showMoreSheet = true) }
            UserHomeIntent.DismissMoreSheet -> updateState { copy(showMoreSheet = false) }
            UserHomeIntent.ShowBlockDialog -> updateState { copy(showBlockDialog = true, showMoreSheet = false) }
            UserHomeIntent.DismissBlockDialog -> updateState { copy(showBlockDialog = false) }
            UserHomeIntent.ConfirmBlock -> blockUser()
            UserHomeIntent.ShowReportSheet -> updateState { copy(showReportSheet = true, showMoreSheet = false) }
            UserHomeIntent.DismissReportSheet -> updateState { copy(showReportSheet = false) }
            is UserHomeIntent.SubmitReport -> submitReport(intent.reason, intent.description)
            is UserHomeIntent.UploadBackground -> uploadBackground(intent.uri)
        }
    }

    fun onBackClick() = sendEffect(UserHomeEffect.NavigateBack)

    fun onEditProfileClick() {
        updateState { copy(showMoreSheet = false) }
        sendEffect(UserHomeEffect.NavigateToEditProfile)
    }

    fun onPrivacySettingsClick() {
        updateState { copy(showMoreSheet = false) }
        sendEffect(UserHomeEffect.NavigateToPrivacySettings)
    }

    fun onReplaceBackgroundClick() {
        updateState { copy(showMoreSheet = false) }
        sendEffect(UserHomeEffect.PickBackgroundImage)
    }

    fun onSendMessageClick() {
        val profile = currentState.profile ?: return
        sendEffect(
            UserHomeEffect.NavigateToChat(
                userId = profile.id,
                peerNickname = profile.nickname,
                peerAvatar = profile.avatar,
            ),
        )
    }

    fun onAddFriendClick() {
        val profile = currentState.profile ?: return
        sendEffect(
            UserHomeEffect.NavigateToAddFriend(
                userId = profile.id,
                nickname = profile.nickname,
            ),
        )
    }

    fun onPlaylistClick(playlistId: Long) {
        sendEffect(UserHomeEffect.NavigateToPlaylist(playlistId))
    }

    fun onPostDetailClick(postId: Long) {
        sendEffect(UserHomeEffect.NavigateToPostDetail(postId))
    }

    fun onLastListenedClick() {
        val music = currentState.profile?.lastListened ?: return
        musicController.setPlaylist(listOf(music), startIndex = 0, key = "user_last_listened")
        sendEffect(UserHomeEffect.NavigateToPlayer)
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            when (val result = getUserHomeProfileUseCase(currentState.userId)) {
                is Result.Success -> {
                    updateState { copy(profile = result.data, isLoading = false) }
                    loadPlaylists()
                }
                is Result.Error -> {
                    updateState {
                        copy(isLoading = false, error = result.message ?: "加载失败")
                    }
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun loadPlaylists() {
        val profile = currentState.profile
        if (profile != null && !profile.canViewContent) {
            updateState { copy(playlists = emptyList(), isPlaylistsLoading = false) }
            return
        }
        viewModelScope.launch {
            updateState { copy(isPlaylistsLoading = true) }
            when (val result = getPlaylistListUseCase(currentState.userId)) {
                is Result.Success -> updateState {
                    copy(playlists = result.data, isPlaylistsLoading = false)
                }
                is Result.Error -> updateState { copy(isPlaylistsLoading = false) }
                is Result.Loading -> Unit
            }
        }
    }

    private fun blockUser() {
        viewModelScope.launch {
            updateState { copy(isBlocking = true) }
            when (val result = blockUserUseCase(currentState.userId)) {
                is Result.Success -> {
                    updateState { copy(isBlocking = false, showBlockDialog = false) }
                    sendEffect(UserHomeEffect.ShowMessage("已加入黑名单"))
                    sendEffect(UserHomeEffect.NavigateBack)
                }
                is Result.Error -> {
                    updateState { copy(isBlocking = false) }
                    sendEffect(UserHomeEffect.ShowMessage(result.message ?: "操作失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun submitReport(reason: String, description: String?) {
        viewModelScope.launch {
            updateState { copy(isReporting = true) }
            when (
                val result = submitReportUseCase(
                    targetType = "user",
                    targetId = currentState.userId,
                    reason = reason,
                    description = description,
                )
            ) {
                is Result.Success -> {
                    updateState { copy(isReporting = false, showReportSheet = false) }
                    sendEffect(UserHomeEffect.ShowMessage("举报已提交"))
                }
                is Result.Error -> {
                    updateState { copy(isReporting = false) }
                    sendEffect(UserHomeEffect.ShowMessage(result.message ?: "举报失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }

    private fun uploadBackground(uri: Uri) {
        viewModelScope.launch {
            updateState { copy(isUploadingBackground = true) }
            when (val result = updateHomepageBackgroundUseCase(uri)) {
                is Result.Success -> {
                    updateState { copy(isUploadingBackground = false, profile = result.data) }
                    sendEffect(UserHomeEffect.ShowMessage("背景图已更新"))
                }
                is Result.Error -> {
                    updateState { copy(isUploadingBackground = false) }
                    sendEffect(UserHomeEffect.ShowMessage(result.message ?: "上传失败"))
                }
                is Result.Loading -> Unit
            }
        }
    }
}
