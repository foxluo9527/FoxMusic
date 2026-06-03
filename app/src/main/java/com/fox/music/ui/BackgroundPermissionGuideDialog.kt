package com.fox.music.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.fox.music.R
import com.fox.music.realtime.BackgroundPermissionGuide

@Composable
fun BackgroundPermissionGuideDialog(
    onLater: () -> Unit,
    onConfirmed: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text(text = context.getString(R.string.background_guide_title))
        },
        text = {
            Text(
                text = context.getString(R.string.background_guide_message),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    BackgroundPermissionGuide.openBackgroundSettings(context)
                },
            ) {
                Text(context.getString(R.string.background_guide_go_settings))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onLater) {
                    Text(context.getString(R.string.background_guide_later))
                }
                TextButton(
                    onClick = {
                        BackgroundPermissionGuide.markUserConfirmed(context)
                        onConfirmed()
                    },
                ) {
                    Text(context.getString(R.string.background_guide_done))
                }
            }
        },
    )
}
