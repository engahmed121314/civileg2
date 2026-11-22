package com.civileg.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.civileg.app.R
import com.civileg.app.db.Project

/**
 * The project a save goes to: the user's pick if it is still in [projects], otherwise the first
 * project (shown selected in the list), or null when there is no project. Never a made-up id.
 */
fun effectiveSaveProjectId(selectedId: Long, projects: List<Project>): Long? =
    projects.firstOrNull { it.id == selectedId }?.id ?: projects.firstOrNull()?.id

/**
 * Save-design dialog shared by the design screens. The target project is always one the user can
 * see selected. With no project there is nothing to select and Save is disabled.
 */
@Composable
fun SaveToProjectDialog(
    title: String,
    nameLabel: String,
    noProjectsText: String,
    designName: String,
    onDesignNameChange: (String) -> Unit,
    projects: List<Project>,
    onConfirm: (projectId: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedId by rememberSaveable { mutableLongStateOf(-1L) }
    val effectiveId = effectiveSaveProjectId(selectedId, projects)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = designName,
                    onValueChange = onDesignNameChange,
                    label = { AppText(nameLabel) },
                    modifier = Modifier.fillMaxWidth()
                )
                AppText(stringResource(R.string.select_project), style = MaterialTheme.typography.labelMedium)
                if (projects.isEmpty()) {
                    AppText(noProjectsText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                } else {
                    projects.forEach { project ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = effectiveId == project.id,
                                onClick = { selectedId = project.id }
                            )
                            AppText(project.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.save),
                fillMaxWidth = false,
                enabled = effectiveId != null,
                onClick = { effectiveId?.let(onConfirm) }
            )
        },
        dismissButton = {
            AppButton(
                label = stringResource(R.string.cancel),
                onClick = onDismiss,
                style = AppButtonStyle.Text,
                fillMaxWidth = false
            )
        }
    )
}
