package com.fox.music.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ReportReason(val key: String, val label: String)

val REPORT_REASONS = listOf(
    ReportReason("spam", "垃圾信息"),
    ReportReason("abuse", "辱骂/骚扰"),
    ReportReason("porn", "色情内容"),
    ReportReason("copyright", "侵权"),
    ReportReason("illegal", "违法内容"),
    ReportReason("other", "其他"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportBottomSheet(
    musicTitle: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, description: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedReason by remember { mutableStateOf(REPORT_REASONS.first().key) }
    var description by remember { mutableStateOf("") }

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
                text = "举报歌曲",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = musicTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            REPORT_REASONS.forEach { reason ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = reason.key }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedReason == reason.key,
                        onClick = { selectedReason = reason.key },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = reason.label, style = MaterialTheme.typography.bodyLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("详细说明（可选）") },
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    onSubmit(
                        selectedReason,
                        description.takeIf { it.isNotBlank() },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSubmitting,
            ) {
                Text(if (isSubmitting) "提交中..." else "提交举报")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
