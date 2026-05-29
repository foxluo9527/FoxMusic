package com.fox.music.feature.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.fox.music.feature.chat.ui.screen.MessagesScreen

@Deprecated("使用 MessagesScreen", ReplaceWith("MessagesScreen"))
@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    MessagesScreen(modifier = modifier)
}
