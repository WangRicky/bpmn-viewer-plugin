package com.rickytech.bpmn.viewer.edit

/**
 * 命令栈，维护 undo / redo 与脏状态。
 */
class CommandStack(private val maxSize: Int = 50) {
    private val undoStack = mutableListOf<EditCommand>()
    private val redoStack = mutableListOf<EditCommand>()
    private var savePoint: Int = 0

    var onStateChanged: (() -> Unit)? = null

    val isModified: Boolean
        get() = undoStack.size != savePoint

    fun execute(cmd: EditCommand, model: EditableModel) {
        cmd.execute(model)
        undoStack.add(cmd)
        redoStack.clear()
        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
            savePoint = (savePoint - 1).coerceAtLeast(0)
        }
        onStateChanged?.invoke()
    }

    fun undo(model: EditableModel) {
        if (!canUndo()) return
        val cmd = undoStack.removeAt(undoStack.lastIndex)
        cmd.undo(model)
        redoStack.add(cmd)
        onStateChanged?.invoke()
    }

    fun redo(model: EditableModel) {
        if (!canRedo()) return
        val cmd = redoStack.removeAt(redoStack.lastIndex)
        cmd.execute(model)
        undoStack.add(cmd)
        onStateChanged?.invoke()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun markSaved() {
        savePoint = undoStack.size
        onStateChanged?.invoke()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        savePoint = 0
        onStateChanged?.invoke()
    }
}
