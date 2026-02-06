package com.fox.music.feature.player.ui.screen

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ComponentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.blankj.utilcode.util.ActivityUtils
import com.fox.music.core.ui.view.ManageWebView
import com.fox.music.feature.player.viewmodel.ManageViewModel

const val MANAGE_ROUTER = "manage"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("RestrictedApi")
@Composable
fun ManageScreen(modifier: Modifier = Modifier,viewModel: ManageViewModel= hiltViewModel(), onBack: () -> Unit) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth()) {
            IconButton(onBack,modifier= Modifier.padding(start = 16.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Navigate back"
                )
            }
            Text("曲库管理", Modifier.align(Alignment.Center))
        }
        AndroidView(
            factory = {
                ManageWebView(it, tokenManager = viewModel.tokenManager).apply {
                    loadUrl(apiUrl)
                    setLifecycleOwner(ActivityUtils.getTopActivity() as ComponentActivity)
                }
            }, modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}