package com.fox.music.feature.profile.viewmodel

import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.domain.usecase.GetProfileUseCase
import com.fox.music.core.domain.usecase.UpdateProfileUseCase
import com.fox.music.core.domain.usecase.UploadImageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditProfileState(
    val nickname: String = "",
    val signature: String = "",
    val email: String = "",
    val avatarUrl: String? = null,
    val isLoading: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface EditProfileIntent : UiIntent {
    data object Load : EditProfileIntent
    data class NicknameChange(val value: String) : EditProfileIntent
    data class SignatureChange(val value: String) : EditProfileIntent
    data class UploadAvatar(val uri: Uri) : EditProfileIntent
    data object Save : EditProfileIntent
}

sealed interface EditProfileEffect : UiEffect {
    data object NavigateBack : EditProfileEffect
    data class ShowMessage(val message: String) : EditProfileEffect
}

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    private val getProfileUseCase: GetProfileUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val uploadImageUseCase: UploadImageUseCase,
) : MviViewModel<EditProfileState, EditProfileIntent, EditProfileEffect>(EditProfileState()) {

    override fun handleIntent(intent: EditProfileIntent) {
        when (intent) {
            EditProfileIntent.Load -> load()
            is EditProfileIntent.NicknameChange -> updateState { copy(nickname = intent.value) }
            is EditProfileIntent.SignatureChange -> updateState { copy(signature = intent.value) }
            is EditProfileIntent.UploadAvatar -> uploadAvatar(intent.uri)
            EditProfileIntent.Save -> save()
        }
    }

    private fun load() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getProfileUseCase()
                .onSuccess { user ->
                    updateState {
                        copy(
                            nickname = user.nickname ?: "",
                            signature = user.signature ?: "",
                            email = user.email ?: "",
                            avatarUrl = user.avatar,
                            isLoading = false,
                        )
                    }
                }
                .onError { _, msg ->
                    updateState { copy(isLoading = false, error = msg ?: "加载失败") }
                }
        }
    }

    private fun uploadAvatar(uri: Uri) {
        viewModelScope.launch {
            updateState { copy(isUploadingAvatar = true) }
            uploadImageUseCase(uri)
                .onSuccess { url ->
                    updateProfileUseCase(avatar = url)
                        .onSuccess { user ->
                            updateState {
                                copy(avatarUrl = user.avatar, isUploadingAvatar = false)
                            }
                            sendEffect(EditProfileEffect.ShowMessage("头像已更新"))
                        }
                        .onError { _, msg ->
                            updateState { copy(isUploadingAvatar = false) }
                            sendEffect(EditProfileEffect.ShowMessage(msg ?: "更新头像失败"))
                        }
                }
                .onError { _, msg ->
                    updateState { copy(isUploadingAvatar = false) }
                    sendEffect(EditProfileEffect.ShowMessage(msg ?: "上传失败"))
                }
        }
    }

    private fun save() {
        viewModelScope.launch {
            updateState { copy(isLoading = true) }
            val nickname = currentState.nickname.trim().ifBlank { null }
            val signature = currentState.signature.trim().ifBlank { null }
            updateProfileUseCase(
                nickname = nickname,
                signature = signature,
                avatar = currentState.avatarUrl,
            )
                .onSuccess {
                    updateState { copy(isLoading = false) }
                    sendEffect(EditProfileEffect.ShowMessage("保存成功"))
                    sendEffect(EditProfileEffect.NavigateBack)
                }
                .onError { _, msg ->
                    updateState { copy(isLoading = false) }
                    sendEffect(EditProfileEffect.ShowMessage(msg ?: "保存失败"))
                }
        }
    }
}
