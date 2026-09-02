package com.fox.music.ui

import androidx.annotation.OptIn
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import com.blankj.utilcode.util.ToastUtils
import com.fox.music.MainActivityViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.DetailType
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.ui.component.CreatePlaylistBottomSheet
import com.fox.music.core.ui.component.UpdateDialog
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.chat.CHAT_DETAIL_ROUTE
import com.fox.music.feature.chat.selectFriendRoute
import com.fox.music.feature.discover.artistDetailRoute
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.navigation.NavGraphContext
import com.fox.music.navigation.appNavGraph
import com.fox.music.navigation.navigateToPlayer
import com.fox.music.ui.TAB_ROOT_ROUTES
import com.fox.music.update.ApkInstallHelper
import com.fox.music.update.ApkInstallResult

@OptIn(UnstableApi::class)
@Composable
fun MainScreen(
    modifier: Modifier,
    navController: NavHostController,
    viewModel: MainActivityViewModel,
    deepLinkRoute: String = HOME_ROUTE,
    musicActionsViewModel: MusicActionsViewModel = hiltViewModel(),
    postActionsViewModel: PostActionsViewModel = hiltViewModel(),
    profileShareActionsViewModel: ProfileShareActionsViewModel = hiltViewModel(),
) {
    val musicController = viewModel.musicController
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val playerState by musicController.playerState.collectAsStateWithLifecycle(PlayerState())
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val showBottomBar = remember(playerState.currentMusic) {
        playerState.currentMusic != null
    }

    var showCreatePlaylistBottomSheet by remember { mutableStateOf(false) }
    var showInputDialog by remember { mutableStateOf(false) }
    var inputDialogType by remember { mutableStateOf("create") }
    var inputValue by remember { mutableStateOf("") }
    var inputDialogTitle by remember { mutableStateOf("") }
    var inputDialogLabel by remember { mutableStateOf("") }
    var profileRefreshKey by remember { mutableIntStateOf(0) }
    var plazaRefreshTrigger by remember { mutableIntStateOf(0) }
    var hideMiniPlayerInSelection by remember { mutableStateOf(false) }
    val postActionsState by postActionsViewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = postActionsState.currentUserId

    val playlistState by viewModel.playlistState.collectAsState()
    val messagesUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()

    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute.isNotBlank() && deepLinkRoute != HOME_ROUTE) {
            navController.navigate(deepLinkRoute) {
                if (deepLinkRoute in TAB_ROOT_ROUTES) {
                    popUpTo(HOME_ROUTE) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                } else {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(authState.requireReLogin) {
        if (authState.requireReLogin) {
            ToastUtils.showLong("登录已失效，请重新登录")
            navController.navigate(LOGIN_ROUTE) {
                popUpTo(HOME_ROUTE) { inclusive = false }
                launchSingleTop = true
            }
            viewModel.onRequireReLoginHandled()
        }
    }

    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        val entry = currentEntry ?: return@LaunchedEffect
        val friendId = entry.savedStateHandle.get<Long>("selectedFriendId") ?: return@LaunchedEffect
        entry.savedStateHandle.remove<Long>("selectedFriendId")
        entry.savedStateHandle.remove<String>("selectedFriendNickname")
        entry.savedStateHandle.remove<String>("selectedFriendAvatar")

        when {
            musicActionsViewModel.uiState.value.showSelectFriend -> {
                musicActionsViewModel.sendShareMusic(friendId)
            }
            postActionsViewModel.uiState.value.showSelectFriend -> {
                postActionsViewModel.sendSharePost(friendId)
            }
            profileShareActionsViewModel.uiState.value.showSelectFriend -> {
                profileShareActionsViewModel.sendShareProfile(friendId)
            }
            else -> {
                try {
                    val chatDetailEntry = navController.getBackStackEntry(CHAT_DETAIL_ROUTE)
                    chatDetailEntry.savedStateHandle["forwardFriendId"] = friendId
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(updateState.installFile) {
        val apkFile = updateState.installFile ?: return@LaunchedEffect
        val result = ApkInstallHelper.tryInstall(context, apkFile)
        viewModel.onInstallAttemptFinished(
            launched = result is ApkInstallResult.Launched,
            file = apkFile,
        )
    }

    DisposableEffect(lifecycleOwner, updateState.pendingInstallApk) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val pending = viewModel.updateState.value.pendingInstallApk ?: return@LifecycleEventObserver
                if (!ApkInstallHelper.canInstallPackages(context)) return@LifecycleEventObserver
                val result = ApkInstallHelper.tryInstall(context, pending)
                viewModel.onInstallAttemptFinished(
                    launched = result is ApkInstallResult.Launched,
                    file = pending,
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(updateState.pendingInstallApk) {
        if (updateState.pendingInstallApk != null) {
            ToastUtils.showShort("请允许安装权限后返回应用继续安装")
        }
    }

    LaunchedEffect(updateState.noUpdateMessage) {
        updateState.noUpdateMessage?.let { message ->
            ToastUtils.showShort(message)
            viewModel.onNoUpdateMessageHandled()
        }
    }

    LaunchedEffect(playlistState) {
        if (playlistState.isPlaylistCreated || playlistState.isPlaylistImported) {
            viewModel.resetPlaylistState()
            navController.navigate(PROFILE_ROUTE) {
                popUpTo(PROFILE_ROUTE) {
                    inclusive = true
                }
            }
        } else if (playlistState.error != null) {
            ToastUtils.showLong(playlistState.error)
        }
    }

    fun onMusicClick(
        music: Music,
        list: List<Music>,
        key: String,
        source: PlaybackPlaylistSource? = null,
    ) {
        val playlist = list.takeIf { it.isNotEmpty() } ?: listOf(music)
        val startIndex = playlist.indexOfFirst { it.id == music.id }.coerceAtLeast(0)
        musicController.setPlaylist(playlist, startIndex, key, source)
        navController.navigateToPlayer()
    }

    fun onUpdateMusicList(musicList: List<Music>, key: String) {
        musicController.updatePlaylist(musicList, key)
    }

    fun onPlaylistClick(playlist: Playlist) {
        navController.navigate(playlistDetailRoute(playlist.id))
    }

    fun onAlbumClick(album: Album) {
        navController.navigate(playlistDetailRoute(album.id, DetailType.ALBUM))
    }

    fun onArtistClick(artistId: Long) {
        navController.navigate(artistDetailRoute(artistId))
    }

    fun onMusicMoreClick(music: Music) {
        musicActionsViewModel.showMusicActions(music)
    }

    fun showCreatePlaylistBottomSheet() {
        showCreatePlaylistBottomSheet = true
    }

    fun onCreateCustomPlaylist() {
        showCreatePlaylistBottomSheet = false
        inputDialogType = "create"
        inputDialogTitle = "创建自定义歌单"
        inputDialogLabel = "歌单名称"
        inputValue = ""
        showInputDialog = true
    }

    fun onImportThirdPartyPlaylist() {
        showCreatePlaylistBottomSheet = false
        inputDialogType = "import"
        inputDialogTitle = "导入第三方歌单"
        inputDialogLabel = "歌单链接"
        inputValue = ""
        showInputDialog = true
    }

    fun onInputDialogConfirm() {
        if (inputValue.isNotBlank()) {
            if (inputDialogType == "create") {
                viewModel.createPlaylist(inputValue)
            } else {
                val encoded = java.net.URLEncoder.encode(inputValue.trim(), Charsets.UTF_8.name())
                navController.navigate(com.fox.music.ui.importplaylist.importPlaylistSelectRoute(encoded))
            }
            showInputDialog = false
            inputValue = ""
        }
    }

    fun onInputDialogDismiss() {
        showInputDialog = false
        inputValue = ""
    }

    val navGraphContext = NavGraphContext(
        navController = navController,
        musicController = musicController,
        viewModel = viewModel,
        authState = authState,
        playerState = playerState,
        showBottomBar = showBottomBar,
        messagesUnreadCount = messagesUnreadCount,
        currentUserId = currentUserId,
        profileRefreshKey = profileRefreshKey,
        plazaRefreshTrigger = plazaRefreshTrigger,
        hideMiniPlayerInSelection = hideMiniPlayerInSelection,
        musicActionsViewModel = musicActionsViewModel,
        postActionsViewModel = postActionsViewModel,
        profileShareActionsViewModel = profileShareActionsViewModel,
        onProfileRefreshKeyIncrement = { profileRefreshKey++ },
        onPlazaRefreshTriggerIncrement = { plazaRefreshTrigger++ },
        onHideMiniPlayerInSelectionChange = { hideMiniPlayerInSelection = it },
        onMusicClick = ::onMusicClick,
        onUpdateMusicList = ::onUpdateMusicList,
        onPlaylistClick = ::onPlaylistClick,
        onAlbumClick = ::onAlbumClick,
        onArtistClick = ::onArtistClick,
        onMusicMoreClick = ::onMusicMoreClick,
        showCreatePlaylistBottomSheet = ::showCreatePlaylistBottomSheet,
    )

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        SharedTransitionLayout(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = HOME_ROUTE,
                modifier = Modifier.fillMaxSize()
            ) {
                appNavGraph(navGraphContext, this@SharedTransitionLayout)
            }
        }
    }

    InAppNotificationHost(navController = navController)

    MusicActionsHost(
        onArtistClick = ::onArtistClick,
        onCreatePlaylist = ::showCreatePlaylistBottomSheet,
        onNavigateToSelectFriend = {
            navController.navigate(selectFriendRoute())
        },
        viewModel = musicActionsViewModel,
    )

    PostActionsHost(
        onNavigateToSelectFriend = {
            navController.navigate(selectFriendRoute())
        },
        onNavigateToEditPost = { postId ->
            navController.navigate(createPostRoute(postId))
        },
        onPostDeleted = {
            plazaRefreshTrigger++
            if (navController.currentDestination?.route?.startsWith("post_detail") == true) {
                navController.popBackStack()
            }
        },
        viewModel = postActionsViewModel,
    )

    ProfileShareActionsHost(
        onNavigateToSelectFriend = {
            navController.navigate(selectFriendRoute())
        },
        viewModel = profileShareActionsViewModel,
    )

    if (showCreatePlaylistBottomSheet) {
        CreatePlaylistBottomSheet(
            onDismiss = { showCreatePlaylistBottomSheet = false },
            onCreateCustom = ::onCreateCustomPlaylist,
            onImportThirdParty = ::onImportThirdPartyPlaylist
        )
    }

    if (showInputDialog) {
        AlertDialog(
            onDismissRequest = ::onInputDialogDismiss,
            title = { Text(inputDialogTitle) },
            text = {
                OutlinedTextField(
                    value = inputValue,
                    onValueChange = { inputValue = it },
                    label = { Text(inputDialogLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = ::onInputDialogConfirm,
                    enabled = inputValue.isNotBlank()
                ) {
                    Text("确认")
                }
            },
            dismissButton = {
                TextButton(onClick = ::onInputDialogDismiss) {
                    Text("取消")
                }
            }
        )
    }

    val updateInfo = updateState.updateInfo
    if (updateState.showDialog && updateInfo != null) {
        UpdateDialog(
            updateInfo = updateInfo,
            forceUpdate = updateState.forceUpdate,
            isDownloading = updateState.isDownloading,
            downloadProgress = updateState.downloadProgress,
            downloadIndeterminate = updateState.downloadIndeterminate,
            downloadStatusText = updateState.downloadStatusText,
            error = updateState.error,
            onDismiss = viewModel::onDismissUpdate,
            onConfirmUpdate = viewModel::onConfirmUpdate,
            onRetry = viewModel::retryDownload,
        )
    }
}
