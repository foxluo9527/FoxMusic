package com.fox.music.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 歌单 / 专辑 / 榜单 / 歌手头部点击后进入的「介绍」页数据。
 * 各业务模块只负责把自己的数据映射成这份纯展示模型。
 */
data class DetailIntroData(
    val title: String,
    val coverUrl: String?,
    val typeLabel: String,
    val coverFallbackUrl: String? = null,
    val coverIsCircle: Boolean = false,
    val coverPlaceholderIcon: ImageVector = Icons.Filled.MusicNote,
    val subtitle: String? = null,
    val metaItems: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val description: String? = null,
    val infoRows: List<Pair<String, String>> = emptyList(),
    val emptyDescription: String = "暂无简介",
)

/**
 * 通用介绍详情页：完整展示封面、标题、标签、简介与详细信息。
 * 各端共用同一份 UI（Android / iOS）。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailIntroContent(
    data: DetailIntroData,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewUrl by remember { mutableStateOf<String?>(null) }
    // 预览兜底链：主封面（coverUrl）失效时切 coverFallbackUrl，
    // 与上面 CachedImage 的 errorImageUrl 兜底语义对齐（库里存在主图死链的艺人）。
    var previewFallbackUrls by remember { mutableStateOf<List<String>>(emptyList()) }

    previewUrl?.let { url ->
        PostImagePreviewDialog(
            images = listOf(url),
            initialIndex = 0,
            onDismiss = { previewUrl = null },
            fallbackImages = previewFallbackUrls,
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "${data.typeLabel}介绍",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 48.dp),
        ) {
            item {
                IntroHero(
                    data = data,
                    onCoverLongClick = { url ->
                        previewUrl = url
                        previewFallbackUrls = listOfNotNull(
                            data.coverFallbackUrl?.takeIf { it.isNotBlank() && it != url },
                        )
                    },
                )
            }

            if (data.tags.isNotEmpty()) {
                item {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        data.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }

            item {
                IntroSectionTitle(text = "简介")
            }
            item {
                Text(
                    text = data.description?.takeIf { it.isNotBlank() } ?: data.emptyDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = if (data.description.isNullOrBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }

            if (data.infoRows.isNotEmpty()) {
                item {
                    IntroSectionTitle(text = "详细信息")
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(),
                    ) {
                        data.infoRows.forEach { (label, value) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(88.dp),
                                )
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntroHero(
    data: DetailIntroData,
    onCoverLongClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CachedImage(
            imageUrl = data.coverUrl,
            errorImageUrl = data.coverFallbackUrl,
            contentDescription = data.title,
            modifier = Modifier
                .size(if (data.coverIsCircle) 140.dp else 172.dp)
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                    onLongClick = {
                        (data.coverUrl ?: data.coverFallbackUrl)?.takeIf { it.isNotBlank() }
                            ?.let(onCoverLongClick)
                    },
                ),
            shape = if (data.coverIsCircle) CircleShape else RoundedCornerShape(16.dp),
            placeholderIcon = data.coverPlaceholderIcon,
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = data.typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )

        data.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (data.metaItems.isNotEmpty()) {
            Text(
                text = data.metaItems.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun IntroSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}
