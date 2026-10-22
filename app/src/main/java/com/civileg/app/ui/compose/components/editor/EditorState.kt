package com.civileg.app.ui.compose.components.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.civileg.app.domain.editor.EditorTool
import com.civileg.app.domain.entities.FrameMaterialType
import com.civileg.app.domain.entities.FrameMemberType
import com.civileg.app.domain.entities.SupportType

class EditorState {
    var activeTool by mutableStateOf(EditorTool.Select)
    var selectedNodeId by mutableStateOf<Int?>(null)
    var selectedMemberId by mutableStateOf<Int?>(null)
    var gridSnapEnabled by mutableStateOf(true)
    var isDrawingMode by mutableStateOf(true)
    var showPropertyPanel by mutableStateOf(false)

    // New member creation defaults
    var newMemberMaterialType by mutableStateOf(FrameMaterialType.Concrete)
    var newMemberType by mutableStateOf(FrameMemberType.Beam)

    // New support defaults
    var newSupportType by mutableStateOf(SupportType.Pin)

    // Interactive gesture dragging state
    var isDragging by mutableStateOf(false)
    var dragStartNodeId by mutableStateOf<Int?>(null)
    var previewLineEnd by mutableStateOf<Offset?>(null)

    fun clearSelection() {
        selectedNodeId = null
        selectedMemberId = null
        showPropertyPanel = false
    }
}
