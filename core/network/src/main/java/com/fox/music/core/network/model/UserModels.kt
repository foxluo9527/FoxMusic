package com.fox.music.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserHomeProfileDto(
    val id: Long,
    val nickname: String? = null,
    val avatar: String? = null,
    val signature: String? = null,
    @SerialName("homepage_background")
    val homepageBackground: String? = null,
    @SerialName("is_friend")
    val isFriend: Boolean = false,
    @SerialName("is_requested")
    val isRequested: Boolean = false,
    @SerialName("is_blocked")
    val isBlocked: Boolean = false,
    @SerialName("is_blocked_by_target")
    val isBlockedByTarget: Boolean = false,
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("is_self")
    val isSelf: Boolean = false,
    @SerialName("can_view_content")
    val canViewContent: Boolean = true,
    @SerialName("last_listened")
    val lastListened: MusicDto? = null,
)

@Serializable
data class PrivacySettingsDto(
    @SerialName("is_private")
    val isPrivate: Boolean = false,
    @SerialName("is_searchable")
    val isSearchable: Boolean = true,
)

@Serializable
data class UpdatePrivacyRequest(
    @SerialName("is_private")
    val isPrivate: Boolean? = null,
    @SerialName("is_searchable")
    val isSearchable: Boolean? = null,
)

@Serializable
data class BlacklistUserDto(
    val id: Long,
    val nickname: String? = null,
    val avatar: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)
