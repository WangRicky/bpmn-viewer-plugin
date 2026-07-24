package com.rickytech.bpmn.viewer.edit

import com.rickytech.bpmn.viewer.model.*

/**
 * 可变节点模型，与 [BpmnNode] 字段一一对应，用于编辑期。
 */
class EditableNode(
    var id: String,
    var name: String,
    var type: BpmnNodeType,
    var javaClass: String? = null,
    var delegateExpression: String? = null,
    var calledElement: String? = null,
    var assignee: String? = null,
    var candidateUsers: String? = null,
    var candidateGroups: String? = null,
    var isSequential: Boolean? = null,
    var loopCardinality: String? = null,
    var loopDataInputRef: String? = null,
    var inputDataItem: String? = null,
    var completionCondition: String = "",
    var loopCondition: String = "",
    var variableMappings: MutableList<VariableMapping> = mutableListOf(),
    var scriptFormat: String? = null,
    var scriptContent: String? = null,
    var timerDuration: String? = null,
    var timeCycle: String? = null,
    var timeDate: String? = null,
    var messageRef: String? = null,
    var signalRef: String? = null,
    var errorRef: String? = null,
    var errorCode: String? = null,
    var isAsync: Boolean = false,
    var attachedToRef: String? = null,
    var cancelActivity: Boolean = true,
    var executionListeners: MutableList<ListenerDef> = mutableListOf(),
    var taskListeners: MutableList<ListenerDef> = mutableListOf(),
    var formKey: String? = null,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var width: Double = 160.0,
    var height: Double = 60.0
) {
    fun toBpmnNode(): BpmnNode = BpmnNode(
        id = id,
        name = name,
        type = type,
        javaClass = javaClass,
        delegateExpression = delegateExpression,
        calledElement = calledElement,
        assignee = assignee,
        candidateUsers = candidateUsers,
        candidateGroups = candidateGroups,
        isSequential = isSequential,
        loopCardinality = loopCardinality,
        loopDataInputRef = loopDataInputRef,
        inputDataItem = inputDataItem,
        completionCondition = completionCondition,
        loopCondition = loopCondition,
        variableMappings = variableMappings.toList(),
        scriptFormat = scriptFormat,
        scriptContent = scriptContent,
        timerDuration = timerDuration,
        timeCycle = timeCycle,
        timeDate = timeDate,
        messageRef = messageRef,
        signalRef = signalRef,
        errorRef = errorRef,
        errorCode = errorCode,
        isAsync = isAsync,
        attachedToRef = attachedToRef,
        cancelActivity = cancelActivity,
        executionListeners = executionListeners.toList(),
        taskListeners = taskListeners.toList(),
        formKey = formKey,
        childNodes = emptyList(),
        childEdges = emptyList(),
        laneRef = null,
        x = x,
        y = y,
        width = width,
        height = height
    )

    companion object {
        fun fromBpmnNode(node: BpmnNode): EditableNode = EditableNode(
            id = node.id,
            name = node.name,
            type = node.type,
            javaClass = node.javaClass,
            delegateExpression = node.delegateExpression,
            calledElement = node.calledElement,
            assignee = node.assignee,
            candidateUsers = node.candidateUsers,
            candidateGroups = node.candidateGroups,
            isSequential = node.isSequential,
            loopCardinality = node.loopCardinality,
            loopDataInputRef = node.loopDataInputRef,
            inputDataItem = node.inputDataItem,
            completionCondition = node.completionCondition,
            loopCondition = node.loopCondition,
            variableMappings = node.variableMappings.toMutableList(),
            scriptFormat = node.scriptFormat,
            scriptContent = node.scriptContent,
            timerDuration = node.timerDuration,
            timeCycle = node.timeCycle,
            timeDate = node.timeDate,
            messageRef = node.messageRef,
            signalRef = node.signalRef,
            errorRef = node.errorRef,
            errorCode = node.errorCode,
            isAsync = node.isAsync,
            attachedToRef = node.attachedToRef,
            cancelActivity = node.cancelActivity,
            executionListeners = node.executionListeners.toMutableList(),
            taskListeners = node.taskListeners.toMutableList(),
            formKey = node.formKey,
            x = node.x,
            y = node.y,
            width = node.width,
            height = node.height
        )
    }
}

/**
 * 可变连线模型，与 [BpmnEdge] 字段对应。
 */
class EditableEdge(
    var id: String,
    var name: String? = null,
    var sourceRef: String,
    var targetRef: String,
    var conditionExpression: String? = null,
    var routePoints: MutableList<Pair<Double, Double>> = mutableListOf(),
    var isDefaultFlow: Boolean = false,
    var isMessageFlow: Boolean = false,
    var isAssociation: Boolean = false
) {
    fun toBpmnEdge(): BpmnEdge = BpmnEdge(
        id = id,
        name = name,
        sourceRef = sourceRef,
        targetRef = targetRef,
        conditionExpression = conditionExpression,
        routePoints = routePoints.toList(),
        isDefaultFlow = isDefaultFlow,
        isMessageFlow = isMessageFlow,
        isAssociation = isAssociation
    )

    companion object {
        fun fromBpmnEdge(edge: BpmnEdge): EditableEdge = EditableEdge(
            id = edge.id,
            name = edge.name,
            sourceRef = edge.sourceRef,
            targetRef = edge.targetRef,
            conditionExpression = edge.conditionExpression,
            routePoints = edge.routePoints.toMutableList(),
            isDefaultFlow = edge.isDefaultFlow,
            isMessageFlow = edge.isMessageFlow,
            isAssociation = edge.isAssociation
        )
    }
}

/**
 * 可变流程模型，编辑模式下使用扁平化的 nodes / edges（不区分 pools / lanes）。
 */
class EditableModel(
    var processId: String,
    var processName: String,
    val nodes: MutableList<EditableNode> = mutableListOf(),
    val edges: MutableList<EditableEdge> = mutableListOf()
) {
    fun toSnapshot(): BpmnModel = BpmnModel(
        processId = processId,
        processName = processName,
        nodes = nodes.map { it.toBpmnNode() },
        edges = edges.map { it.toBpmnEdge() },
        pools = emptyList(),
        lanes = emptyList()
    )

    companion object {
        fun fromBpmnModel(model: BpmnModel): EditableModel = EditableModel(
            processId = model.processId,
            processName = model.processName,
            nodes = model.nodes.map { EditableNode.fromBpmnNode(it) }.toMutableList(),
            edges = model.edges.map { EditableEdge.fromBpmnEdge(it) }.toMutableList()
        )
    }
}
