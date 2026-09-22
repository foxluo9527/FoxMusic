package com.fox.music.core.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.ui.theme.SearchBarShape
import kotlinx.coroutines.delay

@Composable
fun HotKeywordsCarousel(
    keywords: List<HotKeyword>,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme

    LaunchedEffect(keywords) {
        currentIndex = 0
        if (keywords.isNotEmpty()) {
            while (true) {
                delay(3000)
                currentIndex = (currentIndex + 1) % keywords.size
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(
                elevation = 2.dp,
                shape = SearchBarShape,
                ambientColor = scheme.onBackground.copy(alpha = 0.08f),
                spotColor = scheme.onBackground.copy(alpha = 0.12f),
            )
            .clip(SearchBarShape)
            .background(scheme.surface)
            .border(
                width = 1.dp,
                color = scheme.outline.copy(alpha = 0.55f),
                shape = SearchBarShape,
            )
            .clickable(onClick = onSearchClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = scheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))

            if (keywords.isNotEmpty()) {
                AnimatedContent(
                    targetState = keywords[currentIndex.coerceIn(0, keywords.lastIndex)],
                    transitionSpec = {
                        (slideInVertically { it } + fadeIn()).togetherWith(
                            slideOutVertically { -it } + fadeOut()
                        ).using(SizeTransform(clip = false))
                    },
                    label = "keyword_animation",
                ) { keyword ->
                    Text(
                        text = keyword.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurface.copy(alpha = 0.72f),
                    )
                }
            } else {
                Text(
                    text = "搜索音乐、歌单、专辑...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface.copy(alpha = 0.45f),
                )
            }
        }
    }
}
