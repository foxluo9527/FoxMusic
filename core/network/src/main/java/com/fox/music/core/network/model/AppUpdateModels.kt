package com.fox.music.core.network.model

import kotlinx.serialization.Serializable

@Serializable
data class AppUpdateDto(
    val hasUpdate: Boolean = false,
    val latestVersionCode: Int = 0,
    val latestVersionName: String? = null,
    val minSupportedVersionCode: Int = 0,
    val forceUpdate: Boolean = false,
    val publishTime: String? = null,
    val apk: ApkInfoDto? = null,
    val changelog: List<String> = emptyList(),
    val upgradeTitle: String? = null,
    val upgradeContent: String? = null,
    val channel: String? = null,
    val gray: GrayReleaseDto? = null,
)

@Serializable
data class ApkInfoDto(
    val url: String = "",
    val size: Long = 0,
    val sha256: String? = null,
)

@Serializable
data class GrayReleaseDto(
    val enabled: Boolean = false,
    val percent: Int = 0,
)
