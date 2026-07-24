package com.rickytech.bpmn.viewer.ui

import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableEdge
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.ui.property.FormBuilder
import com.rickytech.bpmn.viewer.ui.property.ListenerEditor
import com.rickytech.bpmn.viewer.ui.property.VariableMappingEditor
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

/**
 * 属性编辑面板：根据当前选中的节点 / 连线动态展示可编辑表单。
 *
 * - 节点支持基本信息、实现配置、用户任务、脚本、调用活动、多实例、定时器、消息/信号/错误等分组；
 * - 连线支持名称与条件表达式；
 * - 字段失焦或勾选状态变化时生成 [ChangePropertyCommand] / [ChangeEdgePropertyCommand]
 *   通过 [CommandStack] 执行，从而支持 undo / redo；
 * - 通过 `updating` 标志位避免回填值时再次触发提交。
 */
class BpmnPropertyPanel(
    private val editModel: EditableModel,
    private val commandStack: CommandStack
) : JPanel() {

    private var currentNodeId: String? = null
    private var currentEdgeId: String? = null
    private var updating: Boolean = false

    private val contentPanel: JPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = FormBuilder.BG
    }

    private val emptyHint: JLabel = JLabel("选择节点或连线查看属性", SwingConstants.CENTER).apply {
        foreground = FormBuilder.HINT_FG
        border = BorderFactory.createEmptyBorder(24, 8, 24, 8)
    }

    private val scrollPane: JScrollPane = JScrollPane(contentPanel).apply {
        border = null
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        verticalScrollBar.unitIncrement = 16
    }

    private val variableMappingEditor = VariableMappingEditor(editModel, commandStack) { updating }
    private val listenerEditor = ListenerEditor(editModel, commandStack) { updating }

    init {
        layout = BorderLayout()
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        background = FormBuilder.BG
        add(scrollPane, BorderLayout.CENTER)
        showEmpty()
        // 注意：commandStack.onStateChanged 由 BpmnFileEditor 统一编排，
        // 这里不再自行覆盖，避免与 BpmnEditGraphPanel 的回调互相覆盖。
    }

    // ---------------- public api ----------------

    /** 显示节点属性。 */
    fun showNodeProperties(node: EditableNode) {
        currentNodeId = node.id
        currentEdgeId = null
        rebuildForNode(node)
    }

    /** 显示连线属性。 */
    fun showEdgeProperties(edge: EditableEdge) {
        currentNodeId = null
        currentEdgeId = edge.id
        rebuildForEdge(edge)
    }

    /** 清空面板，恢复空提示。 */
    fun clear() {
        currentNodeId = null
        currentEdgeId = null
        showEmpty()
    }

    /**
     * 当模型发生外部变更（来自 CommandStack）时，重新拉取当前选中实体的最新值并刷新表单。
     * 由 [BpmnFileEditor] 在 onStateChanged 中调用。
     */
    fun refreshFromModel() {
        SwingUtilities.invokeLater { refreshCurrentSelection() }
    }

    // ---------------- rebuild ----------------

    private fun showEmpty() {
        contentPanel.removeAll()
        contentPanel.add(emptyHint)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun refreshCurrentSelection() {
        val nodeId = currentNodeId
        val edgeId = currentEdgeId
        if (nodeId != null) {
            val node = editModel.nodes.firstOrNull { it.id == nodeId }
            if (node != null) {
                rebuildForNode(node)
            } else {
                clear()
            }
        } else if (edgeId != null) {
            val edge = editModel.edges.firstOrNull { it.id == edgeId }
            if (edge != null) {
                rebuildForEdge(edge)
            } else {
                clear()
            }
        }
    }

    private fun rebuildForNode(node: EditableNode) {
        updating = true
        try {
            contentPanel.removeAll()

            // 1. 基本信息
            buildBasicSection(node)

            // 2. 实现配置
            if (node.type == BpmnNodeType.SERVICE_TASK ||
                node.type == BpmnNodeType.SEND_TASK ||
                node.type == BpmnNodeType.MAIL_TASK ||
                node.type == BpmnNodeType.BUSINESS_RULE_TASK
            ) {
                buildImplementationSection(node)
            }

            // 2.1 手工任务（仅支持异步执行配置）
            if (node.type == BpmnNodeType.MANUAL_TASK) {
                buildManualTaskSection(node)
            }

            // 3. 用户任务
            if (node.type == BpmnNodeType.USER_TASK) {
                buildUserTaskSection(node)
            }

            // 4. 脚本任务（SCRIPT_TASK 必须；SERVICE_TASK 也支持脚本式实现）
            if (node.type == BpmnNodeType.SCRIPT_TASK ||
                node.type == BpmnNodeType.SERVICE_TASK
            ) {
                buildScriptSection(node)
            }

            // 4.1 边界事件附属配置
            if (isBoundaryEvent(node.type)) {
                buildBoundarySection(node)
            }

            // 5. 调用活动
            if (node.type == BpmnNodeType.CALL_ACTIVITY) {
                buildCallActivitySection(node)
                variableMappingEditor.buildVariableMappingSection(node, contentPanel)
            }

            // 6. 多实例
            if (node.isSequential != null) {
                buildMultiInstanceSection(node)
            }

            // 7. 定时器
            if (node.type == BpmnNodeType.TIMER_START_EVENT ||
                node.type == BpmnNodeType.BOUNDARY_TIMER ||
                node.type == BpmnNodeType.INTERMEDIATE_TIMER_CATCH
            ) {
                buildTimerSection(node)
            }

            // 8. 消息 / 信号 / 错误
            if (isMessageEvent(node.type) ||
                isSignalEvent(node.type) ||
                isErrorEvent(node.type)
            ) {
                buildEventRefSection(node)
            }

            // 9. 执行监听器：任务类型与 Call Activity 均可配置
            if (isTaskOrCallActivity(node.type)) {
                listenerEditor.buildExecutionListenerSection(node, contentPanel)
            }

            // 10. 任务监听器：仅 USER_TASK
            if (node.type == BpmnNodeType.USER_TASK) {
                listenerEditor.buildTaskListenerSection(node, contentPanel)
            }

            contentPanel.add(Box.createVerticalGlue())
            contentPanel.revalidate()
            contentPanel.repaint()
            // 同时刷新外层容器与 viewport，避免 BoxLayout 在频繁 removeAll 后偶发不重排。
            scrollPane.viewport.revalidate()
            scrollPane.repaint()
            this.revalidate()
            this.repaint()
        } finally {
            updating = false
        }
    }

    private fun rebuildForEdge(edge: EditableEdge) {
        updating = true
        try {
            contentPanel.removeAll()
            buildEdgeBasicSection(edge)
            buildEdgeConditionSection(edge)
            contentPanel.add(Box.createVerticalGlue())
            contentPanel.revalidate()
            contentPanel.repaint()
            scrollPane.viewport.revalidate()
            scrollPane.repaint()
            this.revalidate()
            this.repaint()
        } finally {
            updating = false
        }
    }

    // ---------------- node sections ----------------

    private fun buildBasicSection(node: EditableNode) {
        val section = FormBuilder.createSection("基本信息")
        val idField = FormBuilder.createReadOnlyField(node.id)
        val nameField = JTextField(node.name)
        FormBuilder.bindNodeTextField(nameField, node.id, "name", commandStack, editModel, { updating }) { current(node.id)?.name ?: "" }
        val typeLabel = JLabel(node.type.name).apply { foreground = FormBuilder.VALUE_FG }

        FormBuilder.addField(section, 0, "ID", idField)
        FormBuilder.addField(section, 1, "名称", nameField)
        FormBuilder.addField(section, 2, "类型", typeLabel)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildImplementationSection(node: EditableNode) {
        val section = FormBuilder.createSection("实现配置")
        val classField = JTextField(node.javaClass ?: "")
        FormBuilder.bindNodeTextField(classField, node.id, "javaClass", commandStack, editModel, { updating }) { current(node.id)?.javaClass }

        val delegateField = JTextField(node.delegateExpression ?: "")
        FormBuilder.bindNodeTextField(delegateField, node.id, "delegateExpression", commandStack, editModel, { updating }) { current(node.id)?.delegateExpression }

        val asyncBox = JCheckBox("启用异步执行", node.isAsync).apply { background = FormBuilder.BG }
        FormBuilder.bindNodeCheckBox(asyncBox, node.id, "isAsync", commandStack, editModel, { updating }) { current(node.id)?.isAsync ?: false }

        val formKeyField = JTextField(node.formKey ?: "")
        FormBuilder.bindNodeTextField(formKeyField, node.id, "formKey", commandStack, editModel, { updating }) { current(node.id)?.formKey }

        FormBuilder.addField(section, 0, "Java类", classField)
        FormBuilder.addField(section, 1, "委托表达式", delegateField)
        FormBuilder.addField(section, 2, "异步", asyncBox)
        FormBuilder.addField(section, 3, "表单Key", formKeyField)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildManualTaskSection(node: EditableNode) {
        val section = FormBuilder.createSection("手工任务")
        val asyncBox = JCheckBox("启用异步执行", node.isAsync).apply { background = FormBuilder.BG }
        FormBuilder.bindNodeCheckBox(asyncBox, node.id, "isAsync", commandStack, editModel, { updating }) { current(node.id)?.isAsync ?: false }
        FormBuilder.addField(section, 0, "异步", asyncBox)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildBoundarySection(node: EditableNode) {
        val section = FormBuilder.createSection("边界事件")
        val attachedField = JTextField(node.attachedToRef ?: "")
        FormBuilder.bindNodeTextField(attachedField, node.id, "attachedToRef", commandStack, editModel, { updating }) { current(node.id)?.attachedToRef }

        val cancelBox = JCheckBox("中断附属活动（cancelActivity）", node.cancelActivity).apply { background = FormBuilder.BG }
        FormBuilder.bindNodeCheckBox(cancelBox, node.id, "cancelActivity", commandStack, editModel, { updating }) { current(node.id)?.cancelActivity ?: true }

        FormBuilder.addField(section, 0, "附属节点", attachedField)
        FormBuilder.addField(section, 1, "中断活动", cancelBox)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildUserTaskSection(node: EditableNode) {
        val section = FormBuilder.createSection("用户任务")
        val assigneeField = JTextField(node.assignee ?: "")
        FormBuilder.bindNodeTextField(assigneeField, node.id, "assignee", commandStack, editModel, { updating }) { current(node.id)?.assignee }

        val candUsers = JTextField(node.candidateUsers ?: "")
        FormBuilder.bindNodeTextField(candUsers, node.id, "candidateUsers", commandStack, editModel, { updating }) { current(node.id)?.candidateUsers }

        val candGroups = JTextField(node.candidateGroups ?: "")
        FormBuilder.bindNodeTextField(candGroups, node.id, "candidateGroups", commandStack, editModel, { updating }) { current(node.id)?.candidateGroups }

        val formKey = JTextField(node.formKey ?: "")
        FormBuilder.bindNodeTextField(formKey, node.id, "formKey", commandStack, editModel, { updating }) { current(node.id)?.formKey }

        FormBuilder.addField(section, 0, "处理人", assigneeField)
        FormBuilder.addField(section, 1, "候选用户", candUsers)
        FormBuilder.addField(section, 2, "候选组", candGroups)
        FormBuilder.addField(section, 3, "表单Key", formKey)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildScriptSection(node: EditableNode) {
        val section = FormBuilder.createSection("脚本配置")
        val formatField = JTextField(node.scriptFormat ?: "")
        FormBuilder.bindNodeTextField(formatField, node.id, "scriptFormat", commandStack, editModel, { updating }) { current(node.id)?.scriptFormat }

        val scriptArea = JTextArea(node.scriptContent ?: "", 4, 20).apply {
            lineWrap = true
            wrapStyleWord = true
            tabSize = 4
        }
        FormBuilder.bindNodeTextArea(scriptArea, node.id, "scriptContent", commandStack, editModel, { updating }) { current(node.id)?.scriptContent }
        val scriptScroll = JScrollPane(scriptArea).apply {
            preferredSize = Dimension(220, 90)
        }

        FormBuilder.addField(section, 0, "脚本格式", formatField)
        FormBuilder.addField(section, 1, "脚本内容", scriptScroll)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildCallActivitySection(node: EditableNode) {
        val section = FormBuilder.createSection("调用活动")
        val calledField = JTextField(node.calledElement ?: "")
        FormBuilder.bindNodeTextField(calledField, node.id, "calledElement", commandStack, editModel, { updating }) { current(node.id)?.calledElement }
        FormBuilder.addField(section, 0, "调用元素", calledField)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildMultiInstanceSection(node: EditableNode) {
        val section = FormBuilder.createSection("多实例")
        val seq = node.isSequential == true
        val seqBox = JCheckBox("串行执行（取消则为并行）", seq).apply { background = FormBuilder.BG }
        FormBuilder.bindNodeCheckBoxNullable(seqBox, node.id, "isSequential", commandStack, editModel, { updating }) { current(node.id)?.isSequential }

        val cardField = JTextField(node.loopCardinality ?: "")
        FormBuilder.bindNodeTextField(cardField, node.id, "loopCardinality", commandStack, editModel, { updating }) { current(node.id)?.loopCardinality }

        val dataInputField = JTextField(node.loopDataInputRef ?: "")
        FormBuilder.bindNodeTextField(dataInputField, node.id, "loopDataInputRef", commandStack, editModel, { updating }) { current(node.id)?.loopDataInputRef }

        val itemField = JTextField(node.inputDataItem ?: "")
        FormBuilder.bindNodeTextField(itemField, node.id, "inputDataItem", commandStack, editModel, { updating }) { current(node.id)?.inputDataItem }

        val completionConditionField = JTextField(node.completionCondition)
        FormBuilder.bindNodeTextField(completionConditionField, node.id, "completionCondition", commandStack, editModel, { updating }) { current(node.id)?.completionCondition }

        val loopConditionField = JTextField(node.loopCondition)
        FormBuilder.bindNodeTextField(loopConditionField, node.id, "loopCondition", commandStack, editModel, { updating }) { current(node.id)?.loopCondition }

        FormBuilder.addField(section, 0, "串行/并行", seqBox)
        FormBuilder.addField(section, 1, "循环基数", cardField)
        FormBuilder.addField(section, 2, "数据输入引用", dataInputField)
        FormBuilder.addField(section, 3, "元素变量", itemField)
        FormBuilder.addField(section, 4, "完成条件", completionConditionField)
        FormBuilder.addField(section, 5, "循环条件", loopConditionField)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildTimerSection(node: EditableNode) {
        val section = FormBuilder.createSection("定时器配置")
        val durField = JTextField(node.timerDuration ?: "")
        FormBuilder.bindNodeTextField(durField, node.id, "timerDuration", commandStack, editModel, { updating }) { current(node.id)?.timerDuration }

        val cycleField = JTextField(node.timeCycle ?: "")
        FormBuilder.bindNodeTextField(cycleField, node.id, "timeCycle", commandStack, editModel, { updating }) { current(node.id)?.timeCycle }

        val dateField = JTextField(node.timeDate ?: "")
        FormBuilder.bindNodeTextField(dateField, node.id, "timeDate", commandStack, editModel, { updating }) { current(node.id)?.timeDate }

        FormBuilder.addField(section, 0, "持续时间", durField)
        FormBuilder.addField(section, 1, "循环表达式", cycleField)
        FormBuilder.addField(section, 2, "日期", dateField)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildEventRefSection(node: EditableNode) {
        val section = FormBuilder.createSection("消息 / 信号 / 错误")
        var row = 0
        if (isMessageEvent(node.type)) {
            val f = JTextField(node.messageRef ?: "")
            FormBuilder.bindNodeTextField(f, node.id, "messageRef", commandStack, editModel, { updating }) { current(node.id)?.messageRef }
            FormBuilder.addField(section, row++, "消息引用", f)
        }
        if (isSignalEvent(node.type)) {
            val f = JTextField(node.signalRef ?: "")
            FormBuilder.bindNodeTextField(f, node.id, "signalRef", commandStack, editModel, { updating }) { current(node.id)?.signalRef }
            FormBuilder.addField(section, row++, "信号引用", f)
        }
        if (isErrorEvent(node.type)) {
            val f = JTextField(node.errorRef ?: "")
            FormBuilder.bindNodeTextField(f, node.id, "errorRef", commandStack, editModel, { updating }) { current(node.id)?.errorRef }
            FormBuilder.addField(section, row++, "错误引用", f)

            val codeField = JTextField(node.errorCode ?: "")
            FormBuilder.bindNodeTextField(codeField, node.id, "errorCode", commandStack, editModel, { updating }) { current(node.id)?.errorCode }
            FormBuilder.addField(section, row++, "错误码", codeField)
        }
        if (row > 0) FormBuilder.addSection(contentPanel, section)
    }

    // ---------------- edge sections ----------------

    private fun buildEdgeBasicSection(edge: EditableEdge) {
        val section = FormBuilder.createSection("连线信息")
        val idField = FormBuilder.createReadOnlyField(edge.id)
        val nameField = JTextField(edge.name ?: "")
        FormBuilder.bindEdgeTextField(nameField, edge.id, "name", commandStack, editModel, { updating }) { currentEdge(edge.id)?.name }

        FormBuilder.addField(section, 0, "ID", idField)
        FormBuilder.addField(section, 1, "名称", nameField)
        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildEdgeConditionSection(edge: EditableEdge) {
        val section = FormBuilder.createSection("条件表达式")
        val area = JTextArea(edge.conditionExpression ?: "", 3, 20).apply {
            lineWrap = true
            wrapStyleWord = true
        }
        FormBuilder.bindEdgeTextArea(area, edge.id, "conditionExpression", commandStack, editModel, { updating }) { currentEdge(edge.id)?.conditionExpression }
        val scroll = JScrollPane(area).apply { preferredSize = Dimension(220, 70) }
        FormBuilder.addField(section, 0, "表达式", scroll)
        FormBuilder.addSection(contentPanel, section)
    }

    // ---------------- helpers ----------------

    private fun current(nodeId: String): EditableNode? =
        editModel.nodes.firstOrNull { it.id == nodeId }

    private fun currentEdge(edgeId: String): EditableEdge? =
        editModel.edges.firstOrNull { it.id == edgeId }

    // ---------------- type predicates ----------------

    private fun isMessageEvent(type: BpmnNodeType): Boolean = when (type) {
        BpmnNodeType.MESSAGE_START_EVENT,
        BpmnNodeType.BOUNDARY_MESSAGE,
        BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH -> true
        else -> false
    }

    private fun isSignalEvent(type: BpmnNodeType): Boolean = when (type) {
        BpmnNodeType.SIGNAL_START_EVENT,
        BpmnNodeType.BOUNDARY_SIGNAL,
        BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH,
        BpmnNodeType.INTERMEDIATE_SIGNAL_THROW -> true
        else -> false
    }

    private fun isErrorEvent(type: BpmnNodeType): Boolean = when (type) {
        BpmnNodeType.ERROR_START_EVENT,
        BpmnNodeType.ERROR_END_EVENT,
        BpmnNodeType.BOUNDARY_ERROR -> true
        else -> false
    }

    private fun isBoundaryEvent(type: BpmnNodeType): Boolean = when (type) {
        BpmnNodeType.BOUNDARY_TIMER,
        BpmnNodeType.BOUNDARY_ERROR,
        BpmnNodeType.BOUNDARY_MESSAGE,
        BpmnNodeType.BOUNDARY_SIGNAL,
        BpmnNodeType.BOUNDARY_CANCEL,
        BpmnNodeType.BOUNDARY_COMPENSATE -> true
        else -> false
    }

    private fun isTaskOrCallActivity(type: BpmnNodeType): Boolean = when (type) {
        BpmnNodeType.SERVICE_TASK,
        BpmnNodeType.USER_TASK,
        BpmnNodeType.MANUAL_TASK,
        BpmnNodeType.SCRIPT_TASK,
        BpmnNodeType.BUSINESS_RULE_TASK,
        BpmnNodeType.RECEIVE_TASK,
        BpmnNodeType.SEND_TASK,
        BpmnNodeType.MAIL_TASK,
        BpmnNodeType.CALL_ACTIVITY -> true
        else -> false
    }
}
