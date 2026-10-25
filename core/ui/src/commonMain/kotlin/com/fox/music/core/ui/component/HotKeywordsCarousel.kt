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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fox.music.core.model.music.HotKeyword
import com.fox.music.core.ui.theme.SearchFieldShape
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

    // 40dp 高、13dp 圆角：与首页搜索框同一套（不再是一颗抢眼的 24dp 胶囊 + 投影）。
    // 层级靠发丝描边，而不是靠阴影——纸感底上投影只会让控件"浮"起来、变廉价。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(SearchFieldShape)
            .background(scheme.surface)
            .border(
                width = 1.dp,
                color = scheme.onSurface.copy(alpha = 0.07f),
                shape = SearchFieldShape,
            )
            .clickable(onClick = onSearchClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "搜索",
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(9.dp))

            if (keywords.isNotEmpty()) {
                AnimatedContent(
                    targetState = keywords[currentIndex.coerceIn(0, keywords.lastIndex)],
                    transitionSpec = {
                        // clip = true：不裁剪的话，滑出的上一个热词会画到搜索框上缘之外，
                        // 在页面顶部留下一截被切一半的残字（如「许嵩」）
                        (slideInVertically { it } + fadeIn()).togetherWith(
                            slideOutVertically { -it } + fadeOut()
                        ).using(SizeTransform(clip = true))
                    },
                    label = "keyword_animation",
                ) { keyword ->
                    Text(
                        text = keyword.keyword,
                        fontSize = 13.sp,
                        color = scheme.onSurface.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                Text(
                    text = "搜索音乐、歌单、专辑...",
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
