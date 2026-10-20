package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fox.music.feature.chat.ui.screen.UserProfileContent
import com.fox.music.feature.chat.viewmodel.UserProfileState
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 端「用户资料」页：复用 feature:chat commonMain 的 UserProfileContent。
 *
 * 数据源（与 Android 的差异点）：
 *  - 主源：`GET api/users/{userId}` —— 后端已算好 is_friend / is_requested / is_private /
 *    is_self，不需要像 Android 那样「先拉全部好友再本地比对」。
 *  - 备注 mark：后端主页资料里没有，只有 `GET api/friends` 带 —— 因此**仅在 isFriend 为真**
 *    时才多打一次请求取备注，避免每次进资料页都拉全量好友。
 *
 * 导航回调全部交回 Swift 处理（SwiftUI NavigationStack 是唯一的导航真相）。
 */
object FoxUserProfileScreen {

    private val api = FoxMusicApi()

    fun makeUserProfileViewController(
        userId: Long,
        nickname: String?,
        avatar: String?,
        signature: String?,
        isFriend: Boolean,
        isRequested: Boolean,
        onBack: () -> Unit,
        onSendMessage: (userId: Long, peerNickname: String?, peerAvatar: String?) -> Unit,
        onAddFriend: (userId: Long, nickname: String?) -> Unit,
        onUserHome: (userId: Long) -> Unit,
        onShare: (userId: Long, nickname: String?, signature: String?) -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        // 入口带过来的值先铺上（列表页已知的昵称/头像），首屏不空白，随后用服务端数据覆盖
        var state by remember {
            mutableStateOf(
                UserProfileState(
                    userId = userId,
                    nickname = nickname.orEmpty(),
                    avatar = avatar?.takeIf { it.isNotBlank() },
                    signature = signature?.takeIf { it.isNotBlank() },
                    isFriend = isFriend,
                    isRequested = isRequested,
                ),
            )
        }

        suspend fun load() {
            state = state.copy(isLoading = true, error = null)
            try {
                val home = api.fetchUserHomeProfile(userId)
                val friendMark = if (home?.isFriend == true) {
                    runCatching { api.fetchFriends().firstOrNull { it.id == userId }?.mark }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                } else {
                    null
                }
                state = state.copy(
                    nickname = home?.nickname?.takeIf { it.isNotBlank() } ?: state.nickname,
                    avatar = home?.avatar ?: state.avatar,
                    signature = home?.signature ?: state.signature,
                    mark = friendMark,
                    isFriend = home?.isFriend ?: state.isFriend,
                    isRequested = home?.isRequested ?: state.isRequested,
                    isPrivate = home?.isPrivate ?: state.isPrivate,
                    isSelf = home?.isSelf ?: state.isSelf,
                    isLoading = false,
                )
            } catch (e: Exception) {
                println("FoxUserProfileScreen: 资料加载失败: ${e.message}")
                state = state.copy(isLoading = false, error = e.message)
                onToast(e.message ?: "加载失败")
            }
        }

        LaunchedEffect(userId) { load() }

        UserProfileContent(
            state = state,
            onBack = onBack,
            onSendMessageClick = {
                onSendMessage(
                    state.userId,
                    state.displayName.takeIf { it.isNotBlank() && it != "用户" },
                    state.avatar,
                )
            },
            onAddFriendClick = {
                onAddFriend(state.userId, state.nickname.takeIf { it.isNotBlank() })
            },
            onUserHomeClick = { onUserHome(state.userId) },
            onShowRemarkDialog = { state = state.copy(showRemarkDialog = true) },
            onDismissRemarkDialog = { state = state.copy(showRemarkDialog = false) },
            onSubmitRemark = { remark ->
                val trimmed = remark.trim().take(UserProfileState.MAX_REMARK_LENGTH)
                scope.launch {
                    state = state.copy(isSavingRemark = true)
                    try {
                        api.setFriendRemark(state.userId, trimmed)
                        state = state.copy(
                            mark = trimmed.takeIf { it.isNotBlank() },
                            isSavingRemark = false,
                            showRemarkDialog = false,
                        )
                        onToast("备注已更新")
                    } catch (e: Exception) {
                        state = state.copy(isSavingRemark = false)
                        onToast(e.message ?: "备注更新失败")
                    }
                }
            },
            onShareProfile = onShare,
        )
    }
}
