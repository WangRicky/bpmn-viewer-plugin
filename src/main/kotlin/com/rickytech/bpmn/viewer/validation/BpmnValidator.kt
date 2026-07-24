package com.rickytech.bpmn.viewer.validation

import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType

/**
 * 校验严重级别。
 */
enum class Severity { ERROR, WARNING }

/**
 * 单条校验问题。
 */
data class ValidationIssue(
    val severity: Severity,
    val nodeId: String?,
    val message: String
)

/**
 * BPMN 流程模型校验器。
 */
class BpmnValidator {

    fun validate(model: EditableModel): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        checkStartAndEnd(model, issues)
        checkUniqueIds(model, issues)
        checkEdgeReferences(model, issues)
        checkGatewayOutEdges(model, issues)
        checkStartEventNoInEdge(model, issues)
        checkEndEventNoOutEdge(model, issues)
        checkServiceTaskImpl(model, issues)
        checkUserTaskAssignment(model, issues)
        checkBoundaryAttachment(model, issues)
        checkMultiInstanceConfig(model, issues)
        checkTimerConfig(model, issues)
        checkUnreachableNodes(model, issues)
        checkCallActivityTarget(model, issues)
        return issues
    }

    private fun checkStartAndEnd(model: EditableModel, issues: MutableList<ValidationIssue>) {
        val hasStart = model.nodes.any { it.type in START_EVENT_TYPES }
        val hasEnd = model.nodes.any { it.type in END_EVENT_TYPES }
        if (!hasStart) {
            issues += ValidationIssue(Severity.ERROR, null, "流程缺少开始事件")
        }
        if (!hasEnd) {
            issues += ValidationIssue(Severity.ERROR, null, "流程缺少结束事件")
        }
    }

    private fun checkUniqueIds(model: EditableModel, issues: MutableList<ValidationIssue>) {
        val emptyIdNodes = model.nodes.filter { it.id.isBlank() }
        if (emptyIdNodes.isNotEmpty()) {
            issues += ValidationIssue(
                Severity.ERROR, null,
                "存在 ${emptyIdNodes.size} 个节点未设置ID"
            )
        }
        val duplicates = model.nodes
            .filter { it.id.isNotBlank() }
            .groupBy { it.id }
            .filter { it.value.size > 1 }
            .keys
        for (dupId in duplicates) {
            issues += ValidationIssue(Severity.ERROR, dupId, "节点ID重复: $dupId")
        }
    }

    private fun checkEdgeReferences(model: EditableModel, issues: MutableList<ValidationIssue>) {
        val nodeIds = model.nodes.map { it.id }.toHashSet()
        for (edge in model.edges) {
            if (edge.sourceRef.isBlank() || edge.sourceRef !in nodeIds) {
                issues += ValidationIssue(
                    Severity.ERROR, edge.id,
                    "连线 ${edge.id} 的源节点不存在: ${edge.sourceRef}"
                )
            }
            if (edge.targetRef.isBlank() || edge.targetRef !in nodeIds) {
                issues += ValidationIssue(
                    Severity.ERROR, edge.id,
                    "连线 ${edge.id} 的目标节点不存在: ${edge.targetRef}"
                )
            }
        }
    }

    private fun checkGatewayOutEdges(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type in GATEWAY_TYPES }) {
            val outCount = model.edges.count { it.sourceRef == node.id }
            if (outCount == 0) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "网关 ${nodeLabel(node)} 缺少出边"
                )
            }
        }
    }

    private fun checkStartEventNoInEdge(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type in START_EVENT_TYPES }) {
            val inCount = model.edges.count { it.targetRef == node.id }
            if (inCount > 0) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "开始事件 ${nodeLabel(node)} 不应有入边"
                )
            }
        }
    }

    private fun checkEndEventNoOutEdge(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type in END_EVENT_TYPES }) {
            val outCount = model.edges.count { it.sourceRef == node.id }
            if (outCount > 0) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "结束事件 ${nodeLabel(node)} 不应有出边"
                )
            }
        }
    }

    private fun checkServiceTaskImpl(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type == BpmnNodeType.SERVICE_TASK }) {
            if (node.javaClass.isNullOrBlank() && node.delegateExpression.isNullOrBlank()) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "服务任务 ${nodeLabel(node)} 未配置实现类"
                )
            }
        }
    }

    private fun checkUserTaskAssignment(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type == BpmnNodeType.USER_TASK }) {
            if (node.assignee.isNullOrBlank() &&
                node.candidateUsers.isNullOrBlank() &&
                node.candidateGroups.isNullOrBlank()
            ) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "用户任务 ${nodeLabel(node)} 未配置处理人"
                )
            }
        }
    }

    private fun checkBoundaryAttachment(model: EditableModel, issues: MutableList<ValidationIssue>) {
        val nodeIds = model.nodes.map { it.id }.toHashSet()
        for (node in model.nodes.filter { it.type in BOUNDARY_TYPES }) {
            val ref = node.attachedToRef
            if (ref.isNullOrBlank() || ref !in nodeIds) {
                issues += ValidationIssue(
                    Severity.ERROR, node.id,
                    "边界事件 ${nodeLabel(node)} 的附属节点引用无效: ${node.attachedToRef}"
                )
            }
        }
    }

    private fun checkMultiInstanceConfig(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.isSequential != null }) {
            if (node.loopCardinality.isNullOrBlank() && node.loopDataInputRef.isNullOrBlank()) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "多实例节点 ${nodeLabel(node)} 未配置循环基数或数据输入引用"
                )
            }
        }
    }

    private fun checkTimerConfig(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type in TIMER_TYPES }) {
            if (node.timerDuration.isNullOrBlank() &&
                node.timeCycle.isNullOrBlank() &&
                node.timeDate.isNullOrBlank()
            ) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "定时器事件 ${nodeLabel(node)} 未配置任何定时器表达式"
                )
            }
        }
    }

    private fun checkUnreachableNodes(model: EditableModel, issues: MutableList<ValidationIssue>) {
        val incoming = model.edges.map { it.targetRef }.toHashSet()
        for (node in model.nodes) {
            if (node.type in START_EVENT_TYPES) continue
            if (node.type in BOUNDARY_TYPES) continue
            if (node.type == BpmnNodeType.POOL ||
                node.type == BpmnNodeType.LANE ||
                node.type == BpmnNodeType.TEXT_ANNOTATION
            ) continue
            if (node.id !in incoming) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "节点 ${nodeLabel(node)} 可能不可达（无入边）"
                )
            }
        }
    }

    private fun checkCallActivityTarget(model: EditableModel, issues: MutableList<ValidationIssue>) {
        for (node in model.nodes.filter { it.type == BpmnNodeType.CALL_ACTIVITY }) {
            if (node.calledElement.isNullOrBlank()) {
                issues += ValidationIssue(
                    Severity.WARNING, node.id,
                    "调用活动 ${nodeLabel(node)} 未配置 calledElement"
                )
            }
        }
    }

    private fun nodeLabel(node: EditableNode): String =
        if (node.name.isNotBlank()) "${node.name}(${node.id})" else node.id

    companion object {
        private val START_EVENT_TYPES = setOf(
            BpmnNodeType.START_EVENT,
            BpmnNodeType.TIMER_START_EVENT,
            BpmnNodeType.MESSAGE_START_EVENT,
            BpmnNodeType.SIGNAL_START_EVENT,
            BpmnNodeType.ERROR_START_EVENT
        )

        private val END_EVENT_TYPES = setOf(
            BpmnNodeType.END_EVENT,
            BpmnNodeType.ERROR_END_EVENT,
            BpmnNodeType.TERMINATE_END_EVENT,
            BpmnNodeType.CANCEL_END_EVENT
        )

        private val GATEWAY_TYPES = setOf(
            BpmnNodeType.EXCLUSIVE_GATEWAY,
            BpmnNodeType.INCLUSIVE_GATEWAY,
            BpmnNodeType.PARALLEL_GATEWAY
        )

        private val BOUNDARY_TYPES = setOf(
            BpmnNodeType.BOUNDARY_TIMER,
            BpmnNodeType.BOUNDARY_ERROR,
            BpmnNodeType.BOUNDARY_MESSAGE,
            BpmnNodeType.BOUNDARY_SIGNAL,
            BpmnNodeType.BOUNDARY_CANCEL,
            BpmnNodeType.BOUNDARY_COMPENSATE
        )

        private val TIMER_TYPES = setOf(
            BpmnNodeType.TIMER_START_EVENT,
            BpmnNodeType.BOUNDARY_TIMER,
            BpmnNodeType.INTERMEDIATE_TIMER_CATCH
        )
    }
}
