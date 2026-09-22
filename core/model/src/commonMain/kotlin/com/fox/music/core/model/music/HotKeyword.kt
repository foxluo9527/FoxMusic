package com.fox.music.core.model.music

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *    Author : 罗福林
 *    Date   : 2026/2/12
 *    Desc   :
 */
@Serializable
data class HotKeyword(
    val keyword: String,
    val type: String,
    @SerialName("search_count")
    val searchCount: Int,
)