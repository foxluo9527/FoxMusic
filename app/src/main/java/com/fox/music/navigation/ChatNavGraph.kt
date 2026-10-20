package com.fox.music.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.fox.music.core.model.chat.MessageType
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.feature.chat.ADD_FRIEND_ROUTE
import com.fox.music.feature.chat.CHAT_DETAIL_ROUTE
import com.fox.music.feature.chat.CHAT_SEARCH_ROUTE
import com.fox.music.feature.chat.CHAT_SETTINGS_ROUTE
import com.fox.music.feature.chat.FRIENDS_ROUTE
import com.fox.music.feature.chat.NOTIFICATION_CATEGORY_ROUTE
import com.fox.music.feature.chat.NOTIFICATION_SETTINGS_ROUTE
import com.fox.music.feature.chat.SEARCH_USER_ROUTE
import com.fox.music.feature.chat.SELECT_FRIEND_ROUTE
import com.fox.music.feature.chat.USER_CHAT_SEARCH_ROUTE
import com.fox.music.feature.chat.USER_PROFILE_ROUTE
import com.fox.music.feature.chat.addFriendRoute
import com.fox.music.feature.chat.chatDetailRoute
import com.fox.music.feature.chat.chatSearchRoute
import com.fox.music.feature.chat.chatSettingsRoute
import com.fox.music.feature.chat.notificationCategoryRoute
import com.fox.music.feature.chat.searchUserRoute
import com.fox.music.feature.chat.selectFriendRoute
import com.fox.music.feature.chat.ui.screen.AddFriendRequestScreen
import com.fox.music.feature.chat.ui.screen.ChatDetailScreen
import com.fox.music.feature.chat.ui.screen.ChatSearchScreen
import com.fox.music.feature.chat.ui.screen.ChatSettingsScreen
import com.fox.music.feature.chat.ui.screen.FriendsScreen
import com.fox.music.feature.chat.ui.screen.MessagesScreen
import com.fox.music.feature.chat.ui.screen.NotificationCategoryScreen
import com.fox.music.feature.chat.ui.screen.NotificationSettingsScreen
import com.fox.music.feature.chat.ui.screen.SearchUserScreen
import com.fox.music.feature.chat.ui.screen.SelectFriendScreen
import com.fox.music.feature.chat.ui.screen.UserChatSearchScreen
import com.fox.music.feature.chat.ui.screen.UserProfileScreen
import com.fox.music.feature.chat.userChatSearchRoute
import com.fox.music.feature.chat.userProfileRoute
import com.fox.music.feature.discover.artistDetailRoute
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.player.ui.screen.musicCommentDetailRoute
import com.fox.music.feature.social.postDetailRoute

