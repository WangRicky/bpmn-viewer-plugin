package com.rickytech.bpmn.viewer.serializer

import com.rickytech.bpmn.viewer.edit.EditableEdge
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.model.ListenerDef
import com.rickytech.bpmn.viewer.model.VariableMapping
import com.rickytech.bpmn.viewer.parser.BpmnParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BpmnSerializerTest {

    private val serializer = BpmnSerializer()
    private val parser = BpmnParser()

    @Test
    fun `round-trip preserves processId and processName`() {
        val model = EditableModel("myProcess", "My Process")
        model.nodes.add(EditableNode("start1", "Start", BpmnNodeType.START_EVENT))
        model.nodes.add(EditableNode("end1", "End", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        assertEquals("myProcess", parsed.processId)
        assertEquals("My Process", parsed.processName)
    }

    @Test
    fun `round-trip preserves node count and edge count`() {
        val model = EditableModel("proc1", "Process 1")
        model.nodes.add(EditableNode("start1", "Start", BpmnNodeType.START_EVENT))
        model.nodes.add(EditableNode("task1", "Service Task", BpmnNodeType.SERVICE_TASK,
            javaClass = "com.example.Impl"))
        model.nodes.add(EditableNode("end1", "End", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "task1"))
        model.edges.add(EditableEdge("f2", sourceRef = "task1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        assertEquals(3, parsed.nodes.size)
        assertEquals(2, parsed.edges.size)
    }

    @Test
    fun `round-trip preserves serviceTask javaClass`() {
        val model = EditableModel("proc1", "Process 1")
        model.nodes.add(EditableNode("start1", "", BpmnNodeType.START_EVENT))
        model.nodes.add(EditableNode("svc1", "My Service", BpmnNodeType.SERVICE_TASK,
            javaClass = "com.example.Handler"))
        model.nodes.add(EditableNode("end1", "", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "svc1"))
        model.edges.add(EditableEdge("f2", sourceRef = "svc1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        val svc = parsed.nodes.first { it.id == "svc1" }
        assertEquals("com.example.Handler", svc.javaClass)
        assertEquals("My Service", svc.name)
    }

    @Test
    fun `round-trip preserves variable mappings`() {
        val model = EditableModel("proc1", "P")
        model.nodes.add(EditableNode("start1", "", BpmnNodeType.START_EVENT))
        val callNode = EditableNode("call1", "Call Sub", BpmnNodeType.CALL_ACTIVITY,
            calledElement = "subProc")
        callNode.variableMappings.add(VariableMapping("in", "x", "y"))
        callNode.variableMappings.add(VariableMapping("out", "result", "output"))
        model.nodes.add(callNode)
        model.nodes.add(EditableNode("end1", "", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "call1"))
        model.edges.add(EditableEdge("f2", sourceRef = "call1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        val call = parsed.nodes.first { it.id == "call1" }
        assertEquals("subProc", call.calledElement)
        assertEquals(2, call.variableMappings.size)
        assertEquals("in", call.variableMappings[0].direction)
        assertEquals("x", call.variableMappings[0].source)
        assertEquals("y", call.variableMappings[0].target)
        assertEquals("out", call.variableMappings[1].direction)
    }

    @Test
    fun `round-trip preserves execution listeners`() {
        val model = EditableModel("proc1", "P")
        model.nodes.add(EditableNode("start1", "", BpmnNodeType.START_EVENT))
        val task = EditableNode("svc1", "Task", BpmnNodeType.SERVICE_TASK,
            javaClass = "com.example.X")
        task.executionListeners.add(ListenerDef("start", "com.example.Listener", "class"))
        model.nodes.add(task)
        model.nodes.add(EditableNode("end1", "", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "svc1"))
        model.edges.add(EditableEdge("f2", sourceRef = "svc1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        val svc = parsed.nodes.first { it.id == "svc1" }
        assertEquals(1, svc.executionListeners.size)
        assertEquals("start", svc.executionListeners[0].event)
        assertEquals("com.example.Listener", svc.executionListeners[0].implementation)
    }

    @Test
    fun `round-trip preserves multi-instance configuration`() {
        val model = EditableModel("proc1", "P")
        model.nodes.add(EditableNode("start1", "", BpmnNodeType.START_EVENT))
        model.nodes.add(EditableNode("svc1", "MI Task", BpmnNodeType.SERVICE_TASK,
            javaClass = "com.example.Y",
            isSequential = true,
            loopCardinality = "3",
            completionCondition = "\${done}"))
        model.nodes.add(EditableNode("end1", "", BpmnNodeType.END_EVENT))
        model.edges.add(EditableEdge("f1", sourceRef = "start1", targetRef = "svc1"))
        model.edges.add(EditableEdge("f2", sourceRef = "svc1", targetRef = "end1"))

        val xml = serializer.serialize(model)
        val parsed = parser.parse(xml)

        val svc = parsed.nodes.first { it.id == "svc1" }
        assertEquals(true, svc.isSequential)
        assertEquals("3", svc.loopCardinality)
        assertEquals("\${done}", svc.completionCondition)
    }
}
