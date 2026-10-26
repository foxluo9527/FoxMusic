package com.fox.music.feature.chat.viewmodel

import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.repository.UserPreferencesRepository
import com.fox.music.core.model.chat.NotificationType
import com.fox.music.core.model.user.NotificationPreferenceSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationSettingsState(
    val settings: NotificationPreferenceSettings = NotificationPreferenceSettings(),
) : UiState

sealed interface NotificationSettingsIntent : UiIntent {
    data class UpdateEnabled(val type: NotificationType, val enabled: Boolean) : NotificationSettingsIntent
}

sealed interface NotificationSettingsEffect : UiEffect {
    data class ShowMessage(val message: String) : NotificationSettingsEffect
    data object NavigateBack : NotificationSettingsEffect
}

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<NotificationSettingsState, NotificationSettingsIntent, NotificationSettingsEffect>(
    NotificationSettingsState(),
) {

    val notificationTypes = listOf(
        NotificationType.MESSAGE,
        NotificationType.COMMENT,
        NotificationType.LIKE,
        NotificationType.FOLLOW,
        NotificationType.MENTION,
        NotificationType.FRIEND_REQUEST,
        NotificationType.SYSTEM,
        NotificationType.MUSIC,
    )

    init {
        userPreferencesRepository.userPreferences
            .onEach { prefs ->
                updateState { copy(settings = prefs.notificationSettings) }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: NotificationSettingsIntent) {
        when (intent) {
            is NotificationSettingsIntent.UpdateEnabled -> updateEnabled(intent.type, intent.enabled)
        }
    }

    fun onBackClick() {
        sendEffect(NotificationSettingsEffect.NavigateBack)
    }

    private fun updateEnabled(type: NotificationType, enabled: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.updateNotificationEnabled(type, enabled)
                .onError { _, msg ->
                    sendEffect(NotificationSettingsEffect.ShowMessage(msg ?: "保存失败"))
                }
        }
    }

    fun typeLabel(type: NotificationType): String = when (type) {
        NotificationType.COMMENT -> "评论提醒"
        NotificationType.LIKE -> "点赞提醒"
        NotificationType.FOLLOW -> "关注提醒"
        NotificationType.MENTION -> "提及提醒"
        NotificationType.MESSAGE -> "聊天消息"
        NotificationType.FRIEND_REQUEST -> "好友请求"
        NotificationType.SYSTEM -> "系统通知"
        NotificationType.MUSIC -> "音乐提醒"
    }
}
