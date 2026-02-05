package com.fox.music.feature.player.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ComponentActivity
import com.blankj.utilcode.util.ActivityUtils
import com.fox.music.core.ui.view.ManageWebView

const val MANAGE_ROUTER = "manage"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("RestrictedApi")
@Composable
fun ManageScreen(modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier) {
        TopAppBar({
            "曲库管理"
        }, navigationIcon = {
            IconButton(onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
        }, modifier = Modifier.fillMaxWidth())
        AndroidView(
            factory = {
                ManageWebView(it).apply {
                    loadUrl(apiUrl)
                    setLifecycleOwner(ActivityUtils.getTopActivity() as ComponentActivity)
                }
            }, modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}