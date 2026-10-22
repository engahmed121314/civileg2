package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.editor.EditorTool
import com.civileg.app.domain.editor.UndoManager
import com.civileg.app.domain.entities.FrameNode

@Composable
fun EditorToolbar(
    editorState: EditorState,
    undoManager: UndoManager,
    cameraState: CameraState,
    nodes: List<FrameNode>,
    pixelsPerMeter: Float,
    canvasW: Float,
    canvasH: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolButton(EditorTool.Select, editorState)
            ToolButton(EditorTool.AddNode, editorState)
            ToolButton(EditorTool.DrawMember, editorState)
            ToolButton(EditorTool.AddSupport, editorState)
            ToolButton(EditorTool.AddNodalLoad, editorState)
            ToolButton(EditorTool.AddMemberLoad, editorState)
            ToolButton(EditorTool.Delete, editorState)
            ToolButton(EditorTool.Pan, editorState)

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 2.dp)
            )

            // Undo / Redo
            IconButton(
                onClick = { undoManager.undo() },
                enabled = undoManager.canUndo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Undo, contentDescription = "تراجع")
            }
            IconButton(
                onClick = { undoManager.redo() },
                enabled = undoManager.canRedo,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Redo, contentDescription = "إعادة")
            }

            Divider(
                modifier = Modifier
                    .height(24.dp)
                    .width(1.dp)
                    .padding(horizontal = 2.dp)
            )

            // Snap toggle
            IconButton(
                onClick = { editorState.gridSnapEnabled = !editorState.gridSnapEnabled },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (editorState.gridSnapEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                    contentDescription = "التقاط الشبكة",
                    tint = if (editorState.gridSnapEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                )
            }

            // Fit screen
            IconButton(
                onClick = { cameraState.fitAll(nodes, pixelsPerMeter, canvasW, canvasH) },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.FitScreen, contentDescription = "تحديد الكل")
            }
        }
    }
}

@Composable
private fun ToolButton(tool: EditorTool, editorState: EditorState) {
    val isSelected = editorState.activeTool == tool
    FilledIconButton(
        onClick = { editorState.activeTool = tool },
        modifier = Modifier.size(36.dp),
        colors = if (isSelected) {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        val icon = when (tool.iconName) {
            "near_me" -> Icons.Default.NearMe
            "add_circle" -> Icons.Default.AddCircle
            "timeline" -> Icons.Default.Timeline
            "foundation" -> Icons.Default.Foundation
            "arrow_downward" -> Icons.Default.ArrowDownward
            "linear_scale" -> Icons.Default.LinearScale
            "delete" -> Icons.Default.Delete
            else -> Icons.Default.PanTool
        }
        Icon(imageVector = icon, contentDescription = tool.displayName)
    }
}
