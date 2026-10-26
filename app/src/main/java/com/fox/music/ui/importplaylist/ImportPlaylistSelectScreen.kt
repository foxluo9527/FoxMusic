package com.fox.music.ui.importplaylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fox.music.core.ui.component.FoxTopBar
import com.fox.music.core.ui.component.FoxTopBarTextAction

const val IMPORT_PLAYLIST_SELECT_ROUTE = "import_playlist_select/{url}"

fun importPlaylistSelectRoute(encodedUrl: String): String =
    "import_playlist_select/$encodedUrl"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPlaylistSelectScreen(
    onBack: () -> Unit,
    onNavigateToPlaylist: (Long) -> Unit,
    onMessage: (String) -> Unit,
    viewModel: ImportPlaylistSelectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is ImportSelectEffect.NavigateToPlaylist -> onNavigateToPlaylist(effect.playlistId)
                is ImportSelectEffect.ShowMessage -> onMessage(effect.message)
            }
        }
    }

    Scaffold(
        topBar = {
            FoxTopBar(
                title = "选择导入歌曲",
                showBackButton = true,
                onBackClick = onBack,
                actions = {
                    FoxTopBarTextAction(
                        text = "全选",
                        onClick = { viewModel.selectAll(true) },
                    )
                    FoxTopBarTextAction(
                        text = "清空",
                        onClick = { viewModel.selectAll(false) },
                    )
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在解析歌单…")
                }
            }

            state.error != null && state.tracks.isEmpty() -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error ?: "解析失败")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack) { Text("返回") }
                }
            }

            else -> {
                Column(Modifier
                    .fillMaxSize()
                    .padding(padding)) {
                    OutlinedTextField(
                        value = state.title,
                        onValueChange = viewModel::updateTitle,
                        label = { Text("歌单名称") },
                        singleLine = true,
                        supportingText = {
                            val tip = state.error
                            if (!tip.isNullOrBlank() && state.tracks.isNotEmpty()) {
                                Text(
                                    tip,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text("同名时请修改后再导入")
                            }
                        },
                        isError = !state.error.isNullOrBlank() && state.tracks.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = "已选 ${state.selectedIndexes.size}/${state.tracks.size}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        itemsIndexed(state.tracks) { index, track ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggle(index) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = index in state.selectedIndexes,
                                    onCheckedChange = { viewModel.toggle(index) },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        track.title,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        track.artists.joinToString(", "),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = viewModel::confirm,
                        enabled = !state.confirming &&
                                state.selectedIndexes.isNotEmpty() &&
                                state.title.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(if (state.confirming) "提交中…" else "开始导入")
                    }
                }
            }
        }
    }
}
