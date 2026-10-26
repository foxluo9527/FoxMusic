package com.fox.music.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.fox.music.R
import com.fox.music.core.ui.component.FoxTripleActionDialog
import com.fox.music.realtime.BackgroundPermissionGuide

@Composable
fun BackgroundPermissionGuideDialog(
    onLater: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val context = LocalContext.current

    FoxTripleActionDialog(
        title = context.getString(R.string.background_guide_title),
        text = context.getString(R.string.background_guide_message),
        primaryText = context.getString(R.string.background_guide_go_settings),
        onPrimary = { BackgroundPermissionGuide.openBackgroundSettings(context) },
        secondaryText = context.getString(R.string.background_guide_done),
        onSecondary = {
            BackgroundPermissionGuide.markUserConfirmed(context)
            onConfirmed()
        },
        tertiaryText = context.getString(R.string.background_guide_later),
        onTertiary = onLater,
        onDismiss = onLater,
    )
}
