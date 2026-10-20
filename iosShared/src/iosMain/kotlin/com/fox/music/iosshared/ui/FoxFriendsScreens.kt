package com.fox.music.iosshared.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fox.music.core.data.mapper.toFriend
import com.fox.music.core.data.mapper.toFriendRequest
import com.fox.music.core.data.mapper.toSearchedUser
import com.fox.music.feature.chat.ui.screen.AddFriendRequestContent
import com.fox.music.feature.chat.ui.screen.FriendsContent
import com.fox.music.feature.chat.ui.screen.SearchUserContent
import com.fox.music.feature.chat.util.displayName
import com.fox.music.feature.chat.viewmodel.AddFriendRequestState
import com.fox.music.feature.chat.viewmodel.FriendsState
import com.fox.music.feature.chat.viewmodel.MAX_MARK_LENGTH
import com.fox.music.feature.chat.viewmodel.SearchUserState
import com.fox.music.iosshared.FoxAuthStore
import com.fox.music.iosshared.FoxMusicApi
import com.fox.music.iosshared.foxComposeViewController
import kotlinx.coroutines.launch
import platform.UIKit.UIViewController

/**
 * iOS 端「我的好友」页：复用 feature:chat commonMain 的 FriendsContent。
 *
 * 数据全部 Kotlin 侧生产（Ktor REST：api/friends、api/friends/requests、api/friends/accept）。
 * 与 Android 的差异只有数据来源：Android 走 SocialRepository（Room 缓存 + 网络），
 * iOS 直接打网络，所以每次进页面/下拉都会真实请求一次。
 *
 * 用 `pageOwnsTopBar = true`：本页自带 M3 TopAppBar，由它消费状态栏 inset
 * （见 foxComposeViewController 的说明），宿主要让位。
 */
object FoxFriendsScreen {

    private val api = FoxMusicApi()

    fun makeFriendsViewController(
        onBack: () -> Unit,
        onAddFriendClick: () -> Unit,
        onUserProfileClick: (
            userId: Long,
            nickname: String?,
            avatar: String?,
            signature: String?,
            isFriend: Boolean,
            isRequested: Boolean,
        ) -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(FriendsState(isLoading = true)) }

        suspend fun load() {
            if (!FoxAuthStore.isLoggedIn) {
                state = FriendsState(isLoading = false)
                return
            }
            state = state.copy(isLoading = true, error = null)
            try {
                val friends = api.fetchFriends().map { it.toFriend() }
                val requests = api.fetchFriendRequests().map { it.toFriendRequest() }
                state = FriendsState(friends = friends, friendRequests = requests, isLoading = false)
            } catch (e: Exception) {
                println("FoxFriendsScreen: 好友加载失败: ${e.message}")
                state = state.copy(isLoading = false, error = e.message)
                onToast(e.message ?: "加载失败")
            }
        }

        // 登录/登出后 FoxAuthStore.revision 变化 → 重新拉一次（与消息页同一套机制）
        LaunchedEffect(FoxAuthStore.revision) { load() }

        FriendsContent(
            state = state,
            onBack = onBack,
            onAddFriendClick = onAddFriendClick,
            onFriendClick = { friend ->
                onUserProfileClick(
                    friend.id,
                    friend.displayName(),
                    friend.avatar,
                    friend.signature,
                    true,
                    false,
                )
            },
            onAcceptRequest = { requestId ->
                scope.launch {
                    state = state.copy(isAccepting = true)
                    try {
                        api.acceptFriendRequest(requestId)
                        onToast("已添加好友")
                        load()
                    } catch (e: Exception) {
                        onToast(e.message ?: "接受申请失败")
                    }
                    state = state.copy(isAccepting = false)
                }
            },
        )
    }
}

/**
 * iOS 端「搜索用户」页：复用 SearchUserContent。
 *
 * 关键词留空时不发请求，与 Android ViewModel 的「请输入搜索关键词」提示对齐 ——
 * 区别是 iOS 没有输入框 Toast 的便利，这里只在结果区显示空态。
 */
object FoxSearchUserScreen {

    private val api = FoxMusicApi()

    fun makeSearchUserViewController(
        onBack: () -> Unit,
        onUserProfileClick: (
            userId: Long,
            nickname: String?,
            avatar: String?,
            signature: String?,
            isFriend: Boolean,
            isRequested: Boolean,
        ) -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        var state by remember { mutableStateOf(SearchUserState()) }

        fun search() {
            val keyword = state.query.trim()
            if (keyword.isBlank()) {
                onToast("请输入搜索关键词")
                return
            }
            scope.launch {
                state = state.copy(isLoading = true, error = null)
                try {
                    val users = api.searchUsers(keyword).map { it.toSearchedUser() }
                    state = state.copy(
                        results = users,
                        isLoading = false,
                        hasSearched = true,
                        error = null,
                    )
                } catch (e: Exception) {
                    state = state.copy(
                        isLoading = false,
                        hasSearched = true,
                        error = e.message ?: "搜索失败",
                    )
                }
            }
        }

        SearchUserContent(
            state = state,
            onQueryChange = { state = state.copy(query = it, error = null) },
            onSearch = ::search,
            onBack = onBack,
            onUserClick = { user ->
                onUserProfileClick(
                    user.id,
                    user.displayName(),
                    user.avatar,
                    user.signature,
                    user.isFriend,
                    user.isRequested,
                )
            },
        )
    }
}

/**
 * iOS 端「发送好友申请」页：复用 AddFriendRequestContent。
 * 备注上限走共享常量 MAX_MARK_LENGTH（与 Android 输入截断一致）。
 */
object FoxAddFriendRequestScreen {

    private val api = FoxMusicApi()

    fun makeAddFriendViewController(
        userId: Long,
        nickname: String,
        onBack: () -> Unit,
        onRequestSent: () -> Unit,
        onToast: (String) -> Unit,
    ): UIViewController = foxComposeViewController(pageOwnsTopBar = true) {
        val scope = rememberCoroutineScope()
        var state by remember {
            mutableStateOf(AddFriendRequestState(userId = userId, nickname = nickname))
        }

        AddFriendRequestContent(
            state = state,
            onMessageChange = { state = state.copy(message = it) },
            onMarkChange = { state = state.copy(mark = it.take(MAX_MARK_LENGTH)) },
            onSubmit = {
                val message = state.message.trim()
                if (message.isBlank()) {
                    onToast("请填写验证消息")
                } else if (!state.isSubmitting) {
                    scope.launch {
                        state = state.copy(isSubmitting = true, error = null)
                        try {
                            api.sendFriendRequest(
                                friendId = state.userId,
                                message = message,
                                mark = state.mark.trim().takeIf { it.isNotBlank() },
                            )
                            onToast("好友申请已发送")
                            onRequestSent()
                        } catch (e: Exception) {
                            onToast(e.message ?: "发送失败")
                        }
                        state = state.copy(isSubmitting = false)
                    }
                }
            },
            onBack = onBack,
        )
    }
}
