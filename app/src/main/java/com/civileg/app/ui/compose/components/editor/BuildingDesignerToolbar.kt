package com.civileg.app.ui.compose.components.editor

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
import androidx.compose.ui.unit.dp

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
            Text("الأدوات:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

            ToolbarChip(
                icon = Icons.Default.Square,
                label = "قاعدة",
                isSelected = activeTool == "Footing",
                onClick = { onToolSelected("Footing") }
            )
            ToolbarChip(
                icon = Icons.Default.ViewColumn,
                label = "عمود",
                isSelected = activeTool == "Column",
                onClick = { onToolSelected("Column") }
            )
            ToolbarChip(
                icon = Icons.Default.Layers,
                label = "سقف",
                isSelected = activeTool == "Slab",
                onClick = { onToolSelected("Slab") }
            )
            ToolbarChip(
                icon = Icons.Default.Timeline,
                label = "كمر ستيل",
                isSelected = activeTool == "Steel",
                onClick = { onToolSelected("Steel") }
            )
            ToolbarChip(
                icon = Icons.Default.Delete,
                label = "مسح",
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
        label = { Text(label) },
        leadingIcon = {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    )
}
