package com.fox.music.feature.player.ui.component

import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import com.fox.music.core.common.R
import com.fox.music.feature.player.databinding.LayoutLyricSettingsBinding
import com.fox.music.feature.player.lyric.manager.LyricStyleManager
import com.fox.music.feature.player.lyric.manager.LyricSyncManager
import com.fox.music.feature.player.lyric.ui.LyricSettingsExt.setListener

/**
 *    Author : 罗福林
 *    Date   : 2026/2/5
 *    Desc   :
 */
@OptIn(UnstableApi::class)
@Composable
fun LyricSettings(modifier: Modifier = Modifier) {
    val lyricSyncManager = remember {LyricSyncManager.getInstance()}
    val isDesktopLyricEnabled by lyricSyncManager.isDesktopLyricEnabled.collectAsState()
    val isDesktopLyricLocked by lyricSyncManager.isDesktopLyricLocked.collectAsState()
    val context = LocalContext.current
    Column(modifier) {
        AndroidView(factory = {
            LinearLayout(it).apply {
                orientation = LinearLayout.HORIZONTAL
                LayoutLyricSettingsBinding.inflate(LayoutInflater.from(it), this).apply {
                    setListener(LyricStyleManager.getInstance())
                }
            }
        }, modifier = Modifier.fillMaxWidth())
        Row(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.clickable {
                    lyricSyncManager.toggleLock()
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("桌面歌词")
                if (isDesktopLyricEnabled) {
                    if (isDesktopLyricLocked) {
                        Image(
                            painter = painterResource(R.drawable.ic_lock),
                            contentDescription = null,
                            Modifier.size(14.dp)
                        )
                    } else {
                        Image(
                            painter = painterResource(R.drawable.ic_unlock),
                            contentDescription = null,
                            Modifier.size(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Switch(
                checked = isDesktopLyricEnabled,
                onCheckedChange = {
                    if (it) {
                        lyricSyncManager.openDesktopLyric(context)
                    } else {
                        lyricSyncManager.toggleDesktopLyric()
                    }
                }
            )
        }
    }
}