package com.fox.music.feature.player.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.common.session.TokenManager
import com.fox.music.core.domain.repository.AdminMusicRepository
import com.fox.music.core.domain.repository.ImportRepository
import com.fox.music.core.domain.usecase.GetAdminMusicPagingUseCase
import com.fox.music.core.model.music.Music
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManageState(
    val isLoggedIn: Boolean = false,
    val query: String = "",
    val appliedQuery: String = "",
    val musicPaging: Flow<PagingData<Music>> = flowOf(PagingData.empty()),
    val listRefreshKey: Int = 0,
    val pendingDelete: Music? = null,
    val showActionsMenu: Boolean = false,
    val showChartsConfirm: Boolean = false,
    val showHotPlaylistsConfirm: Boolean = false,
    val isUpdatingCharts: Boolean = false,
    val isUpdatingHotPlaylists: Boolean = false,
) : UiState {
    val isBusy: Boolean get() = isUpdatingCharts || isUpdatingHotPlaylists
}

sealed interface ManageIntent : UiIntent {
    data class QueryChange(val query: String) : ManageIntent
    data object Search : ManageIntent
    data object Refresh : ManageIntent
    data object OpenActionsMenu : ManageIntent
    data object DismissActionsMenu : ManageIntent
    data object ToggleActionsMenu : ManageIntent
    data object RequestAddMusic : ManageIntent
    data class RequestDelete(val music: Music) : ManageIntent
    data object ConfirmDelete : ManageIntent
    data object DismissDelete : ManageIntent
    data object RequestUpdateCharts : ManageIntent
    data object ConfirmUpdateCharts : ManageIntent
    data object DismissChartsConfirm : ManageIntent
    data object RequestUpdateHotPlaylists : ManageIntent
    data object ConfirmUpdateHotPlaylists : ManageIntent
    data object DismissHotPlaylistsConfirm : ManageIntent
    data object RequestLogin : ManageIntent
}

sealed interface ManageEffect : UiEffect {
    data object NavigateToSearch : ManageEffect
    data object NavigateToLogin : ManageEffect
    data class ShowMessage(val message: String) : ManageEffect
}

@HiltViewModel
class ManageViewModel @Inject constructor(
    private val tokenManager: TokenManager,
    private val getAdminMusicPagingUseCase: GetAdminMusicPagingUseCase,
    private val adminMusicRepository: AdminMusicRepository,
    private val importRepository: ImportRepository,
) : MviViewModel<ManageState, ManageIntent, ManageEffect>(ManageState()) {

    init {
        viewModelScope.launch {
            tokenManager.isLoggedIn.collectLatest { loggedIn ->
                updateState { copy(isLoggedIn = loggedIn) }
                if (loggedIn) {
                    reloadPaging(currentState.appliedQuery)
                } else {
                    updateState {
                        copy(musicPaging = flowOf(PagingData.empty()))
                    }
                }
            }
        }
    }

    override fun handleIntent(intent: ManageIntent) {
        when (intent) {
            is ManageIntent.QueryChange -> updateState { copy(query = intent.query) }
            ManageIntent.Search -> {
                val keyword = currentState.query.trim()
                updateState { copy(appliedQuery = keyword) }
                if (currentState.isLoggedIn) {
                    reloadPaging(keyword)
                }
            }
            ManageIntent.Refresh -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageEffect.ShowMessage("请先登录"))
                    return
                }
                reloadPaging(currentState.appliedQuery)
            }
            ManageIntent.OpenActionsMenu -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageEffect.NavigateToLogin)
                    return
                }
                updateState { copy(showActionsMenu = true) }
            }
            ManageIntent.DismissActionsMenu -> updateState { copy(showActionsMenu = false) }
            ManageIntent.ToggleActionsMenu -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageEffect.NavigateToLogin)
                    return
                }
                updateState { copy(showActionsMenu = !showActionsMenu) }
            }
            ManageIntent.RequestAddMusic -> {
                updateState { copy(showActionsMenu = false) }
                sendEffect(ManageEffect.NavigateToSearch)
            }
            is ManageIntent.RequestDelete -> updateState { copy(pendingDelete = intent.music) }
            ManageIntent.DismissDelete -> updateState { copy(pendingDelete = null) }
            ManageIntent.ConfirmDelete -> viewModelScope.launch { deletePending() }
            ManageIntent.RequestUpdateCharts -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageEffect.NavigateToLogin)
                    return
                }
                updateState { copy(showActionsMenu = false, showChartsConfirm = true) }
            }
            ManageIntent.DismissChartsConfirm -> updateState { copy(showChartsConfirm = false) }
            ManageIntent.ConfirmUpdateCharts -> viewModelScope.launch { updateCharts() }
            ManageIntent.RequestUpdateHotPlaylists -> {
                if (!currentState.isLoggedIn) {
                    sendEffect(ManageEffect.NavigateToLogin)
                    return
                }
                updateState { copy(showActionsMenu = false, showHotPlaylistsConfirm = true) }
            }
            ManageIntent.DismissHotPlaylistsConfirm ->
                updateState { copy(showHotPlaylistsConfirm = false) }
            ManageIntent.ConfirmUpdateHotPlaylists ->
                viewModelScope.launch { updateHotPlaylists() }
            ManageIntent.RequestLogin -> sendEffect(ManageEffect.NavigateToLogin)
        }
    }

    private fun reloadPaging(keyword: String) {
        val flow = getAdminMusicPagingUseCase(keyword).cachedIn(viewModelScope)
        updateState {
            copy(
                musicPaging = flow,
                listRefreshKey = listRefreshKey + 1,
            )
        }
    }

    private suspend fun deletePending() {
        val music = currentState.pendingDelete ?: return
        updateState { copy(pendingDelete = null) }
        when (val result = adminMusicRepository.deleteAdminMusic(music.id)) {
            is Result.Success -> {
                sendEffect(ManageEffect.ShowMessage("已删除「${music.title}」"))
                reloadPaging(currentState.appliedQuery)
            }
            is Result.Error -> {
                sendEffect(ManageEffect.ShowMessage(result.exception.message ?: "删除失败"))
            }
            Result.Loading -> Unit
        }
    }

    private suspend fun updateCharts() {
        updateState { copy(showChartsConfirm = false, isUpdatingCharts = true) }
        when (val result = importRepository.importNeteaseCharts("mobile")) {
            is Result.Success -> {
                val data = result.data
                sendEffect(
                    ManageEffect.ShowMessage(
                        "排行榜更新完成：成功 ${data.successCount}，失败 ${data.failedCount}（共 ${data.total}）"
                    )
                )
                reloadPaging(currentState.appliedQuery)
            }
            is Result.Error -> {
                sendEffect(ManageEffect.ShowMessage(result.exception.message ?: "更新排行榜失败"))
            }
            Result.Loading -> Unit
        }
        updateState { copy(isUpdatingCharts = false) }
    }

    private suspend fun updateHotPlaylists() {
        updateState { copy(showHotPlaylistsConfirm = false, isUpdatingHotPlaylists = true) }
        when (val result = importRepository.importHotPlaylists("mobile")) {
            is Result.Success -> {
                sendEffect(ManageEffect.ShowMessage(result.data))
            }
            is Result.Error -> {
                sendEffect(
                    ManageEffect.ShowMessage(result.exception.message ?: "更新热门歌单失败")
                )
            }
            Result.Loading -> Unit
        }
        updateState { copy(isUpdatingHotPlaylists = false) }
    }
}
