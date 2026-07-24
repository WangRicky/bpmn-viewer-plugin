package com.rickytech.bpmn.viewer.ui.property

import com.rickytech.bpmn.viewer.edit.AddListenerCommand
import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.edit.ListenerKind
import com.rickytech.bpmn.viewer.edit.RemoveListenerCommand
import com.rickytech.bpmn.viewer.edit.UpdateListenerCommand
import com.rickytech.bpmn.viewer.model.ListenerDef
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 监听器编辑器：管理节点的执行监听器和任务监听器。
 *
 * - 逐行列出当前节点的 [ListenerDef]，提供事件 / 实现类型 / 实现值 / 删除按钮；
 * - 底部提供「添加监听器」快捷按钮；
 * - 所有变更都通过 [CommandStack] 以命令形式提交，支持 undo / redo。
 */
class ListenerEditor(
    private val editModel: EditableModel,
    private val commandStack: CommandStack,
    private val updating: () -> Boolean
) {

    private fun current(nodeId: String): EditableNode? =
        editModel.nodes.firstOrNull { it.id == nodeId }

    /** 构建执行监听器 section 并添加到 contentPanel。 */
    fun buildExecutionListenerSection(node: EditableNode, contentPanel: JPanel) {
        buildListenerSection(
            node = node,
            contentPanel = contentPanel,
            title = "执行监听器",
            kind = ListenerKind.EXECUTION,
            listeners = node.executionListeners,
            eventOptions = EXECUTION_EVENTS,
            defaultEvent = "start"
        )
    }

    /** 构建任务监听器 section 并添加到 contentPanel。 */
    fun buildTaskListenerSection(node: EditableNode, contentPanel: JPanel) {
        buildListenerSection(
            node = node,
            contentPanel = contentPanel,
            title = "任务监听器",
            kind = ListenerKind.TASK,
            listeners = node.taskListeners,
            eventOptions = TASK_EVENTS,
            defaultEvent = "create"
        )
    }

    private fun buildListenerSection(
        node: EditableNode,
        contentPanel: JPanel,
        title: String,
        kind: String,
        listeners: List<ListenerDef>,
        eventOptions: Array<String>,
        defaultEvent: String
    ) {
        val section = FormBuilder.createSection(title)
        section.layout = BoxLayout(section, BoxLayout.Y_AXIS)

        if (listeners.isEmpty()) {
            section.add(JLabel("暂无监听器").apply {
                foreground = FormBuilder.HINT_FG
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            })
        } else {
            val header = JPanel(GridBagLayout()).apply {
                background = FormBuilder.BG
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            buildListenerHeader(header)
            section.add(header)

            listeners.forEachIndexed { index, listener ->
                section.add(createListenerRow(node.id, kind, index, listener, eventOptions))
            }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            background = FormBuilder.BG
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        val addBtn = JButton("+ 添加监听器").apply {
            addActionListener {
                if (updating()) return@addActionListener
                commandStack.execute(
                    AddListenerCommand(
                        node.id,
                        kind,
                        ListenerDef(event = defaultEvent, implementation = "", implementationType = "class")
                    ),
                    editModel
                )
            }
        }
        buttonRow.add(addBtn)
        section.add(buttonRow)

        FormBuilder.addSection(contentPanel, section)
    }

    private fun buildListenerHeader(row: JPanel) {
        val gbc = GridBagConstraints()
        gbc.gridy = 0
        gbc.insets = Insets(2, 6, 2, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(JLabel("事件").apply {
            foreground = FormBuilder.KEY_FG
            preferredSize = Dimension(60, preferredSize.height)
        }, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.0
        row.add(JLabel("类型").apply {
            foreground = FormBuilder.KEY_FG
            preferredSize = Dimension(140, preferredSize.height)
        }, gbc)

        gbc.gridx = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(JLabel("实现").apply { foreground = FormBuilder.KEY_FG }, gbc)

        gbc.gridx = 3
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(Box.createHorizontalStrut(28), gbc)
    }

    /**
     * 一行可编辑的监听器：事件组合框 + 实现类型下拉 + 实现值文本框 + 删除按钮。
     * 各输入控件在变更后以 [UpdateListenerCommand] 全量替换。
     */
    private fun createListenerRow(
        nodeId: String,
        kind: String,
        index: Int,
        listener: ListenerDef,
        eventOptions: Array<String>
    ): JPanel {
        val row = JPanel(GridBagLayout()).apply {
            background = FormBuilder.BG
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        val eventCombo = JComboBox(eventOptions).apply {
            isEditable = true
            selectedItem = listener.event
        }
        val typeCombo = JComboBox(IMPL_TYPES).apply {
            isEditable = false
            selectedItem = listener.implementationType.ifBlank { "class" }
        }
        val implField = JTextField(listener.implementation)

        val updater: () -> Unit = upd@{
            if (updating()) return@upd
            val n = current(nodeId) ?: return@upd
            val list = if (kind == ListenerKind.TASK) n.taskListeners else n.executionListeners
            val cur = list.getOrNull(index) ?: return@upd
            val newListener = cur.copy(
                event = (eventCombo.editor.item?.toString() ?: cur.event).trim(),
                implementationType = (typeCombo.selectedItem?.toString() ?: cur.implementationType),
                implementation = implField.text
            )
            if (cur != newListener) {
                commandStack.execute(
                    UpdateListenerCommand(nodeId, kind, index, cur, newListener),
                    editModel
                )
            }
        }

        eventCombo.addActionListener { if (!updating()) updater() }
        (eventCombo.editor.editorComponent as? JTextField)?.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = updater()
        })
        typeCombo.addActionListener { if (!updating()) updater() }
        implField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = updater()
        })

        val deleteBtn = JButton("×").apply {
            margin = Insets(0, 6, 0, 6)
            toolTipText = "删除监听器"
            addActionListener {
                if (updating()) return@addActionListener
                val n = current(nodeId) ?: return@addActionListener
                val list = if (kind == ListenerKind.TASK) n.taskListeners else n.executionListeners
                val target = list.getOrNull(index) ?: return@addActionListener
                commandStack.execute(
                    RemoveListenerCommand(nodeId, kind, index, target),
                    editModel
                )
            }
        }

        val gbc = GridBagConstraints()
        gbc.gridy = 0
        gbc.insets = Insets(2, 6, 2, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        eventCombo.preferredSize = Dimension(80, eventCombo.preferredSize.height)
        row.add(eventCombo, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.0
        typeCombo.preferredSize = Dimension(140, typeCombo.preferredSize.height)
        row.add(typeCombo, gbc)

        gbc.gridx = 2
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(implField, gbc)

        gbc.gridx = 3
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(deleteBtn, gbc)

        return row
    }

    companion object {
        private val EXECUTION_EVENTS: Array<String> = arrayOf("start", "end", "take")
        private val TASK_EVENTS: Array<String> = arrayOf("create", "assignment", "complete", "delete")
        private val IMPL_TYPES: Array<String> = arrayOf("class", "delegateExpression", "expression")
    }
}
