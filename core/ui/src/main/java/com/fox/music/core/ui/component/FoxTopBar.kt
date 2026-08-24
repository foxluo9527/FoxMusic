package com.fox.music.core.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import com.fox.music.core.ui.theme.FoxMusicTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoxTopBar(
    title: String,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = false,
    onBackClick: () -> Unit = {},
    centerAligned: Boolean = true,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable () -> Unit = {},
) {
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    )
    if (centerAligned) {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = modifier,
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
            },
            actions = { actions() },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
            ),
            scrollBehavior = scrollBehavior,
        )
    } else {
        androidx.compose.material3.TopAppBar(
            title = {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = modifier,
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                }
            },
            actions = { actions() },
            colors = colors,
            scrollBehavior = scrollBehavior,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
private fun FoxTopBarPreview() {
    FoxMusicTheme {
        FoxTopBar(title = "Fox Music", showBackButton = true)
    }
}
