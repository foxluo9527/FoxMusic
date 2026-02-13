package com.fox.music.feature.home

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.fox.music.core.common.mvi.MviViewModel
import com.fox.music.core.common.mvi.UiEffect
import com.fox.music.core.common.mvi.UiIntent
import com.fox.music.core.common.mvi.UiState
import com.fox.music.core.common.result.Result
import com.fox.music.core.domain.repository.AlbumRepository
import com.fox.music.core.domain.repository.PlaylistRepository
import com.fox.music.core.domain.repository.SearchRepository
import com.fox.music.core.domain.repository.SocialRepository
import com.fox.music.core.domain.usecase.GetMusicListUseCase
import com.fox.music.core.model.Album
import com.fox.music.core.model.HotKeyword
import com.fox.music.core.model.Music
import com.fox.music.core.model.Playlist
import com.fox.music.core.model.Post
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val hotKeywords: List<HotKeyword> = emptyList(),
    val recommendedPlaylists: List<Playlist> = emptyList(),
    val recommendedMusic: Flow<PagingData<Music>> = flowOf(),
    val recommendedAlbums: List<Album> = emptyList(),
    val posts: List<Post> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) : UiState

sealed interface HomeIntent : UiIntent {
    data object Load : HomeIntent
    data object Refresh : HomeIntent
    data class OnPlaylistClick(val playlist: Playlist) : HomeIntent
    data class OnAlbumClick(val album: Album) : HomeIntent
    data class OnPostClick(val post: Post) : HomeIntent
    data class OnPostLike(val post: Post) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data class NavigateToMusic(val music: Music) : HomeEffect
    data class NavigateToPlaylist(val playlistId: Long) : HomeEffect
    data class NavigateToAlbum(val albumId: Long) : HomeEffect
    data class NavigateToPost(val postId: Long) : HomeEffect
    data object NavigateToSearch : HomeEffect
    data object NavigateToPlaylistCategory : HomeEffect
    data object NavigateToAlbumCategory : HomeEffect
    data object NavigateToSocial : HomeEffect
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getMusicListUseCase: GetMusicListUseCase,
    private val searchRepository: SearchRepository,
    private val playlistRepository: PlaylistRepository,
    private val albumRepository: AlbumRepository,
    private val socialRepository: SocialRepository,
): MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            sendIntent(HomeIntent.Load)
        }
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> loadContent(isRefresh = false)
            HomeIntent.Refresh -> loadContent(isRefresh = true)
            is HomeIntent.OnPlaylistClick -> {
                sendEffect(HomeEffect.NavigateToPlaylist(intent.playlist.id))
            }
            is HomeIntent.OnAlbumClick -> {
                sendEffect(HomeEffect.NavigateToAlbum(intent.album.id))
            }
            is HomeIntent.OnPostClick -> {
                sendEffect(HomeEffect.NavigateToPost(intent.post.id))
            }
            is HomeIntent.OnPostLike -> {
                togglePostLike(intent.post.id)
            }
        }
    }

    fun onMusicClick(music: Music) {
        sendEffect(HomeEffect.NavigateToMusic(music))
    }

    fun onSearchClick() {
        sendEffect(HomeEffect.NavigateToSearch)
    }

    fun onPlaylistMoreClick() {
        sendEffect(HomeEffect.NavigateToPlaylistCategory)
    }

    fun onSocialMoreClick() {
        sendEffect(HomeEffect.NavigateToSocial)
    }

    fun onAlbumMoreClick() {
        sendEffect(HomeEffect.NavigateToAlbumCategory)
    }

    private fun loadContent(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                updateState { copy(isRefreshing = true, error = null) }
            } else {
                updateState { copy(isLoading = true, error = null) }
            }

            // 加载热词
            when (val result = searchRepository.getHotKeywords(limit = 10)) {
                is Result.Success -> {
                    updateState { copy(hotKeywords = result.data) }
                }
                is Result.Error -> {
                    // 忽略热词加载失败
                }
                else -> {}
            }

            // 加载推荐歌单
            when (val result = playlistRepository.getRecommendedPlaylists(page = 1, limit = 8)) {
                is Result.Success -> {
                    updateState { copy(recommendedPlaylists = result.data.list) }
                }
                is Result.Error -> {
                    updateState { copy(error = result.message) }
                }
                else -> {}
            }

            // 加载推荐音乐
            updateState {
                copy(
                    recommendedMusic = getMusicListUseCase.getPagingSource(
                        limit = 5,
                        sort = "recommend"
                    ).flow.cachedIn(viewModelScope)
                )
            }

            // 加载推荐专辑
            when (val result = albumRepository.getAlbumList(page = 1, limit = 8, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(recommendedAlbums = result.data.list) }
                }
                is Result.Error -> {
                    // 忽略专辑加载失败
                }
                else -> {}
            }

            // 加载社区动态
            when (val result = socialRepository.getPosts(page = 1, limit = 10, sort = "hot")) {
                is Result.Success -> {
                    updateState { copy(posts = result.data.list) }
                }
                is Result.Error -> {
                    // 忽略动态加载失败
                }
                else -> {}
            }

            updateState { copy(isLoading = false, isRefreshing = false) }
        }
    }

    private fun togglePostLike(postId: Long) {
        viewModelScope.launch {
            when (socialRepository.togglePostLike(postId)) {
                is Result.Success -> {
                    // 更新本地状态
                    updateState {
                        copy(
                            posts = posts.map { post ->
                                if (post.id == postId) {
                                    post.copy(
                                        isLiked = !post.isLiked,
                                        likeCount = if (post.isLiked) post.likeCount - 1 else post.likeCount + 1
                                    )
                                } else {
                                    post
                                }
                            }
                        )
                    }
                }
                is Result.Error -> {
                    // 忽略点赞失败
                }
                else -> {}
            }
        }
    }
}
