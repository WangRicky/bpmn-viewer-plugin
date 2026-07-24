package com.rickytech.bpmn.viewer.ui.property

import com.rickytech.bpmn.viewer.edit.AddVariableMappingCommand
import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.edit.RemoveVariableMappingCommand
import com.rickytech.bpmn.viewer.edit.UpdateVariableMappingCommand
import com.rickytech.bpmn.viewer.model.VariableMapping
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
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * 变量映射编辑器：管理 Call Activity 节点的 activiti:in / activiti:out 变量映射。
 *
 * - 逐行列出当前节点的 [VariableMapping]，提供方向、源变量、目标变量、删除按钮；
 * - 底部提供「添加输入映射 / 添加输出映射」两个快捷按钮；
 * - 所有变更都通过 [CommandStack] 以命令形式提交，支持 undo / redo。
 */
class VariableMappingEditor(
    private val editModel: EditableModel,
    private val commandStack: CommandStack,
    private val updating: () -> Boolean
) {

    private fun current(nodeId: String): EditableNode? =
        editModel.nodes.firstOrNull { it.id == nodeId }

    /**
     * 构建变量映射编辑 section 并添加到 contentPanel。
     */
    fun buildVariableMappingSection(node: EditableNode, contentPanel: JPanel) {
        val section = FormBuilder.createSection("变量映射")
        section.layout = BoxLayout(section, BoxLayout.Y_AXIS)

        if (node.variableMappings.isEmpty()) {
            section.add(JLabel("暂无变量映射").apply {
                foreground = FormBuilder.HINT_FG
                alignmentX = JPanel.LEFT_ALIGNMENT
                border = BorderFactory.createEmptyBorder(2, 4, 2, 4)
            })
        } else {
            val header = JPanel(GridBagLayout()).apply {
                background = FormBuilder.BG
                alignmentX = JPanel.LEFT_ALIGNMENT
            }
            buildMappingHeaderRow(header)
            section.add(header)

            node.variableMappings.forEachIndexed { index, mapping ->
                section.add(createMappingRow(node.id, index, mapping))
            }
        }

        val buttonRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 4)).apply {
            background = FormBuilder.BG
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        val addInBtn = JButton("+ 添加输入映射").apply {
            addActionListener {
                if (updating()) return@addActionListener
                commandStack.execute(
                    AddVariableMappingCommand(node.id, VariableMapping("in", "", "")),
                    editModel
                )
            }
        }
        val addOutBtn = JButton("+ 添加输出映射").apply {
            addActionListener {
                if (updating()) return@addActionListener
                commandStack.execute(
                    AddVariableMappingCommand(node.id, VariableMapping("out", "", "")),
                    editModel
                )
            }
        }
        buttonRow.add(addInBtn)
        buttonRow.add(addOutBtn)
        section.add(buttonRow)

        FormBuilder.addSection(contentPanel, section)
    }

    /**
     * 创建一行可编辑的变量映射：方向标签 + source 文本框 + target 文本框 + 删除按钮。
     * source / target 在失焦时以 [UpdateVariableMappingCommand] 全量替换。
     */
    private fun createMappingRow(nodeId: String, index: Int, mapping: VariableMapping): JPanel {
        val row = JPanel(GridBagLayout()).apply {
            background = FormBuilder.BG
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        val sourceField = JTextField(mapping.source)
        val targetField = JTextField(mapping.target)

        val updater: () -> Unit = upd@{
            if (updating()) return@upd
            val node = current(nodeId) ?: return@upd
            val current = node.variableMappings.getOrNull(index) ?: return@upd
            val newMapping = current.copy(
                source = sourceField.text,
                target = targetField.text
            )
            if (current != newMapping) {
                commandStack.execute(
                    UpdateVariableMappingCommand(nodeId, index, current, newMapping),
                    editModel
                )
            }
        }
        sourceField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = updater()
        })
        targetField.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) = updater()
        })

        val deleteBtn = JButton("×").apply {
            margin = Insets(0, 6, 0, 6)
            toolTipText = "删除映射"
            addActionListener {
                if (updating()) return@addActionListener
                val node = current(nodeId) ?: return@addActionListener
                val target = node.variableMappings.getOrNull(index) ?: return@addActionListener
                commandStack.execute(
                    RemoveVariableMappingCommand(nodeId, index, target),
                    editModel
                )
            }
        }

        buildMappingRowComponents(row, mapping.direction, sourceField, targetField, deleteBtn)
        return row
    }

    /** 表头辅助构建（以文本占位）。 */
    private fun buildMappingHeaderRow(row: JPanel) {
        val gbc = GridBagConstraints()
        gbc.gridy = 0
        gbc.insets = Insets(2, 6, 2, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(JLabel("方向").apply {
            foreground = FormBuilder.KEY_FG
            preferredSize = Dimension(40, preferredSize.height)
        }, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.5
        gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(JLabel("源变量").apply { foreground = FormBuilder.KEY_FG }, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.5
        row.add(JLabel("目标变量").apply { foreground = FormBuilder.KEY_FG }, gbc)

        gbc.gridx = 3
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(Box.createHorizontalStrut(28), gbc)
    }

    /** 实际可编辑行组件布局。 */
    private fun buildMappingRowComponents(
        row: JPanel,
        direction: String,
        sourceField: JTextField,
        targetField: JTextField,
        deleteBtn: JButton
    ) {
        val gbc = GridBagConstraints()
        gbc.gridy = 0
        gbc.insets = Insets(2, 6, 2, 4)
        gbc.anchor = GridBagConstraints.WEST

        gbc.gridx = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(JLabel(direction).apply {
            foreground = if (direction == "in") FormBuilder.IN_FG else FormBuilder.OUT_FG
            preferredSize = Dimension(40, preferredSize.height)
        }, gbc)

        gbc.gridx = 1
        gbc.weightx = 0.5
        gbc.fill = GridBagConstraints.HORIZONTAL
        row.add(sourceField, gbc)

        gbc.gridx = 2
        gbc.weightx = 0.5
        row.add(targetField, gbc)

        gbc.gridx = 3
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        row.add(deleteBtn, gbc)
    }
}
