package com.fox.music.navigation

import androidx.navigation.NavHostController
import com.fox.music.MainActivityViewModel
import com.fox.music.core.model.music.Album
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.PlaybackPlaylistSource
import com.fox.music.core.model.music.PlayerState
import com.fox.music.core.model.music.Playlist
import com.fox.music.core.player.controller.MusicController
import com.fox.music.ui.MusicActionsViewModel
import com.fox.music.ui.PostActionsViewModel
import com.fox.music.ui.ProfileShareActionsViewModel

data class NavGraphContext(
    val navController: NavHostController,
    val musicController: MusicController,
    val viewModel: MainActivityViewModel,
    val authState: MainActivityViewModel.AuthState,
    val playerState: PlayerState,
    val showBottomBar: Boolean,
    val messagesUnreadCount: Int,
    val currentUserId: Long?,
    val profileRefreshKey: Int,
    val plazaRefreshTrigger: Int,
    val hideMiniPlayerInSelection: Boolean,
    val musicActionsViewModel: MusicActionsViewModel,
    val postActionsViewModel: PostActionsViewModel,
    val profileShareActionsViewModel: ProfileShareActionsViewModel,
    val onProfileRefreshKeyIncrement: () -> Unit,
    val onPlazaRefreshTriggerIncrement: () -> Unit,
    val onHideMiniPlayerInSelectionChange: (Boolean) -> Unit,
    val onMusicClick: (Music, List<Music>, String, PlaybackPlaylistSource?) -> Unit,
    val onUpdateMusicList: (List<Music>, String) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val onAlbumClick: (Album) -> Unit,
    val onArtistClick: (Long) -> Unit,
    val onMusicMoreClick: (Music) -> Unit,
    val showCreatePlaylistBottomSheet: () -> Unit,
)
