package com.civileg.app.ui.compose.components.editor

import com.civileg.app.ui.components.AppText

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.civileg.app.R

@Composable
fun BuildingDesignerToolbar(
    activeTool: String,
    onToolSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppText(stringResource(R.string.bd_tools_label) + ":", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            ToolbarChip(
                icon = Icons.Default.Square,
                label = stringResource(R.string.bd_tool_footing),
                isSelected = activeTool == "Footing",
                onClick = { onToolSelected("Footing") }
            )
            ToolbarChip(
                icon = Icons.Default.ViewColumn,
                label = stringResource(R.string.bd_tool_column),
                isSelected = activeTool == "Column",
                onClick = { onToolSelected("Column") }
            )
            ToolbarChip(
                icon = Icons.Default.Layers,
                label = stringResource(R.string.bd_tool_slab),
                isSelected = activeTool == "Slab",
                onClick = { onToolSelected("Slab") }
            )
            ToolbarChip(
                icon = Icons.Default.Timeline,
                label = stringResource(R.string.bd_tool_steel),
                isSelected = activeTool == "Steel",
                onClick = { onToolSelected("Steel") }
            )
            ToolbarChip(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.bd_tool_erase),
                isSelected = activeTool == "Erase",
                onClick = { onToolSelected("Erase") }
            )
        }
    }
}

@Composable
private fun ToolbarChip(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { AppText(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    )
}