fun NavGraphBuilder.chatNavGraph(context: NavGraphContext) {
    val navController = context.navController
    val musicController = context.musicController

    composableStack(NOTIFICATION_SETTINGS_ROUTE) {
        NotificationSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
        )
    }

    composableStack(FRIENDS_ROUTE) {
        FriendsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToSearch = { navController.navigate(searchUserRoute()) },
            onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                navController.navigate(
                    userProfileRoute(
                        userId = userId,
                        nickname = nickname,
                        avatar = avatar,
                        signature = signature,
                        isFriend = isFriend,
                        isRequested = isRequested,
                    ),
                )
            },
        )
    }

    composableStack(SEARCH_USER_ROUTE) {
        SearchUserScreen(
            modifier = Modifier
                .fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                navController.navigate(
                    userProfileRoute(
                        userId = userId,
                        nickname = nickname,
                        avatar = avatar,
                        signature = signature,
                        isFriend = isFriend,
                        isRequested = isRequested,
                    ),
                )
            },
        )
    }

    composableStack(
        route = USER_PROFILE_ROUTE,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType },
            navArgument("nickname") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("avatar") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("signature") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("isFriend") {
                type = NavType.BoolType
                defaultValue = false
            },
            navArgument("isRequested") {
                type = NavType.BoolType
                defaultValue = false
            },
        ),
    ) { backStackEntry ->
        val friendRequestSent by backStackEntry.savedStateHandle
            .getStateFlow("friendRequestSent", false)
            .collectAsStateWithLifecycle()
        UserProfileScreen(
            modifier = Modifier.fillMaxSize(),
            friendRequestSent = friendRequestSent,
            onFriendRequestSentHandled = {
                backStackEntry.savedStateHandle["friendRequestSent"] = false
            },
            onBack = { navController.popBackStack() },
            onNavigateToChat = { userId, peerNickname, peerAvatar ->
                navController.navigate(chatDetailRoute(userId, peerNickname, peerAvatar))
            },
            onNavigateToAddFriend = { userId, nickname ->
                navController.navigate(addFriendRoute(userId, nickname))
            },
            onNavigateToUserHome = { userId ->
                navController.navigate(com.fox.music.feature.profile.ui.screen.userHomeRoute(userId))
            },
            onShareProfile = { userId, nickname, signature ->
                context.profileShareActionsViewModel.shareProfile(userId, nickname, signature)
            },
        )
    }

    composableStack(
        route = ADD_FRIEND_ROUTE,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType },
            navArgument("nickname") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        AddFriendRequestScreen(
            modifier = Modifier
                .fillMaxSize(),
            onBack = { navController.popBackStack() },
            onRequestSent = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("friendRequestSent", true)
                navController.popBackStack()
            },
        )
    }

    composableStack(
        route = NOTIFICATION_CATEGORY_ROUTE,
        arguments = listOf(navArgument("type") { type = NavType.StringType }),
    ) {
        NotificationCategoryScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToPostDetail = { postId ->
                navController.navigateToDetail(postDetailRoute(postId))
            },
            onNavigateToMusicComment = { musicId, commentId ->
                navController.navigate(musicCommentDetailRoute(musicId, commentId))
            },
        )
    }

    composableStack(
        route = CHAT_DETAIL_ROUTE,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType },
            navArgument("peerNickname") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("peerAvatar") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        ChatDetailScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToPlayer = { music ->
                musicController.setPlaylist(listOf(music), startIndex = 0, key = "chat_peer_last_listened")
                navController.navigateToPlayer()
            },
            onNavigateToSettings = { userId, nickname, avatar ->
                navController.navigate(chatSettingsRoute(userId, nickname, avatar))
            },
            onForward = { message ->
                navController.navigate(selectFriendRoute())
            },
            onShareClick = { message, allMessages ->
                val shareId = message.shareId ?: return@ChatDetailScreen
                when (message.shareType) {
                    "music" -> {
                        message.shareData?.let { data ->
                            val musicList = allMessages
                                .filter { it.type == MessageType.SHARE && it.shareType == "music" && it.shareData != null }
                                .mapNotNull { msg ->
                                    msg.shareData?.let { sd ->
                                        Music(
                                            id = sd.id,
                                            title = sd.title ?: "",
                                            url = sd.url ?: "",
                                            coverImage = sd.coverImage,
                                            duration = sd.duration,
                                            artists = sd.artists,
                                            lyrics = sd.lyrics,
                                            lyricsTrans = sd.lyricsTrans,
                                            lyricsUrl = sd.lyricsUrl,
                                        )
                                    }
                                }
                                .ifEmpty {
                                    listOf(
                                        Music(
                                            id = data.id,
                                            title = data.title ?: "",
                                            url = data.url ?: "",
                                            coverImage = data.coverImage,
                                            duration = data.duration,
                                            artists = data.artists,
                                            lyrics = data.lyrics,
                                            lyricsTrans = data.lyricsTrans,
                                            lyricsUrl = data.lyricsUrl,
                                        )
                                    )
                                }
                            val startIndex = musicList.indexOfFirst { it.id == data.id }.coerceAtLeast(0)
                            musicController.setPlaylist(musicList, startIndex, "chat_music")
                            navController.navigateToPlayer()
                        }
                    }
                    "playlist" -> navController.navigate(playlistDetailRoute(shareId))
                    "album" -> navController.navigate(playlistDetailRoute(shareId, DetailType.ALBUM))
                    "artist" -> navController.navigate(artistDetailRoute(shareId))
                    "post" -> navController.navigateToDetail(postDetailRoute(shareId))
                    "user" -> navController.navigate(
                        com.fox.music.feature.profile.ui.screen.userHomeRoute(shareId),
                    )
                }
            },
        )
    }

    composableStack(SELECT_FRIEND_ROUTE) {
        SelectFriendScreen(
            modifier = Modifier
                .fillMaxSize(),
            onBack = {
                context.musicActionsViewModel.dismissSelectFriend()
                context.postActionsViewModel.dismissSelectFriend()
                context.profileShareActionsViewModel.dismissSelectFriend()
                navController.popBackStack()
            },
            onFriendSelected = { friend ->
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("selectedFriendId", friend.id)
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("selectedFriendNickname", friend.mark ?: friend.nickname ?: friend.username ?: "")
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("selectedFriendAvatar", friend.avatar ?: "")
                navController.popBackStack()
            },
        )
    }

    composableStack(
        route = CHAT_SETTINGS_ROUTE,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType },
            navArgument("peerNickname") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("peerAvatar") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        ChatSettingsScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToUserProfile = { userId, nickname, avatar, signature, isFriend, isRequested ->
                navController.navigate(
                    userProfileRoute(
                        userId = userId,
                        nickname = nickname,
                        avatar = avatar,
                        signature = signature,
                        isFriend = isFriend,
                        isRequested = isRequested,
                    ),
                )
            },
            onNavigateToChatSearch = { userId, nickname, avatar ->
                navController.navigate(userChatSearchRoute(userId, nickname, avatar, ""))
            },
        )
    }

    composableStack(CHAT_SEARCH_ROUTE) {
        ChatSearchScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToUserSearch = { userId, nickname, avatar, query ->
                navController.navigate(userChatSearchRoute(userId, nickname, avatar, query))
            },
            onNavigateToChatDetail = { userId, messageId ->
                navController.navigate(chatDetailRoute(userId))
            },
            onNavigateToUserProfile = { userId ->
                navController.navigate(userProfileRoute(userId))
            },
        )
    }

    composableStack(
        route = USER_CHAT_SEARCH_ROUTE,
        arguments = listOf(
            navArgument("userId") { type = NavType.LongType },
            navArgument("nickname") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("avatar") {
                type = NavType.StringType
                defaultValue = ""
            },
            navArgument("query") {
                type = NavType.StringType
                defaultValue = ""
            },
        ),
    ) {
        UserChatSearchScreen(
            modifier = Modifier.fillMaxSize(),
            onBack = { navController.popBackStack() },
            onNavigateToChatDetail = { userId, messageId ->
                navController.navigate(chatDetailRoute(userId))
            },
            onNavigateToUserProfile = { userId ->
                navController.navigate(userProfileRoute(userId))
            },
        )
    }
}
