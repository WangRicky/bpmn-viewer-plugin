package com.rickytech.bpmn.viewer.validation

import com.rickytech.bpmn.viewer.edit.EditableEdge
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BpmnValidatorTest {

    private val validator = BpmnValidator()

    private fun makeModel(
        nodes: List<EditableNode> = emptyList(),
        edges: List<EditableEdge> = emptyList()
    ): EditableModel {
        val model = EditableModel("testProcess", "Test Process")
        model.nodes.addAll(nodes)
        model.edges.addAll(edges)
        return model
    }

    private fun validModel(): EditableModel = makeModel(
        nodes = listOf(
            EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
            EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.example.Foo"),
            EditableNode("end1", "End", BpmnNodeType.END_EVENT)
        ),
        edges = listOf(
            EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
            EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
        )
    )

    @Test
    fun `valid model produces no issues`() {
        val issues = validator.validate(validModel())
        assertTrue(issues.isEmpty(), "Expected no issues but got: $issues")
    }

    // --- checkStartAndEnd ---

    @Test
    fun `checkStartAndEnd - missing start event`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(EditableEdge("f1", sourceRef = "task1", targetRef = "end1"))
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("开始事件") })
    }

    @Test
    fun `checkStartAndEnd - missing end event`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X")
            ),
            edges = listOf(EditableEdge("f1", sourceRef = "start1", targetRef = "task1"))
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("结束事件") })
    }

    // --- checkUniqueIds ---

    @Test
    fun `checkUniqueIds - blank id`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("", "NoId", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("未设置ID") })
    }

    @Test
    fun `checkUniqueIds - duplicate ids`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("dup", "Task A", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("dup", "Task B", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.Y"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "dup"),
                EditableEdge("f2", sourceRef = "dup", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("ID重复") })
    }

    // --- checkEdgeReferences ---

    @Test
    fun `checkEdgeReferences - nonexistent source or target`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "end1"),
                EditableEdge("f2", sourceRef = "ghost", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("源节点不存在") })
    }

    // --- checkGatewayOutEdges ---

    @Test
    fun `checkGatewayOutEdges - gateway with no out edges`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("gw1", "Gateway", BpmnNodeType.EXCLUSIVE_GATEWAY),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "gw1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("缺少出边") })
    }

    // --- checkStartEventNoInEdge ---

    @Test
    fun `checkStartEventNoInEdge - start event has incoming edge`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "task1", targetRef = "start1"),
                EditableEdge("f2", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f3", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("不应有入边") })
    }

    // --- checkEndEventNoOutEdge ---

    @Test
    fun `checkEndEventNoOutEdge - end event has outgoing edge`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1"),
                EditableEdge("f3", sourceRef = "end1", targetRef = "task1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("不应有出边") })
    }

    // --- checkServiceTaskImpl ---

    @Test
    fun `checkServiceTaskImpl - service task without implementation`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "NoImpl", BpmnNodeType.SERVICE_TASK),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("未配置实现类") })
    }

    // --- checkUserTaskAssignment ---

    @Test
    fun `checkUserTaskAssignment - user task without assignment`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "NoAssign", BpmnNodeType.USER_TASK),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("未配置处理人") })
    }

    // --- checkBoundaryAttachment ---

    @Test
    fun `checkBoundaryAttachment - invalid attachedToRef`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "Task", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("boundary1", "Boundary", BpmnNodeType.BOUNDARY_TIMER,
                    attachedToRef = "nonexistent", timerDuration = "PT5M"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("附属节点引用无效") })
    }

    // --- checkMultiInstanceConfig ---

    @Test
    fun `checkMultiInstanceConfig - multi-instance without cardinality or data input`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "MI Task", BpmnNodeType.SERVICE_TASK,
                    javaClass = "com.x.X", isSequential = true),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("未配置循环基数或数据输入引用") })
    }

    // --- checkTimerConfig ---

    @Test
    fun `checkTimerConfig - timer event without expression`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.TIMER_START_EVENT),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("未配置任何定时器表达式") })
    }

    // --- checkUnreachableNodes ---

    @Test
    fun `checkUnreachableNodes - node with no incoming edge`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("task1", "Reachable", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.X"),
                EditableNode("task2", "Unreachable", BpmnNodeType.SERVICE_TASK, javaClass = "com.x.Y"),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "task1"),
                EditableEdge("f2", sourceRef = "task1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.nodeId == "task2" && it.message.contains("不可达") })
    }

    // --- checkCallActivityTarget ---

    @Test
    fun `checkCallActivityTarget - callActivity without calledElement`() {
        val model = makeModel(
            nodes = listOf(
                EditableNode("start1", "Start", BpmnNodeType.START_EVENT),
                EditableNode("call1", "Call", BpmnNodeType.CALL_ACTIVITY),
                EditableNode("end1", "End", BpmnNodeType.END_EVENT)
            ),
            edges = listOf(
                EditableEdge("f1", sourceRef = "start1", targetRef = "call1"),
                EditableEdge("f2", sourceRef = "call1", targetRef = "end1")
            )
        )
        val issues = validator.validate(model)
        assertTrue(issues.any { it.message.contains("calledElement") })
    }
}
