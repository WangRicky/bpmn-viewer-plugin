package com.rickytech.bpmn.viewer.edit

import com.rickytech.bpmn.viewer.model.BpmnNodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CommandStackTest {

    private fun makeModel(): EditableModel {
        val model = EditableModel("testProcess", "Test")
        model.nodes.add(EditableNode("node1", "Node 1", BpmnNodeType.SERVICE_TASK, x = 10.0, y = 20.0))
        return model
    }

    @Test
    fun `execute makes canUndo true and canRedo false`() {
        val stack = CommandStack()
        val model = makeModel()
        val cmd = MoveNodeCommand("node1", 10.0, 20.0, 100.0, 200.0)

        assertFalse(stack.canUndo())
        assertFalse(stack.canRedo())

        stack.execute(cmd, model)

        assertTrue(stack.canUndo())
        assertFalse(stack.canRedo())
    }

    @Test
    fun `undo makes canRedo true`() {
        val stack = CommandStack()
        val model = makeModel()
        val cmd = MoveNodeCommand("node1", 10.0, 20.0, 100.0, 200.0)

        stack.execute(cmd, model)
        stack.undo(model)

        assertFalse(stack.canUndo())
        assertTrue(stack.canRedo())
    }

    @Test
    fun `redo restores state`() {
        val stack = CommandStack()
        val model = makeModel()
        val cmd = MoveNodeCommand("node1", 10.0, 20.0, 100.0, 200.0)

        stack.execute(cmd, model)
        assertEquals(100.0, model.nodes[0].x)

        stack.undo(model)
        assertEquals(10.0, model.nodes[0].x)

        stack.redo(model)
        assertEquals(100.0, model.nodes[0].x)
        assertTrue(stack.canUndo())
        assertFalse(stack.canRedo())
    }

    @Test
    fun `markSaved and isModified`() {
        val stack = CommandStack()
        val model = makeModel()

        assertFalse(stack.isModified)

        stack.execute(MoveNodeCommand("node1", 10.0, 20.0, 50.0, 50.0), model)
        assertTrue(stack.isModified)

        stack.markSaved()
        assertFalse(stack.isModified)

        stack.execute(MoveNodeCommand("node1", 50.0, 50.0, 80.0, 80.0), model)
        assertTrue(stack.isModified)

        stack.undo(model)
        assertFalse(stack.isModified)
    }

    @Test
    fun `exceeding maxSize discards earliest command`() {
        val maxSize = 50
        val stack = CommandStack(maxSize)
        val model = makeModel()

        // Execute 51 commands
        for (i in 1..51) {
            stack.execute(
                MoveNodeCommand("node1", (i - 1).toDouble(), 0.0, i.toDouble(), 0.0),
                model
            )
        }

        // Should still have maxSize undo entries
        var undoCount = 0
        while (stack.canUndo()) {
            stack.undo(model)
            undoCount++
        }
        assertEquals(maxSize, undoCount)
    }
}
