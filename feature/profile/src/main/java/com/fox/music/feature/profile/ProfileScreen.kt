package com.fox.music.feature.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.components.CachedImage
import com.fox.music.core.ui.components.ErrorView
import com.fox.music.core.ui.components.LoadingIndicator

const val PROFILE_ROUTE = "profile"

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    when {
        state.isLoading && state.user == null -> LoadingIndicator(useLottie = false)
        state.error != null && state.user == null -> ErrorView(message = state.error!!)
        else -> state.user?.let { user ->
            Column(modifier = modifier.fillMaxSize().padding(24.dp)) {
                CachedImage(
                    imageUrl = user.avatar,
                    contentDescription = user.username,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(user.username, style = MaterialTheme.typography.headlineSmall)
                user.email?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                user.signature?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}
