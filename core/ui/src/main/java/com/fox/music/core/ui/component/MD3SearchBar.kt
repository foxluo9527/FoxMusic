package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material Design 3 搜索框组件
 *
 * @param query 当前搜索文本
 * @param onQueryChange 搜索文本改变回调
 * @param onSearch 搜索执行回调
 * @param modifier Modifier
 * @param placeholder 提示文本
 * @param suggestions 搜索建议列表
 * @param onSuggestionClick 建议项点击回调
 * @param variant 搜索框样式变体
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "搜索...",
    suggestions: List<SearchSuggestion> = emptyList(),
    onSuggestionClick: (SearchSuggestion) -> Unit = {},
    variant: SearchBarVariant = SearchBarVariant.Filled,
) {
    val focusRequester = remember {FocusRequester()}
    val focusManager = LocalFocusManager.current
    val interactionSource = remember {MutableInteractionSource()}
    val isFocused by interactionSource.collectIsFocusedAsState()

    val showSuggestions by remember(
        query,
        isFocused,
        suggestions
    ) {
        derivedStateOf {
            isFocused && query.isNotEmpty() && suggestions.isNotEmpty()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        // 搜索框主体
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = when(variant) {
                SearchBarVariant.Filled -> MaterialTheme.colorScheme.surface
                SearchBarVariant.Outlined -> Color.Transparent
                SearchBarVariant.Tonal -> MaterialTheme.colorScheme.surfaceVariant
            },
            tonalElevation = if (variant == SearchBarVariant.Filled) 1.dp else 0.dp,
            border = if (variant == SearchBarVariant.Outlined) {
                androidx.compose.foundation.BorderStroke(
                    width = if (isFocused) 2.dp else 1.dp,
                    color = if (isFocused)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.outline
                )
            } else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 搜索图标
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                // 输入框
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    interactionSource = interactionSource,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 16.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearch(query)
                            focusManager.clearFocus()
                        }
                    ),
                    decorationBox = {innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = TextStyle(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp
                                )
                            )
                        }
                        innerTextField()
                    }
                )

                // 清除按钮
                AnimatedVisibility(
                    visible = query.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = {
                            onQueryChange("")
                            focusRequester.requestFocus()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 搜索建议列表
        AnimatedVisibility(
            visible = showSuggestions,
            enter = expandVertically(
                animationSpec = tween(300),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(300)),
            exit = shrinkVertically(
                animationSpec = tween(300),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(300))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                ) {
                    items(suggestions) {suggestion ->
                        SuggestionItem(
                            suggestion = suggestion,
                            onClick = {
                                onSuggestionClick(suggestion)
                                focusManager.clearFocus()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 搜索建议项
 */
@Composable
private fun SuggestionItem(
    suggestion: SearchSuggestion,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = suggestion.icon ?: Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = suggestion.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 搜索建议数据类
 */
data class SearchSuggestion(
    val text: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
)

/**
 * 搜索框样式变体
 */
enum class SearchBarVariant {
    Filled,    // 填充样式（默认）
    Outlined,  // 轮廓样式
    Tonal      // 色调样式
}

// ============= 预览 =============

@Preview(showBackground = true)
@Composable
private fun SearchBarPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            var query1 by remember {mutableStateOf("")}
            var query2 by remember {mutableStateOf("")}
            var query3 by remember {mutableStateOf("")}

            val sampleSuggestions = listOf(
                SearchSuggestion("Material Design 3"),
                SearchSuggestion("搜索框组件"),
                SearchSuggestion("UI 设计模式"),
                SearchSuggestion("Android Compose")
            )

            Text(
                text = "Material Design 3 搜索框",
                style = MaterialTheme.typography.headlineSmall
            )

            // 填充样式
            Text(
                text = "标准填充样式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SearchBar(
                query = query1,
                onQueryChange = {query1 = it},
                onSearch = { /* 处理搜索 */},
                suggestions = if (query1.isNotEmpty()) sampleSuggestions else emptyList(),
                onSuggestionClick = {query1 = it.text},
                variant = SearchBarVariant.Filled
            )

            // 轮廓样式
            Text(
                text = "轮廓样式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SearchBar(
                query = query2,
                onQueryChange = {query2 = it},
                onSearch = { /* 处理搜索 */},
                suggestions = if (query2.isNotEmpty()) sampleSuggestions else emptyList(),
                onSuggestionClick = {query2 = it.text},
                variant = SearchBarVariant.Outlined
            )

            // 色调样式
            Text(
                text = "色调样式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SearchBar(
                query = query3,
                onQueryChange = {query3 = it},
                onSearch = { /* 处理搜索 */},
                variant = SearchBarVariant.Tonal
            )
        }
    }
}

@Preview(showBackground = true, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchBarDarkPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            var query by remember {mutableStateOf("搜索")}

            Text(
                text = "暗色主题",
                style = MaterialTheme.typography.headlineSmall
            )

            SearchBar(
                query = query,
                onQueryChange = {query = it},
                onSearch = { /* 处理搜索 */},
                suggestions = listOf(
                    SearchSuggestion("Material Design 3"),
                    SearchSuggestion("暗色主题")
                ),
                onSuggestionClick = {query = it.text}
            )
        }
    }
}
