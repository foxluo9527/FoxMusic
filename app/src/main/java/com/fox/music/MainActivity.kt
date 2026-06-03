package com.fox.music

import android.app.ComponentCaller
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.rememberNavController
import com.fox.music.core.model.user.DarkMode
import com.fox.music.feature.chat.chatDetailRoute
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.notification.FoxNotificationManager
import com.fox.music.ui.MainScreen
import com.fox.music.ui.theme.FoxMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startRoute by lazy {
        mutableStateOf(HOME_ROUTE)
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        resolveStartRoute(intent)?.let { startRoute.value = it }
        enableEdgeToEdge()
        setContent {
            FoxMusicApp(startRoute)
        }
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        setIntent(intent)
        resolveStartRoute(intent)?.let { startRoute.value = it }
    }

    private fun resolveStartRoute(intent: Intent?): String? {
        intent?.getLongExtra(FoxNotificationManager.EXTRA_PEER_USER_ID, 0L)
            ?.takeIf { it > 0 }
            ?.let { return chatDetailRoute(it) }
        intent?.getStringExtra(FoxNotificationManager.EXTRA_START_ROUTE)?.takeIf { it.isNotBlank() }?.let {
            return it
        }
        return intent?.getStringExtra("start_route")?.takeIf { it.isNotBlank() }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
fun FoxMusicApp(startRoute: MutableState<String>) {
    val viewModel: MainActivityViewModel = hiltViewModel()
    val userPreferences by viewModel.userPreferences.collectAsState()
    val darkTheme = when (userPreferences.darkMode) {
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
        DarkMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    val navController = rememberNavController()
    FoxMusicTheme(darkTheme = darkTheme) {
        MainScreen(
            modifier = Modifier.fillMaxSize(),
            navController = navController,
            viewModel = viewModel,
            deepLinkRoute = startRoute.value,
        )
    }
}
