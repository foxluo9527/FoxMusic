package com.fox.music.core.ui.theme

import androidx.compose.ui.graphics.Color

object FoxSemanticColors {
    val playerLightBackground = PaperBg
    val playerDarkBackground = VinylInk
    val playerOverlay = Color.Black.copy(alpha = 0.35f)
    val glassSurface = Color.White.copy(alpha = 0.08f)
    val glassBorder = Color.White.copy(alpha = 0.12f)
    val amberGlow = VinylAmber.copy(alpha = 0.25f)

    val chatMessage = Color(0xFFE8A838)
    val chatComment = Color(0xFFD4784A)
    val chatLike = Color(0xFFE8A838)
    val chatSystem = Color(0xFF7CB342)

    val reportPending = Color(0xFFE8A838)
    val reportResolved = Color(0xFF7CB342)
    val reportRejected = Color(0xFFC96B6B)
}
