package com.rickytech.bpmn.viewer.parser

import com.rickytech.bpmn.viewer.model.BpmnNodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BpmnParserTest {

    private val parser = BpmnParser()

    @Test
    fun `parse simple process with serviceTask userTask and exclusiveGateway`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn">
              <process id="testProcess" name="Test Process">
                <startEvent id="start1"/>
                <serviceTask id="task1" name="Service" activiti:class="com.example.Foo"/>
                <userTask id="task2" name="User" activiti:assignee="admin"/>
                <exclusiveGateway id="gw1" name="Decision"/>
                <endEvent id="end1"/>
                <sequenceFlow id="flow1" sourceRef="start1" targetRef="task1"/>
                <sequenceFlow id="flow2" sourceRef="task1" targetRef="gw1"/>
                <sequenceFlow id="flow3" sourceRef="gw1" targetRef="task2"/>
                <sequenceFlow id="flow4" sourceRef="task2" targetRef="end1"/>
              </process>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        assertEquals("testProcess", model.processId)
        assertEquals("Test Process", model.processName)
        assertEquals(5, model.nodes.size)
        assertEquals(4, model.edges.size)

        val serviceTask = model.nodes.first { it.id == "task1" }
        assertEquals(BpmnNodeType.SERVICE_TASK, serviceTask.type)
        assertEquals("com.example.Foo", serviceTask.javaClass)

        val userTask = model.nodes.first { it.id == "task2" }
        assertEquals(BpmnNodeType.USER_TASK, userTask.type)
        assertEquals("admin", userTask.assignee)

        val gateway = model.nodes.first { it.id == "gw1" }
        assertEquals(BpmnNodeType.EXCLUSIVE_GATEWAY, gateway.type)
    }

    @Test
    fun `parse multi-instance configuration`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn">
              <process id="miProcess" name="MI Process">
                <startEvent id="start1"/>
                <serviceTask id="task1" name="MI Task" activiti:class="com.example.Bar">
                  <multiInstanceLoopCharacteristics isSequential="true">
                    <loopCardinality>5</loopCardinality>
                    <completionCondition>${"$"}{nrOfCompletedInstances/nrOfInstances >= 0.6}</completionCondition>
                  </multiInstanceLoopCharacteristics>
                </serviceTask>
                <endEvent id="end1"/>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1"/>
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1"/>
              </process>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        val task = model.nodes.first { it.id == "task1" }
        assertEquals(true, task.isSequential)
        assertEquals("5", task.loopCardinality)
        assertTrue(task.completionCondition.contains("nrOfCompletedInstances"))
    }

    @Test
    fun `parse execution listener and task listener`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn">
              <process id="listenerProc" name="Listener Process">
                <startEvent id="start1"/>
                <userTask id="task1" name="Task With Listeners" activiti:assignee="user1">
                  <extensionElements>
                    <activiti:executionListener event="start" class="com.example.ExecListener"/>
                    <activiti:taskListener event="create" class="com.example.TaskListener"/>
                  </extensionElements>
                </userTask>
                <endEvent id="end1"/>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1"/>
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1"/>
              </process>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        val task = model.nodes.first { it.id == "task1" }
        assertEquals(1, task.executionListeners.size)
        assertEquals("start", task.executionListeners[0].event)
        assertEquals("com.example.ExecListener", task.executionListeners[0].implementation)
        assertEquals("class", task.executionListeners[0].implementationType)

        assertEquals(1, task.taskListeners.size)
        assertEquals("create", task.taskListeners[0].event)
        assertEquals("com.example.TaskListener", task.taskListeners[0].implementation)
    }

    @Test
    fun `parse variable mappings in and out`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn">
              <process id="vmProc" name="VM Process">
                <startEvent id="start1"/>
                <callActivity id="call1" name="Sub Call" calledElement="subProcess">
                  <extensionElements>
                    <activiti:in source="mainVar" target="subVar"/>
                    <activiti:out source="subResult" target="mainResult"/>
                  </extensionElements>
                </callActivity>
                <endEvent id="end1"/>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="call1"/>
                <sequenceFlow id="f2" sourceRef="call1" targetRef="end1"/>
              </process>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        val call = model.nodes.first { it.id == "call1" }
        assertEquals(BpmnNodeType.CALL_ACTIVITY, call.type)
        assertEquals("subProcess", call.calledElement)
        assertEquals(2, call.variableMappings.size)
        assertEquals("in", call.variableMappings[0].direction)
        assertEquals("mainVar", call.variableMappings[0].source)
        assertEquals("subVar", call.variableMappings[0].target)
        assertEquals("out", call.variableMappings[1].direction)
        assertEquals("subResult", call.variableMappings[1].source)
        assertEquals("mainResult", call.variableMappings[1].target)
    }

    @Test
    fun `parse DI coordinates and verify node geometry`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                         xmlns:omgdc="http://www.omg.org/spec/DD/20100524/DC">
              <process id="diProc" name="DI Process">
                <startEvent id="start1"/>
                <serviceTask id="task1" name="Positioned Task"/>
                <endEvent id="end1"/>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1"/>
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1"/>
              </process>
              <bpmndi:BPMNDiagram id="diagram1">
                <bpmndi:BPMNPlane id="plane1" bpmnElement="diProc">
                  <bpmndi:BPMNShape bpmnElement="start1">
                    <omgdc:Bounds x="100" y="200" width="36" height="36"/>
                  </bpmndi:BPMNShape>
                  <bpmndi:BPMNShape bpmnElement="task1">
                    <omgdc:Bounds x="250" y="180" width="160" height="80"/>
                  </bpmndi:BPMNShape>
                </bpmndi:BPMNPlane>
              </bpmndi:BPMNDiagram>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        val start = model.nodes.first { it.id == "start1" }
        assertEquals(100.0, start.x)
        assertEquals(200.0, start.y)
        assertEquals(36.0, start.width)
        assertEquals(36.0, start.height)

        val task = model.nodes.first { it.id == "task1" }
        assertEquals(250.0, task.x)
        assertEquals(180.0, task.y)
        assertEquals(160.0, task.width)
        assertEquals(80.0, task.height)
    }

    @Test
    fun `parse multiple processes and merge nodes`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                         xmlns:activiti="http://activiti.org/bpmn">
              <process id="proc1" name="Process 1">
                <startEvent id="start1"/>
                <serviceTask id="task1" name="Task A" activiti:class="com.a.A"/>
                <endEvent id="end1"/>
                <sequenceFlow id="f1" sourceRef="start1" targetRef="task1"/>
                <sequenceFlow id="f2" sourceRef="task1" targetRef="end1"/>
              </process>
              <process id="proc2" name="Process 2">
                <startEvent id="start2"/>
                <userTask id="task2" name="Task B" activiti:assignee="user1"/>
                <endEvent id="end2"/>
                <sequenceFlow id="f3" sourceRef="start2" targetRef="task2"/>
                <sequenceFlow id="f4" sourceRef="task2" targetRef="end2"/>
              </process>
            </definitions>
        """.trimIndent()

        val model = parser.parse(xml)
        // First process ID is used
        assertEquals("proc1", model.processId)
        // Nodes from both processes are merged
        assertEquals(6, model.nodes.size)
        assertEquals(4, model.edges.size)
        assertNotNull(model.nodes.firstOrNull { it.id == "task1" })
        assertNotNull(model.nodes.firstOrNull { it.id == "task2" })
    }
}
