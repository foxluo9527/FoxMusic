package com.fox.music.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 「反馈音乐不匹配」弹窗：填写问题说明后提交到后端，
 * 管理员可在管理端/客户端音源纠错页处理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicFeedbackSheet(
    targetTitle: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (content: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var content by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "反馈音乐不匹配",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = targetTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Text(
                text = "如果这首歌播放的内容与歌名/歌手不符，或音质异常，请告诉我们，我们会尽快核实修正。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("问题说明（可选）") },
                placeholder = { Text("例如：播放的是另一首歌、歌词与音频不同步等") },
                minLines = 2,
                maxLines = 5,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSubmit(content.takeIf { it.isNotBlank() })
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            ) {
                Text(if (isSubmitting) "提交中..." else "提交反馈")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
