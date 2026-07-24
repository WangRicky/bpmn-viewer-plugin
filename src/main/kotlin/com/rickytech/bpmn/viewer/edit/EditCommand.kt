package com.rickytech.bpmn.viewer.edit

import com.rickytech.bpmn.viewer.model.ListenerDef
import com.rickytech.bpmn.viewer.model.VariableMapping

/**
 * 编辑命令，统一支持 execute / undo / 描述。
 */
sealed interface EditCommand {
    fun execute(model: EditableModel)
    fun undo(model: EditableModel)
    fun description(): String
}

/**
 * 移动节点命令，记录前后位置以便撤销。
 */
class MoveNodeCommand(
    private val nodeId: String,
    private val fromX: Double,
    private val fromY: Double,
    private val toX: Double,
    private val toY: Double
) : EditCommand {
    override fun execute(model: EditableModel) {
        model.nodes.firstOrNull { it.id == nodeId }?.let {
            it.x = toX
            it.y = toY
        }
    }

    override fun undo(model: EditableModel) {
        model.nodes.firstOrNull { it.id == nodeId }?.let {
            it.x = fromX
            it.y = fromY
        }
    }

    override fun description(): String = "Move node $nodeId"
}

/**
 * 添加节点命令。
 */
class AddNodeCommand(private val node: EditableNode) : EditCommand {
    override fun execute(model: EditableModel) {
        model.nodes.add(node)
    }

    override fun undo(model: EditableModel) {
        model.nodes.removeAll { it.id == node.id }
    }

    override fun description(): String = "Add node ${node.id}"
}

/**
 * 删除节点命令，会同时删除关联连线，撤销时恢复。
 */
class RemoveNodeCommand(private val nodeId: String) : EditCommand {
    private var removedNode: EditableNode? = null
    private var removedEdges: List<EditableEdge> = emptyList()

    override fun execute(model: EditableModel) {
        removedNode = model.nodes.firstOrNull { it.id == nodeId }
        removedEdges = model.edges.filter { it.sourceRef == nodeId || it.targetRef == nodeId }.toList()
        model.nodes.removeAll { it.id == nodeId }
        model.edges.removeAll { it.sourceRef == nodeId || it.targetRef == nodeId }
    }

    override fun undo(model: EditableModel) {
        removedNode?.let { model.nodes.add(it) }
        model.edges.addAll(removedEdges)
    }

    override fun description(): String = "Remove node $nodeId"
}

/**
 * 修改节点属性命令。支持的属性见 when 分支。
 */
class ChangePropertyCommand(
    private val nodeId: String,
    private val propertyName: String,
    private val oldValue: Any?,
    private val newValue: Any?
) : EditCommand {
    override fun execute(model: EditableModel) {
        applyValue(model, newValue)
    }

    override fun undo(model: EditableModel) {
        applyValue(model, oldValue)
    }

    override fun description(): String = "Change $propertyName of $nodeId"

    private fun applyValue(model: EditableModel, value: Any?) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        when (propertyName) {
            "name" -> node.name = (value as? String) ?: ""
            "javaClass" -> node.javaClass = value as? String
            "delegateExpression" -> node.delegateExpression = value as? String
            "assignee" -> node.assignee = value as? String
            "candidateUsers" -> node.candidateUsers = value as? String
            "candidateGroups" -> node.candidateGroups = value as? String
            "calledElement" -> node.calledElement = value as? String
            "formKey" -> node.formKey = value as? String
            "scriptFormat" -> node.scriptFormat = value as? String
            "scriptContent" -> node.scriptContent = value as? String
            "isAsync" -> node.isAsync = (value as? Boolean) ?: false
            "isSequential" -> node.isSequential = value as? Boolean
            "loopCardinality" -> node.loopCardinality = value as? String
            "loopDataInputRef" -> node.loopDataInputRef = value as? String
            "inputDataItem" -> node.inputDataItem = value as? String
            "completionCondition" -> node.completionCondition = (value as? String) ?: ""
            "loopCondition" -> node.loopCondition = (value as? String) ?: ""
            "timerDuration" -> node.timerDuration = value as? String
            "timeCycle" -> node.timeCycle = value as? String
            "timeDate" -> node.timeDate = value as? String
            "messageRef" -> node.messageRef = value as? String
            "signalRef" -> node.signalRef = value as? String
            "errorRef" -> node.errorRef = value as? String
            "errorCode" -> node.errorCode = value as? String
            "attachedToRef" -> node.attachedToRef = value as? String
            "cancelActivity" -> node.cancelActivity = (value as? Boolean) ?: true
        }
    }
}

/**
 * 添加连线命令。
 */
class AddEdgeCommand(private val edge: EditableEdge) : EditCommand {
    override fun execute(model: EditableModel) {
        model.edges.add(edge)
    }

    override fun undo(model: EditableModel) {
        model.edges.removeAll { it.id == edge.id }
    }

    override fun description(): String = "Add edge ${edge.id}"
}

/**
 * 删除连线命令，撤销时恢复。
 */
class RemoveEdgeCommand(private val edgeId: String) : EditCommand {
    private var removedEdge: EditableEdge? = null

