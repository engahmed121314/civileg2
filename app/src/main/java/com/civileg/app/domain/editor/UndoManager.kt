package com.civileg.app.domain.editor

class UndoManager(private val maxHistory: Int = 50) {
    private val undoStack = mutableListOf<EditorCommand>()
    private val redoStack = mutableListOf<EditorCommand>()

    fun execute(command: EditorCommand) {
        command.execute()
        undoStack.add(command)
        if (undoStack.size > maxHistory) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (undoStack.isEmpty()) return false
        val command = undoStack.removeAt(undoStack.lastIndex)
        command.undo()
        redoStack.add(command)
        return true
    }

    fun redo(): Boolean {
        if (redoStack.isEmpty()) return false
        val command = redoStack.removeAt(redoStack.lastIndex)
        command.execute()
        undoStack.add(command)
        return true
    }

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoDescription: String? get() = undoStack.lastOrNull()?.description
    val redoDescription: String? get() = redoStack.lastOrNull()?.description

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
