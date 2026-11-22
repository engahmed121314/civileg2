package com.civileg.app.ui.compose.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.civileg.app.R
import com.civileg.app.db.Project
import com.civileg.app.domain.entities.HordiSlabDesignSnapshot
import com.civileg.app.ui.components.AppButton
import com.civileg.app.ui.components.AppButtonStyle
import com.civileg.app.ui.components.AppText

/** A completed calculation may be stored without approving construction or quantities. */
@Composable
internal fun HordiSnapshotSaveDialog(
    snapshot: HordiSlabDesignSnapshot,
    projects: List<Project>,
    isSaving: Boolean,
    errorMessage: String? = null,
    onDismiss: () -> Unit,
    onSave: (projectId: Long, name: String) -> Unit
) {
    val defaultName = stringResource(R.string.hordi_slab_title)
    var name by remember(snapshot.revisionSha256) { mutableStateOf(defaultName) }
    var selectedProjectId by remember(snapshot.revisionSha256) { mutableStateOf<Long?>(null) }
    var expanded by remember { mutableStateOf(false) }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { AppText(stringResource(R.string.hordi_save_calculation_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppText(stringResource(R.string.hordi_save_calculation_note),
                    style = MaterialTheme.typography.bodySmall)
                AppText(stringResource(R.string.hordi_save_revision, snapshot.revisionSha256.take(12)),
                    style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { AppText(stringResource(R.string.hordi_save_name)) },
                    singleLine = true,
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth().testTag("hordi-save-name")
                )
                if (projects.isEmpty()) {
                    AppText(stringResource(R.string.no_projects_available),
                        color = MaterialTheme.colorScheme.error)
                } else {
                    Box {
                        AppButton(
                            label = selectedProject?.name ?: stringResource(R.string.select_project),
                            onClick = { expanded = true },
                            enabled = !isSaving,
                            modifier = Modifier.fillMaxWidth().testTag("hordi-save-project-picker"),
                            style = AppButtonStyle.Outline
                        )
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                            modifier = Modifier.heightIn(max = 260.dp)) {
                            projects.forEach { project ->
                                DropdownMenuItem(
                                    text = { AppText(project.name) },
                                    modifier = Modifier.testTag("hordi-save-project-${project.id}"),
                                    onClick = { selectedProjectId = project.id; expanded = false }
                                )
                            }
                        }
                    }
                }
                if (!errorMessage.isNullOrBlank()) {
                    AppText(errorMessage, modifier = Modifier.testTag("hordi-save-error"),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            AppButton(
                label = stringResource(R.string.save),
                enabled = !isSaving && selectedProject != null && name.isNotBlank(),
                loading = isSaving,
                onClick = { selectedProject?.let { onSave(it.id, name.trim()) } },
                modifier = Modifier.testTag("hordi-save-confirm"),
                fillMaxWidth = false,
                style = AppButtonStyle.Text
            )
        },
        dismissButton = {
            AppButton(label = stringResource(R.string.cancel),
                onClick = onDismiss, enabled = !isSaving, fillMaxWidth = false,
                modifier = Modifier.testTag("hordi-save-cancel"),
                style = AppButtonStyle.Text)
        }
    )
}
