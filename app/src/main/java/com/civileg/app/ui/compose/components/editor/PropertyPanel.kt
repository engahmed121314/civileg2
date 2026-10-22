package com.civileg.app.ui.compose.components.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.civileg.app.domain.editor.ChangeSupportCommand
import com.civileg.app.domain.editor.DeleteMemberCommand
import com.civileg.app.domain.editor.DeleteNodeCommand
import com.civileg.app.domain.editor.MoveNodeCommand
import com.civileg.app.domain.editor.UndoManager
import com.civileg.app.domain.entities.FrameMember
import com.civileg.app.domain.entities.FrameNode
import com.civileg.app.domain.entities.SupportType
import com.civileg.app.viewmodel.FrameAnalysisViewModel

@Composable
fun PropertyPanel(
    selectedNodeId: Int?,
    selectedMemberId: Int?,
    nodes: List<FrameNode>,
    members: List<FrameMember>,
    viewModel: FrameAnalysisViewModel,
    editorState: EditorState,
    undoManager: UndoManager,
    modifier: Modifier = Modifier
) {
    val node = nodes.find { it.id == selectedNodeId }
    val member = members.find { it.id == selectedMemberId }

    if (node == null && member == null) return

    Card(
        modifier = modifier.width(260.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (node != null) "خصائص العقدة ${node.id}" else "خصائص العضو ${member?.id}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { editorState.clearSelection() }) {
                    Icon(Icons.Default.Close, contentDescription = "إغلاق")
                }
            }

            if (node != null) {
                var xStr by remember(node.id) { mutableStateOf(node.x.toString()) }
                var yStr by remember(node.id) { mutableStateOf(node.y.toString()) }

                OutlinedTextField(
                    value = xStr,
                    onValueChange = {
                        xStr = it
                        val newX = it.toDoubleOrNull()
                        if (newX != null) {
                            val cmd = MoveNodeCommand(viewModel, node.id, node.x, node.y, newX, node.y, node.support)
                            undoManager.execute(cmd)
                        }
                    },
                    label = { Text("X (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = yStr,
                    onValueChange = {
                        yStr = it
                        val newY = it.toDoubleOrNull()
                        if (newY != null) {
                            val cmd = MoveNodeCommand(viewModel, node.id, node.x, node.y, node.x, newY, node.support)
                            undoManager.execute(cmd)
                        }
                    },
                    label = { Text("Y (m)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("نوع الارتكاز:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SupportType.entries.forEach { st ->
                        FilterChip(
                            selected = node.support == st,
                            onClick = {
                                val cmd = ChangeSupportCommand(viewModel, node.id, node.x, node.y, node.support, st)
                                undoManager.execute(cmd)
                            },
                            label = { Text(st.displayName) }
                        )
                    }
                }

                Button(
                    onClick = {
                        val connected = members.filter { it.nodeI == node.id || it.nodeJ == node.id }
                        val cmd = DeleteNodeCommand(viewModel, node, connected)
                        undoManager.execute(cmd)
                        editorState.clearSelection()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(" حذف العقدة")
                }
            } else if (member != null) {
                val length = member.getLength(nodes)
                Text(text = "العقد: ${member.nodeI} -> ${member.nodeJ}", style = MaterialTheme.typography.bodySmall)
                Text(text = "الطول: ${String.format(java.util.Locale.ROOT, "%.3f", length)} m", style = MaterialTheme.typography.bodySmall)
                Text(text = "المادة: ${member.materialType.displayName}", style = MaterialTheme.typography.bodySmall)

                Button(
                    onClick = {
                        val cmd = DeleteMemberCommand(viewModel, member)
                        undoManager.execute(cmd)
                        editorState.clearSelection()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Text(" حذف العضو")
                }
            }
        }
    }
}
