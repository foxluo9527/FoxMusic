package com.fox.music.feature.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.ErrorView
import com.fox.music.core.ui.component.LoadingIndicator

const val PROFILE_ROUTE = "profile"

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
    isLogin: Boolean,
    onLogin: () -> Unit = {},
    manageMusics:()-> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(isLogin) {
        if (isLogin) {
            viewModel.sendIntent(ProfileIntent.Load)
        }
    }
    when {
        !isLogin-> ErrorView(Modifier.fillMaxSize(), "请先登录", true, retryText = "登录", onLogin)
        state.isLoading && state.user == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.user == null -> ErrorView(message = state.error!!)
        else -> state.user?.let { user ->
            Column(modifier = modifier
                .fillMaxSize()
                .padding(24.dp)) {
                CachedImage(
                    imageUrl = user.avatar,
                    contentDescription = user.username,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(user.username, style = MaterialTheme.typography.headlineSmall)
                user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                user.signature?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(
                    Modifier
                        .padding(top = 10.dp)
                        .height(49.dp)
                        .fillMaxWidth()
                        .border(
                            1.dp, Color.Gray,
                            RoundedCornerShape(10.dp)
                        ).clickable{
                            manageMusics()
                        }.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("查看曲库", Modifier.weight(1f))

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "more",
                        Modifier.rotate(180f)
                    )
                }
            }
        }
    }
}
