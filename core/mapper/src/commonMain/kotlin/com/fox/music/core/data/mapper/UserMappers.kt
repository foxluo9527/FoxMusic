package com.fox.music.core.data.mapper

import com.fox.music.core.model.user.BlacklistUser
import com.fox.music.core.model.user.PrivacySettings
import com.fox.music.core.model.user.UserHomeProfile
import com.fox.music.core.network.model.BlacklistUserDto
import com.fox.music.core.network.model.PrivacySettingsDto
import com.fox.music.core.network.model.UserHomeProfileDto

fun UserHomeProfileDto.toUserHomeProfile(): UserHomeProfile = UserHomeProfile(
    id = id,
    nickname = nickname,
    avatar = avatar,
    signature = signature,
    homepageBackground = homepageBackground,
    isFriend = isFriend,
    isRequested = isRequested,
    isBlocked = isBlocked,
    isBlockedByTarget = isBlockedByTarget,
    isPrivate = isPrivate,
    isSelf = isSelf,
    canViewContent = canViewContent,
    lastListened = lastListened?.toMusic(),
)

fun PrivacySettingsDto.toPrivacySettings(): PrivacySettings = PrivacySettings(
    isPrivate = isPrivate,
    isSearchable = isSearchable,
)

fun BlacklistUserDto.toBlacklistUser(): BlacklistUser = BlacklistUser(
    id = id,
    nickname = nickname,
    avatar = avatar,
    createdAt = createdAt,
)
