package com.rickytech.bpmn.viewer.model

enum class BpmnNodeType {
    // 现有 (保持不变)
    START_EVENT, END_EVENT, SERVICE_TASK, USER_TASK, CALL_ACTIVITY, MANUAL_TASK,
    EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY,
    // 新增任务
    SCRIPT_TASK, BUSINESS_RULE_TASK, RECEIVE_TASK, SEND_TASK, MAIL_TASK,
    // 新增网关
    INCLUSIVE_GATEWAY, EVENT_BASED_GATEWAY,
    // 启动事件变体
    TIMER_START_EVENT, MESSAGE_START_EVENT, SIGNAL_START_EVENT, ERROR_START_EVENT,
    // 结束事件变体
    ERROR_END_EVENT, TERMINATE_END_EVENT, CANCEL_END_EVENT,
    // 边界事件
    BOUNDARY_TIMER, BOUNDARY_ERROR, BOUNDARY_MESSAGE,
    BOUNDARY_SIGNAL, BOUNDARY_CANCEL, BOUNDARY_COMPENSATE,
    // 中间事件
    INTERMEDIATE_TIMER_CATCH, INTERMEDIATE_MESSAGE_CATCH, INTERMEDIATE_SIGNAL_CATCH,
    INTERMEDIATE_SIGNAL_THROW, INTERMEDIATE_COMPENSATE_THROW,
    // 结构
    SUB_PROCESS, EVENT_SUB_PROCESS,
    // 泳道
    POOL, LANE,
    // 注释
    TEXT_ANNOTATION
}

/**
 * Variable mapping declared via <activiti:in> / <activiti:out> inside <extensionElements>.
 *
 * @param direction "in" (caller -> callee) or "out" (callee -> caller)
 * @param source variable name in the source process
 * @param target variable name in the target process
 */
data class VariableMapping(
    val direction: String,
    val source: String,
    val target: String
)

data class ListenerDef(
    val event: String,
    val implementation: String,
    val implementationType: String
)

data class BpmnNode(
    val id: String,
    val name: String,
    val type: BpmnNodeType,
    val javaClass: String? = null,                       // from activiti:class
    val delegateExpression: String? = null,              // from activiti:delegateExpression
    val calledElement: String? = null,                   // for callActivity
    val assignee: String? = null,                        // userTask: activiti:assignee
    val candidateUsers: String? = null,                  // userTask: activiti:candidateUsers
    val candidateGroups: String? = null,                 // userTask: activiti:candidateGroups
    val isSequential: Boolean? = null,                   // multiInstanceLoopCharacteristics @isSequential
    val loopCardinality: String? = null,                 // <loopCardinality>
    val loopDataInputRef: String? = null,                // <loopDataInputRef>
    val inputDataItem: String? = null,                   // <inputDataItem name="...">
    val completionCondition: String = "",                  // <completionCondition>
    val loopCondition: String = "",                        // <loopCondition>
    val variableMappings: List<VariableMapping> = emptyList(),
    // 新增字段
    val scriptFormat: String? = null,
    val scriptContent: String? = null,
    val timerDuration: String? = null,
    val timeCycle: String? = null,
    val timeDate: String? = null,
    val messageRef: String? = null,
    val signalRef: String? = null,
    val errorRef: String? = null,
    val errorCode: String? = null,
    val isAsync: Boolean = false,
    val attachedToRef: String? = null,
    val cancelActivity: Boolean = true,
    val executionListeners: List<ListenerDef> = emptyList(),
    val taskListeners: List<ListenerDef> = emptyList(),
    val formKey: String? = null,
    val childNodes: List<BpmnNode> = emptyList(),
    val childEdges: List<BpmnEdge> = emptyList(),
    val laneRef: String? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 160.0,
    var height: Double = 60.0
)
