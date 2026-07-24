package com.rickytech.bpmn.viewer.ui.property

import com.rickytech.bpmn.viewer.edit.ChangeEdgePropertyCommand
import com.rickytech.bpmn.viewer.edit.ChangePropertyCommand
import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableModel
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.ActionListener
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.UIManager
import javax.swing.border.TitledBorder

/**
 * 表单构建工具：提供属性面板的通用 UI 构建方法和颜色/样式常量。
 */
object FormBuilder {

    // ---------------- 颜色/样式常量 ----------------

    val BG: Color = UIManager.getColor("Panel.background") ?: Color(0xFD, 0xFD, 0xFD)
    val KEY_FG = Color(0x33, 0x33, 0x33)
    val VALUE_FG = Color(0x55, 0x55, 0x55)
    val HINT_FG = Color(0x99, 0x99, 0x99)
    val READONLY_BG = Color(0xF1, 0xF3, 0xF5)
    val IN_FG = Color(0x1E, 0x6F, 0xCC)
    val OUT_FG = Color(0xC2, 0x6B, 0x1F)

    // ---------------- 布局辅助方法 ----------------

    fun createSection(title: String): JPanel {
        val panel = JPanel(GridBagLayout()).apply {
            background = BG
            alignmentX = JPanel.LEFT_ALIGNMENT
        }
        panel.border = BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title,
            TitledBorder.LEFT,
            TitledBorder.TOP
        )
        return panel
    }

    fun addSection(contentPanel: JPanel, panel: JPanel) {
        panel.maximumSize = Dimension(Int.MAX_VALUE, panel.preferredSize.height)
        contentPanel.add(panel)
        contentPanel.add(Box.createVerticalStrut(6))
    }

    fun addField(panel: JPanel, row: Int, label: String, component: JComponent) {
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = row
        gbc.anchor = GridBagConstraints.WEST
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        gbc.insets = Insets(4, 6, 4, 6)
        panel.add(JLabel(label).apply { foreground = KEY_FG }, gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        gbc.insets = Insets(4, 0, 4, 6)
        panel.add(component, gbc)
    }

    fun createReadOnlyField(text: String): JTextField {
        return JTextField(text).apply {
            isEditable = false
            background = READONLY_BG
            foreground = VALUE_FG
        }
    }

    // ---------------- 绑定方法 ----------------

    fun bindNodeTextField(
        field: JTextField,
        nodeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> String?
    ) {
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (updating()) return
                val oldValue = getter()
                val typed = field.text
                val newValue: String? = if (propertyName == "name") {
                    typed
                } else {
                    typed.takeIf { it.isNotBlank() }
                }
                if (oldValue != newValue) {
                    commandStack.execute(
                        ChangePropertyCommand(nodeId, propertyName, oldValue, newValue),
                        editModel
                    )
                }
            }
        })
    }

    fun bindNodeTextArea(
        area: JTextArea,
        nodeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> String?
    ) {
        area.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (updating()) return
                val oldValue = getter()
                val newValue = area.text.takeIf { it.isNotBlank() }
                if (oldValue != newValue) {
                    commandStack.execute(
                        ChangePropertyCommand(nodeId, propertyName, oldValue, newValue),
                        editModel
                    )
                }
            }
        })
    }

    fun bindNodeCheckBox(
        box: JCheckBox,
        nodeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> Boolean
    ) {
        box.addActionListener(ActionListener {
            if (updating()) return@ActionListener
            val oldValue = getter()
            val newValue = box.isSelected
            if (oldValue != newValue) {
                commandStack.execute(
                    ChangePropertyCommand(nodeId, propertyName, oldValue, newValue),
                    editModel
                )
            }
        })
    }

    /** 用于 isSequential 这类可空 Boolean，节点本身存在即视为已声明多实例，仅切换 true/false。 */
    fun bindNodeCheckBoxNullable(
        box: JCheckBox,
        nodeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> Boolean?
    ) {
        box.addActionListener(ActionListener {
            if (updating()) return@ActionListener
            val oldValue = getter()
            val newValue: Boolean? = box.isSelected
            if (oldValue != newValue) {
                commandStack.execute(
                    ChangePropertyCommand(nodeId, propertyName, oldValue, newValue),
                    editModel
                )
            }
        })
    }

    fun bindEdgeTextField(
        field: JTextField,
        edgeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> String?
    ) {
        field.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (updating()) return
                val oldValue = getter()
                val newValue = field.text.takeIf { it.isNotBlank() }
                if (oldValue != newValue) {
                    commandStack.execute(
                        ChangeEdgePropertyCommand(edgeId, propertyName, oldValue, newValue),
                        editModel
                    )
                }
            }
        })
    }

    fun bindEdgeTextArea(
        area: JTextArea,
        edgeId: String,
        propertyName: String,
        commandStack: CommandStack,
        editModel: EditableModel,
        updating: () -> Boolean,
        getter: () -> String?
    ) {
        area.addFocusListener(object : FocusAdapter() {
            override fun focusLost(e: FocusEvent) {
                if (updating()) return
                val oldValue = getter()
                val newValue = area.text.takeIf { it.isNotBlank() }
                if (oldValue != newValue) {
                    commandStack.execute(
                        ChangeEdgePropertyCommand(edgeId, propertyName, oldValue, newValue),
                        editModel
                    )
                }
            }
        })
    }
}
