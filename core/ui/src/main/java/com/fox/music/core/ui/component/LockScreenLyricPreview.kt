package com.fox.music.core.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.user.AppThemeIds
import com.fox.music.core.ui.theme.FoxMusicTheme

@Composable
fun LockScreenLyricPreview(
    playerState: PlayerState,
    modifier: Modifier = Modifier,
    appThemeId: String = AppThemeIds.DEFAULT,
    customThemeSeedArgb: Long = 0xFF6C63FF,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    FoxMusicTheme(
        darkTheme = darkTheme,
        appThemeId = appThemeId,
        customThemeSeedArgb = customThemeSeedArgb,
    ) {
        LockScreenLyricScreen(
            playerState = playerState,
            positionMs = playerState.position,
            onDismiss = {},
            modifier = modifier
                .fillMaxWidth()
                .height(520.dp)
                .clip(RoundedCornerShape(24.dp)),
            enableSwipeToDismiss = false,
            showSwipeHint = true,
        )
    }
}
