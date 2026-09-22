package com.fox.music.feature.discover

/** 发现页相关路由。放在 commonMain，iOS 端共用同一套路由字符串。 */
const val DISCOVER_ROUTE = "discover"
const val ALL_ARTIST_LIST_ROUTE = "all_artist_list"
const val HOT_ARTIST_LIST_ROUTE = "hot_artist_list"
const val ARTIST_DETAIL_ROUTE = "artist_detail/{artistId}"

fun artistDetailRoute(artistId: Long) = "artist_detail/$artistId"
