package com.fox.music.feature.social.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.model.music.Music
import com.fox.music.core.model.music.SearchSuggest
import com.fox.music.core.ui.component.CachedImage
import com.fox.music.core.ui.component.LoadingIndicator
import com.fox.music.feature.social.viewmodel.SelectPostMusicSearchEffect
import com.fox.music.feature.social.viewmodel.SelectPostMusicSearchIntent
import com.fox.music.feature.social.viewmodel.SelectPostMusicSearchViewModel

const val SELECT_POST_MUSIC_SEARCH_ROUTE = "select_post_music_search"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectPostMusicSearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SelectPostMusicSearchViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
    onMusicSelected: (Music) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        viewModel.sendIntent(SelectPostMusicSearchIntent.FocusChanged(true))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is SelectPostMusicSearchEffect.MusicSelected -> onMusicSelected(effect.music)
                is SelectPostMusicSearchEffect.ShowToast -> {
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                }
                SelectPostMusicSearchEffect.ClearSearchFocus -> focusManager.clearFocus()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        SelectPostMusicSearchTopBar(
            query = state.query,
            focusRequester = focusRequester,
            onBack = onBack,
            onQueryChange = { viewModel.sendIntent(SelectPostMusicSearchIntent.QueryChange(it)) },
            onFocusChanged = { viewModel.sendIntent(SelectPostMusicSearchIntent.FocusChanged(it)) },
            onSearch = { viewModel.sendIntent(SelectPostMusicSearchIntent.Search) },
        )
        when {
            state.showSuggestions -> {
                SelectPostMusicSuggestList(
                    suggestions = state.suggestions,
                    onSuggestClick = {
                        viewModel.sendIntent(SelectPostMusicSearchIntent.SelectSuggest(it))
                    },
                )
            }
            state.isLoading -> LoadingIndicator(useLottie = false)
            state.hasSearched -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(state.items, key = { it.id }) { music ->
                        SelectPostMusicRow(
                            music = music,
                            onClick = { viewModel.sendIntent(SelectPostMusicSearchIntent.Select(music)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectPostMusicSearchTopBar(
    query: String,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onSearch: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回",
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        onSearch()
                        focusManager.clearFocus()
                    },
                ),
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索要分享的音乐",
                            style = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                            ),
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "清除",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        TextButton(onClick = onSearch) {
            Text("搜索")
        }
    }
}

@Composable
private fun SelectPostMusicSuggestList(
    suggestions: List<SearchSuggest>,
    onSuggestClick: (SearchSuggest) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(
            items = suggestions,
            key = { "${it.type}_${it.id}_${it.text}" },
        ) { suggest ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestClick(suggest) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CachedImage(
                    imageUrl = suggest.cover,
                    contentDescription = suggest.text,
                    modifier = Modifier.size(48.dp),
                    shape = MaterialTheme.shapes.small,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = suggest.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = suggestTypeLabel(suggest.type),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun suggestTypeLabel(type: String): String = when (type.lowercase()) {
    "music" -> "音乐"
    "artist" -> "艺人"
    "album" -> "专辑"
    else -> type.ifBlank { "搜索" }
}
