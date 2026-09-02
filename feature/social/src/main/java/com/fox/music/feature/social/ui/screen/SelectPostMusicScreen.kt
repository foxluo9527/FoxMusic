package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Music
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.social.viewmodel.SelectPostMusicEffect
import com.fox.music.feature.social.viewmodel.SelectPostMusicIntent
import com.fox.music.feature.social.viewmodel.SelectPostMusicTab
import com.fox.music.feature.social.viewmodel.SelectPostMusicViewModel

const val SELECT_POST_MUSIC_ROUTE = "select_post_music"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPostMusicScreen(
    modifier: Modifier = Modifier,
    viewModel: SelectPostMusicViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onMusicSelected: (Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SelectPostMusicEffect.MusicSelected -> onMusicSelected(effect.music)
                is SelectPostMusicEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            FoxTopBar(title = "选择内容", showBackButton = true, onBackClick = onBack)
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(innerPadding),
        ) {
            SelectPostMusicSearchEntry(
                onClick = onSearchClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ScrollableTabRow(
                selectedTabIndex = if (state.tab == SelectPostMusicTab.RECENT) 1 else 0,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Tab(
                    selected = state.tab == SelectPostMusicTab.FAVORITE,
                    onClick = {
                        viewModel.sendIntent(SelectPostMusicIntent.SwitchTab(SelectPostMusicTab.FAVORITE))
                    },
                    text = { Text("我喜欢的音乐") },
                )
                Tab(
                    selected = state.tab == SelectPostMusicTab.RECENT,
                    onClick = {
                        viewModel.sendIntent(SelectPostMusicIntent.SwitchTab(SelectPostMusicTab.RECENT))
                    },
                    text = { Text("最近播放") },
                )
            }
            if (state.isLoading) {
                LoadingIndicator(useLottie = false)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.items, key = { it.id }) { music ->
                        SelectPostMusicRow(
                            music = music,
                            showFavoriteIcon = state.tab != SelectPostMusicTab.FAVORITE,
                            onClick = { viewModel.sendIntent(SelectPostMusicIntent.Select(music)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SelectPostMusicSearchEntry(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索要分享的音乐",
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SelectPostMusicRow(
    music: Music,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFavoriteIcon: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CachedImage(
            imageUrl = music.coverImage,
            contentDescription = music.title,
            modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(6.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = music.artists.joinToString("/") { it.name }.ifBlank { "未知歌手" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showFavoriteIcon && music.isFavorite) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "已收藏",
                tint = Color(0xFFE53935),
            )
        }
    }
}
