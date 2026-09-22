package com.fox.music.core.model.music

data class SearchSuggest(
    val id: Long,
    val text: String,
    val type: String,
    val cover: String? = null,
)