    override fun execute(model: EditableModel) {
        removedEdge = model.edges.firstOrNull { it.id == edgeId }
        model.edges.removeAll { it.id == edgeId }
    }

    override fun undo(model: EditableModel) {
        removedEdge?.let { model.edges.add(it) }
    }

    override fun description(): String = "Remove edge $edgeId"
}

/**
 * 修改连线属性命令。支持 name / conditionExpression。
 */
class ChangeEdgePropertyCommand(
    private val edgeId: String,
    private val propertyName: String,
    private val oldValue: Any?,
    private val newValue: Any?
) : EditCommand {
    override fun execute(model: EditableModel) {
        applyValue(model, newValue)
    }

    override fun undo(model: EditableModel) {
        applyValue(model, oldValue)
    }

    override fun description(): String = "Change $propertyName of edge $edgeId"

    private fun applyValue(model: EditableModel, value: Any?) {
        val edge = model.edges.firstOrNull { it.id == edgeId } ?: return
        when (propertyName) {
            "name" -> edge.name = value as? String
            "conditionExpression" -> edge.conditionExpression = value as? String
        }
    }
}

/**
 * 为 Call Activity 节点新增一条变量映射（activiti:in / activiti:out）。
 */
class AddVariableMappingCommand(
    private val nodeId: String,
    private val mapping: VariableMapping
) : EditCommand {
    override fun execute(model: EditableModel) {
        model.nodes.firstOrNull { it.id == nodeId }?.variableMappings?.add(mapping)
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        // 移除最后一条与 mapping 等值的项，避免误删早先的同值映射。
        val idx = node.variableMappings.indexOfLast { it == mapping }
        if (idx >= 0) node.variableMappings.removeAt(idx)
    }

    override fun description(): String = "Add variable mapping on $nodeId"
}

/**
 * 删除 Call Activity 节点指定下标的变量映射。
 */
class RemoveVariableMappingCommand(
    private val nodeId: String,
    private val index: Int,
    private val removed: VariableMapping
) : EditCommand {
    override fun execute(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        if (index in node.variableMappings.indices) {
            node.variableMappings.removeAt(index)
        }
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val safeIndex = index.coerceIn(0, node.variableMappings.size)
        node.variableMappings.add(safeIndex, removed)
    }

    override fun description(): String = "Remove variable mapping on $nodeId"
}

/**
 * 更新 Call Activity 节点指定下标的变量映射，整体替换。
 */
class UpdateVariableMappingCommand(
    private val nodeId: String,
    private val index: Int,
    private val oldMapping: VariableMapping,
    private val newMapping: VariableMapping
) : EditCommand {
    override fun execute(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        if (index in node.variableMappings.indices) {
            node.variableMappings[index] = newMapping
        }
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        if (index in node.variableMappings.indices) {
            node.variableMappings[index] = oldMapping
        }
    }

    override fun description(): String = "Update variable mapping on $nodeId"
}

/**
 * 监听器命令所属的列表类型。
 */
object ListenerKind {
    const val EXECUTION: String = "execution"
    const val TASK: String = "task"
}

private fun EditableNode.listenerListOf(kind: String): MutableList<ListenerDef> =
    if (kind == ListenerKind.TASK) taskListeners else executionListeners

/**
 * 为节点新增执行 / 任务监听器。
 */
class AddListenerCommand(
    private val nodeId: String,
    private val listenerType: String,
    private val listener: ListenerDef
) : EditCommand {
    override fun execute(model: EditableModel) {
        model.nodes.firstOrNull { it.id == nodeId }?.listenerListOf(listenerType)?.add(listener)
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val list = node.listenerListOf(listenerType)
        val idx = list.indexOfLast { it == listener }
        if (idx >= 0) list.removeAt(idx)
    }

    override fun description(): String = "Add $listenerType listener on $nodeId"
}

/**
 * 删除节点指定下标的监听器。
 */
class RemoveListenerCommand(
    private val nodeId: String,
    private val listenerType: String,
    private val index: Int,
    private val removed: ListenerDef
) : EditCommand {
    override fun execute(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val list = node.listenerListOf(listenerType)
        if (index in list.indices) list.removeAt(index)
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val list = node.listenerListOf(listenerType)
        val safeIndex = index.coerceIn(0, list.size)
        list.add(safeIndex, removed)
    }

    override fun description(): String = "Remove $listenerType listener on $nodeId"
}

/**
 * 整体替换节点指定下标的监听器。
 */
class UpdateListenerCommand(
    private val nodeId: String,
    private val listenerType: String,
    private val index: Int,
    private val oldListener: ListenerDef,
    private val newListener: ListenerDef
) : EditCommand {
    override fun execute(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val list = node.listenerListOf(listenerType)
        if (index in list.indices) list[index] = newListener
    }

    override fun undo(model: EditableModel) {
        val node = model.nodes.firstOrNull { it.id == nodeId } ?: return
        val list = node.listenerListOf(listenerType)
        if (index in list.indices) list[index] = oldListener
    }

    override fun description(): String = "Update $listenerType listener on $nodeId"
}
