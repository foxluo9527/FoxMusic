package com.fox.music.feature.profile.ui.screen

const val USER_HOME_ROUTE = "user/home/{userId}"
const val PRIVACY_SETTINGS_ROUTE = "settings/privacy"

fun userHomeRoute(userId: Long): String = "user/home/$userId"
