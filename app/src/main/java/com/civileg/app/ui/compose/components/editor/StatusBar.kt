package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun StatusBar(
    cursorWorldX: Double,
    cursorWorldY: Double,
    mapper: CoordinateMapper,
    editorState: EditorState,
    nodesCount: Int,
    membersCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "X: ${String.format(Locale.ROOT, "%.3f", cursorWorldX)} m  Y: ${String.format(Locale.ROOT, "%.3f", cursorWorldY)} m",
                style = MaterialTheme.typography.labelSmall
            )
            val gridLabel = if (mapper.gridSpacing < 1.0) {
                "${(mapper.gridSpacing * 1000).toInt()} mm"
            } else {
                "${mapper.gridSpacing} m"
            }
            Text(
                text = "Zoom: ${String.format(Locale.ROOT, "%.1f", mapper.cameraScale)}× | Grid: $gridLabel",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Joints: $nodesCount | Members: $membersCount | ${editorState.activeTool.displayName}",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
