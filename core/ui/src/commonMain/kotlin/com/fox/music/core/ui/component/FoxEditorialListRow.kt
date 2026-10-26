package com.fox.music.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fox.music.core.ui.theme.FoxEditorial
import com.fox.music.core.ui.theme.InkQuaternary
import com.fox.music.core.ui.theme.PaperHairline

/**
 * 编辑杂志风 · 无边框编辑列表行（v9 核心组件之一）。
 *
 * 去掉 v8 的"圆角卡片背景 + 投影"，改用 1px 发丝线分隔；左侧 2.5dp 强调色竖条
 * 表示"正在播放"；可选衬线序号（01/02/03）落在最左。信息即装饰。
 *
 * @param number 可选序号（字符串，如 "1"），用衬线灰显示。
 * @param leading 左侧视觉（封面 / 头像 / 图标容器），可选。
 * @param title 主标题。
 * @param subtitle 副标题（艺人 / 时间），可选。
 * @param trailing 右侧内容（时长 / 更多 / 删除），可选。
 * @param isPlaying 是否正在播放（左侧强调竖条）。
 */
@Composable
fun FoxEditorialRow(
    title: String,
    modifier: Modifier = Modifier,
    number: String? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    isPlaying: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                // 页面左右安全边距由组件自带（设计系统 20dp），调用处不要再叠加 horizontal padding，
                // 否则会变成 40dp。需要贴边时传 modifier 覆盖即可。
                .padding(horizontal = 20.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {

            if (number != null) {
                Text(
                    text = number,
                    style = FoxEditorial.listNumber,
                    color = InkQuaternary,
                    // 15sp 衬线下 "02"/"03" 双字符在 20dp 里会折行，放宽容度并强制单行
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(24.dp),
                )
            }

            if (leading != null) {
                leading()
                Spacer(Modifier.width(13.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }

            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                    content = trailing,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = PaperHairline)
    }
}
