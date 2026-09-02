package com.fox.music.ui

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileShareActionsHost(
    onNavigateToSelectFriend: () -> Unit,
    viewModel: ProfileShareActionsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ProfileShareActionsEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                ProfileShareActionsEffect.NavigateToSelectFriend -> onNavigateToSelectFriend()
            }
        }
    }
}
