package com.rickytech.bpmn.viewer.edit

import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.model.VariableMapping
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class EditCommandTest {

    private fun makeModel(): EditableModel {
        val model = EditableModel("testProcess", "Test")
        model.nodes.add(EditableNode("node1", "Node 1", BpmnNodeType.SERVICE_TASK, x = 10.0, y = 20.0))
        model.edges.add(EditableEdge("edge1", sourceRef = "node1", targetRef = "node1"))
        return model
    }

    // --- MoveNodeCommand ---

    @Test
    fun `MoveNodeCommand execute and undo`() {
        val model = makeModel()
        val cmd = MoveNodeCommand("node1", 10.0, 20.0, 100.0, 200.0)

        cmd.execute(model)
        assertEquals(100.0, model.nodes[0].x)
        assertEquals(200.0, model.nodes[0].y)

        cmd.undo(model)
        assertEquals(10.0, model.nodes[0].x)
        assertEquals(20.0, model.nodes[0].y)
    }

    // --- AddNodeCommand ---

    @Test
    fun `AddNodeCommand execute and undo`() {
        val model = makeModel()
        val newNode = EditableNode("node2", "Node 2", BpmnNodeType.USER_TASK)
        val cmd = AddNodeCommand(newNode)

        cmd.execute(model)
        assertEquals(2, model.nodes.size)
        assertNotNull(model.nodes.firstOrNull { it.id == "node2" })

        cmd.undo(model)
        assertEquals(1, model.nodes.size)
        assertNull(model.nodes.firstOrNull { it.id == "node2" })
    }

    // --- RemoveNodeCommand ---

    @Test
    fun `RemoveNodeCommand execute removes node and related edges, undo restores`() {
        val model = makeModel()
        // Add a second node and an edge between them
        model.nodes.add(EditableNode("node2", "Node 2", BpmnNodeType.END_EVENT))
        model.edges.clear()
        model.edges.add(EditableEdge("e1", sourceRef = "node1", targetRef = "node2"))

        val cmd = RemoveNodeCommand("node1")

        cmd.execute(model)
        assertEquals(1, model.nodes.size)
        assertEquals("node2", model.nodes[0].id)
        assertTrue(model.edges.isEmpty())

        cmd.undo(model)
        assertEquals(2, model.nodes.size)
        assertEquals(1, model.edges.size)
    }

    // --- ChangePropertyCommand ---

    @Test
    fun `ChangePropertyCommand changes value and undo restores`() {
        val model = makeModel()
        val cmd = ChangePropertyCommand("node1", "name", "Node 1", "New Name")

        cmd.execute(model)
        assertEquals("New Name", model.nodes[0].name)

        cmd.undo(model)
        assertEquals("Node 1", model.nodes[0].name)
    }

    @Test
    fun `ChangePropertyCommand for javaClass`() {
        val model = makeModel()
        val cmd = ChangePropertyCommand("node1", "javaClass", null, "com.example.NewClass")

        cmd.execute(model)
        assertEquals("com.example.NewClass", model.nodes[0].javaClass)

        cmd.undo(model)
        assertNull(model.nodes[0].javaClass)
    }

    // --- AddEdgeCommand ---

    @Test
    fun `AddEdgeCommand execute and undo`() {
        val model = makeModel()
        model.nodes.add(EditableNode("node2", "Node 2", BpmnNodeType.END_EVENT))
        val newEdge = EditableEdge("edge2", sourceRef = "node1", targetRef = "node2")
        val cmd = AddEdgeCommand(newEdge)

        cmd.execute(model)
        assertEquals(2, model.edges.size)

        cmd.undo(model)
        assertEquals(1, model.edges.size)
        assertNull(model.edges.firstOrNull { it.id == "edge2" })
    }

    // --- RemoveEdgeCommand ---

    @Test
    fun `RemoveEdgeCommand execute and undo`() {
        val model = makeModel()
        val cmd = RemoveEdgeCommand("edge1")

        cmd.execute(model)
        assertTrue(model.edges.isEmpty())

        cmd.undo(model)
        assertEquals(1, model.edges.size)
        assertEquals("edge1", model.edges[0].id)
    }

    // --- AddVariableMappingCommand ---

    @Test
    fun `AddVariableMappingCommand execute and undo`() {
        val model = makeModel()
        val mapping = VariableMapping("in", "srcVar", "tgtVar")
        val cmd = AddVariableMappingCommand("node1", mapping)

        cmd.execute(model)
        assertEquals(1, model.nodes[0].variableMappings.size)
        assertEquals(mapping, model.nodes[0].variableMappings[0])

        cmd.undo(model)
        assertTrue(model.nodes[0].variableMappings.isEmpty())
    }

    // --- RemoveVariableMappingCommand ---

    @Test
    fun `RemoveVariableMappingCommand execute and undo`() {
        val model = makeModel()
        val mapping = VariableMapping("out", "a", "b")
        model.nodes[0].variableMappings.add(mapping)

        val cmd = RemoveVariableMappingCommand("node1", 0, mapping)

        cmd.execute(model)
        assertTrue(model.nodes[0].variableMappings.isEmpty())

        cmd.undo(model)
        assertEquals(1, model.nodes[0].variableMappings.size)
        assertEquals(mapping, model.nodes[0].variableMappings[0])
    }
}
