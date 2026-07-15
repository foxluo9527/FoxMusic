package com.fox.music.core.network.model;

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Author : 罗福林
 * Date   : 2026/2/12
 * Desc   :
 */
@Serializable
data class SearchKeywordDto(
    val keyword: String,
    val type:String,
    @SerialName("search_count")
    val searchCount: Int
)

@Serializable
data class SearchSuggestResponse(
    val keyword: String? = null,
    val suggestions: List<SearchSuggestDto> = emptyList(),
)

@Serializable
data class SearchSuggestDto(
    val id: Long = 0,
    val text: String = "",
    val type: String = "music",
    val cover: String? = null,
)
