package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.civileg.app.R
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.entities.SupportType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportPicker(
    nodeId: Int,
    currentSupport: SupportType,
    onSupportSelected: (SupportType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            AppText(stringResource(R.string.ed_support_type_for_node, nodeId), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (st in SupportType.entries) {
                    val isSelected = st == currentSupport
                    Card(
                        onClick = { onSupportSelected(st) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppText(st.displayName, style = MaterialTheme.typography.bodyMedium)
                            if (isSelected) {
                                AppText("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            AppButton(label = stringResource(R.string.cancel), style = AppButtonStyle.Text, fillMaxWidth = false, onClick = onDismiss)
        }
    )
}
