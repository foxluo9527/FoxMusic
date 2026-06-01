package com.fox.music.feature.chat

import android.net.Uri

const val MESSAGES_ROUTE = "messages"
const val FRIENDS_ROUTE = "messages/friends"
const val SEARCH_USER_ROUTE = "messages/friends/search"
const val USER_PROFILE_ROUTE =
    "user/{userId}?nickname={nickname}&avatar={avatar}&signature={signature}&isFriend={isFriend}&isRequested={isRequested}"
const val ADD_FRIEND_ROUTE = "user/{userId}/add-friend?nickname={nickname}"
const val NOTIFICATION_CATEGORY_ROUTE = "messages/notifications/{type}"
const val CHAT_DETAIL_ROUTE = "chat/{userId}?peerNickname={peerNickname}&peerAvatar={peerAvatar}"
const val CHAT_SEARCH_ROUTE = "messages/search"
const val USER_CHAT_SEARCH_ROUTE = "messages/search/user/{userId}?nickname={nickname}&avatar={avatar}&query={query}"
const val SELECT_FRIEND_ROUTE = "messages/select-friend"
const val CHAT_SETTINGS_ROUTE = "chat/{userId}/settings?peerNickname={peerNickname}&peerAvatar={peerAvatar}"

/** @deprecated 使用 [MESSAGES_ROUTE] */
const val CHAT_ROUTE = MESSAGES_ROUTE

fun notificationCategoryRoute(type: String): String = "messages/notifications/$type"

fun chatDetailRoute(
    userId: Long,
    peerNickname: String? = null,
    peerAvatar: String? = null,
): String = buildString {
    append("chat/$userId")
    val params = mutableListOf<String>()
    peerNickname?.takeIf { it.isNotBlank() }?.let { params.add("peerNickname=${Uri.encode(it)}") }
    peerAvatar?.takeIf { it.isNotBlank() }?.let { params.add("peerAvatar=${Uri.encode(it)}") }
    if (params.isNotEmpty()) append("?${params.joinToString("&")}")
}

fun searchUserRoute(): String = SEARCH_USER_ROUTE

fun userProfileRoute(
    userId: Long,
    nickname: String? = null,
    avatar: String? = null,
    signature: String? = null,
    isFriend: Boolean = false,
    isRequested: Boolean = false,
): String = buildString {
    append("user/$userId")
    append("?nickname=${Uri.encode(nickname.orEmpty())}")
    append("&avatar=${Uri.encode(avatar.orEmpty())}")
    append("&signature=${Uri.encode(signature.orEmpty())}")
    append("&isFriend=$isFriend")
    append("&isRequested=$isRequested")
}

fun addFriendRoute(userId: Long, nickname: String? = null): String =
    "user/$userId/add-friend?nickname=${Uri.encode(nickname.orEmpty())}"

fun chatSearchRoute(): String = CHAT_SEARCH_ROUTE

fun userChatSearchRoute(
    userId: Long,
    nickname: String,
    avatar: String? = null,
    query: String,
): String = buildString {
    append("messages/search/user/$userId")
    append("?nickname=${Uri.encode(nickname)}")
    append("&avatar=${Uri.encode(avatar.orEmpty())}")
    append("&query=${Uri.encode(query)}")
}

fun selectFriendRoute(): String = SELECT_FRIEND_ROUTE

fun chatSettingsRoute(
    userId: Long,
    peerNickname: String? = null,
    peerAvatar: String? = null,
): String = buildString {
    append("chat/$userId/settings")
    val params = mutableListOf<String>()
    peerNickname?.takeIf { it.isNotBlank() }?.let { params.add("peerNickname=${Uri.encode(it)}") }
    peerAvatar?.takeIf { it.isNotBlank() }?.let { params.add("peerAvatar=${Uri.encode(it)}") }
    if (params.isNotEmpty()) append("?${params.joinToString("&")}")
}
