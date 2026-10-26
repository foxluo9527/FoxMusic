package com.fox.music.ui

import android.app.Activity
import androidx.annotation.OptIn
import com.fox.music.core.common.EventViewModel
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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
import com.fox.music.core.model.music.SleepTimerState
import com.fox.music.core.ui.component.CreatePlaylistBottomSheet
import com.fox.music.core.ui.component.FoxChoiceDialog
import com.fox.music.core.ui.component.FoxInputDialog
import com.fox.music.core.ui.component.JumpHighlightController
import com.fox.music.core.ui.component.LocalJumpHighlight
import com.fox.music.core.ui.component.LocalNowPlayingMusicId
import com.fox.music.core.ui.component.LocalTransientMediaAudioCoordinator
import com.fox.music.BuildConfig
import com.fox.music.core.ui.component.MineDrawerContent
import com.fox.music.core.ui.component.TransientMediaAudioCoordinator
import com.fox.music.core.ui.component.UpdateDialog
import com.fox.music.feature.assistant.assistantRoute
import com.fox.music.feature.auth.ui.screen.LOGIN_ROUTE
import com.fox.music.feature.chat.CHAT_DETAIL_ROUTE
import com.fox.music.feature.chat.MESSAGES_ROUTE
import com.fox.music.feature.chat.selectFriendRoute
import com.fox.music.feature.discover.artistDetailRoute
import com.fox.music.feature.home.HOME_ROUTE
import com.fox.music.feature.player.ui.screen.DesktopLyricSettingsRequest
import com.fox.music.feature.player.ui.screen.MANAGE_ROUTER
import com.fox.music.feature.player.ui.screen.PLAYER_ROUTE
import com.fox.music.feature.player.ui.screen.sourceCorrectRoute
import com.fox.music.feature.player.viewmodel.CollectTrackArgs
import com.fox.music.feature.player.viewmodel.collectTrackRoute
import com.fox.music.feature.playlist.ui.component.playlistDetailRoute
import com.fox.music.feature.profile.ui.screen.DOWNLOAD_MANAGER_ROUTE
import com.fox.music.feature.profile.ui.screen.EDIT_PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.PROFILE_ROUTE
import com.fox.music.feature.profile.ui.screen.SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.THEME_SETTINGS_ROUTE
import com.fox.music.feature.profile.ui.screen.userHomeRoute
import com.fox.music.feature.social.ui.screen.createPostRoute
import com.fox.music.navigation.NavGraphContext
import com.fox.music.navigation.appNavGraph
import com.fox.music.navigation.navigateToPlayer
import com.fox.music.ui.TAB_ROOT_ROUTES
import com.fox.music.update.ApkInstallHelper
import com.fox.music.update.ApkInstallResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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
    // 「跳转高亮」的全局信号载体：列表点「跳到正在播放」后，让目标行闪一下
    val jumpHighlight = remember { JumpHighlightController() }
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
    var showSleepTimerPicker by remember { mutableStateOf(false) }
    val postActionsState by postActionsViewModel.uiState.collectAsStateWithLifecycle()
    val currentUserId = postActionsState.currentUserId

    val playlistState by viewModel.playlistState.collectAsState()
    val messagesUnreadCount by viewModel.totalUnreadCount.collectAsStateWithLifecycle()
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val drawerProfile by viewModel.drawerProfile.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimer.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    // 抽屉展开时状态栏图标/文字变白：抽屉头部是深色封面图，深色图标压在上面看不见
    //（用户反馈）。关闭时恢复进入前的样式（各 tab 页是浅色底 → 深色图标）。
    val statusBarView = LocalView.current
    val statusBarWindow = (statusBarView.context as? Activity)?.window
    if (statusBarWindow != null) {
        DisposableEffect(drawerState.isOpen) {
            val controller = WindowCompat.getInsetsController(statusBarWindow, statusBarView)
            val previousLight = controller.isAppearanceLightStatusBars
            if (drawerState.isOpen) {
                controller.isAppearanceLightStatusBars = false
            }
            onDispose {
                controller.isAppearanceLightStatusBars = previousLight
            }
        }
    }

    fun closeDrawer(block: () -> Unit = {}) {
        drawerScope.launch { drawerState.close() }
        block()
    }

    fun navigateFromDrawer(route: String) {
        closeDrawer { navController.navigate(route) }
    }

    LaunchedEffect(deepLinkRoute) {
        if (deepLinkRoute.isBlank() || deepLinkRoute == HOME_ROUTE) return@LaunchedEffect
        // v12 修崩溃：`Cannot navigate to messages/notifications/comment.
        // Navigation graph has not been set for NavController`。
        //
        // 本 effect 注册在 [MainScreen] 主体里，比下方 NavHost 的 setGraph 更早进入
        // 副作用队列；冷启动带通知路由（点推送进 App）时 effect 先跑，此时
        // navController.graph 还是 null，navigate 直接抛 IllegalArgumentException。
        // 等首个 back stack entry 落地（= NavHost 已 setGraph）再跳，既不改导航结构，
        // 也不引入延时魔法值。
        navController.currentBackStackEntryFlow.first()
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

    // 桌面歌词悬浮窗的「设置」：无论冷启动还是回到已在前台的 App，
    // 都由这个信号把用户送进播放页，再由 PlayerScreen 展开歌词设置。
    val desktopLyricSettingsPending by DesktopLyricSettingsRequest.pending.collectAsState()
    LaunchedEffect(desktopLyricSettingsPending) {
        if (!desktopLyricSettingsPending) return@LaunchedEffect
        navController.currentBackStackEntryFlow.first()
        navController.navigate(PLAYER_ROUTE) {
            launchSingleTop = true
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

    LaunchedEffect(Unit) {
        EventViewModel.plazaPostsUpdated.collect {
            plazaRefreshTrigger++
        }
    }

    LaunchedEffect(Unit) {
        EventViewModel.postPublishCompleted.collect { message ->
            ToastUtils.showShort(message)
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
        // 第三方（网易云/QQ）歌曲不支持连续列表播放：进入单个查看/收录页，
        // 用户可在该页试听并决定是否收录到平台。
        if (music.isThirdParty) {
            navController.navigate(
                collectTrackRoute(
                        CollectTrackArgs(
                        title = music.title,
                        artist = music.artists.joinToString(", ") { it.name },
                        platform = music.sourcePlatform ?: "netease",
                        externalId = music.sourceId,
                        coverUrl = music.coverImage.orEmpty(),
                        durationMs = music.duration,
                        album = music.album?.title ?: "",
                        mode = "search",
                    )
                )
            )
            return
        }
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
        onOpenDrawer = { drawerScope.launch { drawerState.open() } },
    )

    val transientMediaAudioCoordinator = remember(musicController) {
        object : TransientMediaAudioCoordinator {
            override fun onTransientMediaPlaybackStarted() {
                musicController.beginTransientMediaPlayback()
            }

            override fun onTransientMediaPlaybackEnded() {
                musicController.endTransientMediaPlayback()
            }
        }
    }

    CompositionLocalProvider(
        LocalTransientMediaAudioCoordinator provides transientMediaAudioCoordinator,
    ) {
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            // 仿网易云侧边栏：面板只占约 86% 宽，右侧留出可见遮罩区（默认会撑满 360dp）。
            // drawerShape = RectangleShape：去掉 M3 默认的右端 16dp 圆角——
            // 用户明确反馈「外部圆角去掉」，面板贴屏幕边缘应是整整齐齐的直角。
            ModalDrawerSheet(
                modifier = Modifier.fillMaxWidth(0.86f),
                drawerShape = RectangleShape,
                // 面板背景铺满整屏（含状态栏），否则状态栏区域露出的是遮罩底色，形成一条灰色断层。
                windowInsets = WindowInsets(0.dp),
            ) {
                MineDrawerContent(
                    isLogin = authState.isLoggedIn,
                    nickname = drawerProfile?.nickname ?: userPreferences.username,
                    avatarUrl = drawerProfile?.avatar,
                    coverUrl = drawerProfile?.homepageBackground,
                    unreadCount = messagesUnreadCount,
                    onProfileClick = {
                        closeDrawer()
                        currentUserId?.let { navController.navigate(userHomeRoute(it)) }
                    },
                    onLoginClick = { navigateFromDrawer(LOGIN_ROUTE) },
                    onMessagesClick = { navigateFromDrawer(MESSAGES_ROUTE) },
                    onAssistantClick = {
                        closeDrawer()
                        if (!authState.isLoggedIn) navController.navigate(LOGIN_ROUTE)
                        else navController.navigate(assistantRoute())
                    },
                    onEditProfileClick = { navigateFromDrawer(EDIT_PROFILE_ROUTE) },
                    onSleepTimerClick = { closeDrawer { showSleepTimerPicker = true } },
                    onDownloadsClick = { navigateFromDrawer(DOWNLOAD_MANAGER_ROUTE) },
                    onThemeClick = { navigateFromDrawer(THEME_SETTINGS_ROUTE) },
                    onManageLibraryClick = { navigateFromDrawer(MANAGE_ROUTER) },
                    onSettingsClick = { navigateFromDrawer(SETTINGS_ROUTE) },
                    versionLabel = "FOXMUSIC ${BuildConfig.VERSION_NAME}",
                )
            }
        },
    ) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        SharedTransitionLayout(modifier = Modifier.padding(innerPadding)) {
            // 「正在播放」的全局只读信号：各列表页（搜索/下载/歌单详情/歌手详情）据此做
            // 高亮与「跳到正在播放」。在这里统一 provide，避免把 musicController 逐层下传。
            CompositionLocalProvider(
                LocalNowPlayingMusicId provides playerState.currentMusic?.id,
                LocalJumpHighlight provides jumpHighlight,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = HOME_ROUTE,
                    modifier = Modifier.fillMaxSize()
                ) {
                    appNavGraph(navGraphContext, this@SharedTransitionLayout)
                }
            }
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
        onNavigateToSourceCorrect = { musicId ->
            navController.navigate(sourceCorrectRoute(musicId))
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
        FoxInputDialog(
            title = inputDialogTitle,
            label = inputDialogLabel,
            initialValue = inputValue,
            confirmText = "确认",
            onDismiss = ::onInputDialogDismiss,
            onConfirm = { value ->
                inputValue = value
                onInputDialogConfirm()
            },
        )
    }

    if (showSleepTimerPicker) {
        SleepTimerPickerDialog(
            sleepTimerState = sleepTimerState,
            onSelect = { minutes ->
                viewModel.startSleepTimer(minutes)
                showSleepTimerPicker = false
            },
            onCancelTimer = {
                viewModel.cancelSleepTimer()
                showSleepTimerPicker = false
            },
            onDismiss = { showSleepTimerPicker = false },
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
}

private val SleepTimerOptionsMinutes = listOf(0L, 15L, 30L, 45L, 60L, 90L, 120L)

@Composable
private fun SleepTimerPickerDialog(
    sleepTimerState: SleepTimerState,
    onSelect: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 「取消当前定时」用 -1 标记，避免与「关闭定时」(0) 混淆
    val cancelTimerMinutes = -1L
    val optionMinutes: List<Long> =
        if (sleepTimerState.isActive) SleepTimerOptionsMinutes + cancelTimerMinutes
        else SleepTimerOptionsMinutes
    val labels = optionMinutes.map { minutes ->
        when (minutes) {
            cancelTimerMinutes -> "取消当前定时"
            0L -> "关闭定时"
            else -> "$minutes 分钟后停止播放"
        }
    }
    // 定时生效时按 totalMs 回显当前档位
    val selectedIndex = if (sleepTimerState.isActive) {
        SleepTimerOptionsMinutes.indexOfFirst { it * 60_000L == sleepTimerState.totalMs }
    } else {
        -1
    }
    FoxChoiceDialog(
        title = "定时关闭",
        options = labels,
        selectedIndex = selectedIndex,
        onSelect = { index ->
            val minutes = optionMinutes[index]
            if (minutes == cancelTimerMinutes) onCancelTimer() else onSelect(minutes)
        },
        onDismiss = onDismiss,
    )
}
