package com.rickytech.bpmn.viewer.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.ListenerDef
import com.rickytech.bpmn.viewer.model.VariableMapping
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextPane
import javax.swing.SwingConstants
import javax.swing.UIManager
import javax.swing.border.EmptyBorder

/**
 * Right-side detail inspector. Renders a selected BpmnNode as a series of grouped
 * sections (basic info, implementation, assignment, multi-instance, variable mappings),
 * skipping any group whose fields are all empty so the panel stays compact.
 */
class BpmnDetailPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val titleLabel = JLabel("详情")
    private val contentPanel = JPanel()
    private val emptyLabel = JLabel("点击流程图中的节点或连线查看详情", SwingConstants.CENTER)

    companion object {
        private val BG = Color(0xFD, 0xFD, 0xFD)
        private val SECTION_TITLE = Color(0x1A, 0x73, 0xE8)
        private val SECTION_RULE = Color(0xE3, 0xE6, 0xEA)
        private val KEY_FG = Color(0x33, 0x33, 0x33)
        private val VALUE_FG = Color(0x55, 0x55, 0x55)
        private val LINK_FG = Color(0x1A, 0x73, 0xE8)
        private val TABLE_HEADER_BG = Color(0xF1, 0xF3, 0xF5)
        private val TABLE_BORDER = Color(0xDD, 0xE1, 0xE6)
        private val IN_TAG_BG = Color(0xE6, 0xF4, 0xEA)
        private val IN_TAG_FG = Color(0x1E, 0x8E, 0x3E)
        private val OUT_TAG_BG = Color(0xFC, 0xE8, 0xE6)
        private val OUT_TAG_FG = Color(0xC5, 0x39, 0x29)
    }

    init {
        border = EmptyBorder(8, 10, 8, 10)
        background = BG

        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
        titleLabel.border = EmptyBorder(0, 0, 8, 0)
        add(titleLabel, BorderLayout.NORTH)

        contentPanel.layout = BoxLayout(contentPanel, BoxLayout.Y_AXIS)
        contentPanel.background = BG

        val scrollPane = JScrollPane(contentPanel).apply {
            border = null
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            verticalScrollBar.unitIncrement = 16
        }
        add(scrollPane, BorderLayout.CENTER)

        emptyLabel.foreground = Color(0x99, 0x99, 0x99)
        contentPanel.add(emptyLabel)
    }

    fun showNodeDetail(node: BpmnNode) {
        contentPanel.removeAll()
        titleLabel.text = "节点详情"

        // 1. Basic
        addSection("基本信息", listOf(
            "名称" to node.name.ifBlank { node.id },
            "ID" to node.id,
            "类型" to node.type.name
        ).filterValues())

        // 2. Implementation
        val impl = mutableListOf<FieldRow>()
        node.javaClass?.let { impl += FieldRow("Java类", it, isClassLink = true) }
        node.delegateExpression?.let { impl += FieldRow("DelegateExpression", it) }
        node.calledElement?.let { impl += FieldRow("CalledElement", it, isProcessLink = true) }
        if (impl.isNotEmpty()) addSection("实现配置", impl)

        // 3. Assignment
        val assign = mutableListOf<FieldRow>()
        node.assignee?.let { assign += FieldRow("Assignee", it) }
        node.candidateUsers?.let { assign += FieldRow("CandidateUsers", it) }
        node.candidateGroups?.let { assign += FieldRow("CandidateGroups", it) }
        if (assign.isNotEmpty()) addSection("任务分配", assign)

        // 4. Multi-instance
        val mi = mutableListOf<FieldRow>()
        node.isSequential?.let { mi += FieldRow("isSequential", if (it) "true (串行)" else "false (并行)") }
        node.loopCardinality?.let { mi += FieldRow("loopCardinality", it) }
        node.loopDataInputRef?.let { mi += FieldRow("loopDataInputRef", it) }
        node.inputDataItem?.let { mi += FieldRow("inputDataItem", it) }
        if (node.completionCondition.isNotBlank()) mi += FieldRow("完成条件", node.completionCondition)
        if (node.loopCondition.isNotBlank()) mi += FieldRow("循环条件", node.loopCondition)
        if (mi.isNotEmpty()) addSection("多实例配置", mi)

        // 5. Timer configuration
        if (node.timerDuration != null || node.timeCycle != null || node.timeDate != null) {
            val timer = mutableListOf<FieldRow>()
            node.timerDuration?.let { timer += FieldRow("定时持续时间", it) }
            node.timeCycle?.let { timer += FieldRow("定时周期", it) }
            node.timeDate?.let { timer += FieldRow("定时日期", it) }
            if (timer.isNotEmpty()) addSection("定时器配置", timer)
        }

        // 6. Event configuration
        if (node.messageRef != null || node.signalRef != null || node.errorRef != null || node.attachedToRef != null) {
            val event = mutableListOf<FieldRow>()
            node.messageRef?.let { event += FieldRow("消息引用", it) }
            node.signalRef?.let { event += FieldRow("信号引用", it) }
            node.errorRef?.let { event += FieldRow("错误引用", it) }
            node.attachedToRef?.let { event += FieldRow("附属节点", it) }
            if (node.attachedToRef != null) {
                event += FieldRow("取消活动", if (node.cancelActivity) "是" else "否")
            }
            if (event.isNotEmpty()) addSection("事件配置", event)
        }

        // 7. Execution configuration
        if (node.isAsync || node.formKey != null || node.scriptFormat != null || node.scriptContent != null) {
            val exec = mutableListOf<FieldRow>()
            if (node.isAsync) exec += FieldRow("异步执行", "是")
            node.formKey?.let { exec += FieldRow("表单Key", it) }
            node.scriptFormat?.let { exec += FieldRow("脚本格式", it) }
            node.scriptContent?.let {
                val display = if (it.length > 200) it.substring(0, 200) + "..." else it
                exec += FieldRow("脚本内容", display)
            }
            if (exec.isNotEmpty()) addSection("执行配置", exec)
        }

        // 8. Variable mappings
        if (node.variableMappings.isNotEmpty()) {
            addMappingsSection(node.variableMappings)
        }

        // 9. Execution listeners
        if (node.executionListeners.isNotEmpty()) {
            addListenersSection("执行监听器", node.executionListeners)
        }

        // 10. Task listeners
        if (node.taskListeners.isNotEmpty()) {
            addListenersSection("任务监听器", node.taskListeners)
        }

        // tail spacer so the last section doesn't hug the bottom
        contentPanel.add(Box.createVerticalStrut(8))

        contentPanel.revalidate()
        contentPanel.repaint()
    }

    fun clear() {
        contentPanel.removeAll()
        contentPanel.add(emptyLabel)
        titleLabel.text = "详情"
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    /**
     * Renders details for a clicked sequence flow: id, optional name, the
     * source → target hop, and the full condition expression (which is
     * intentionally NOT drawn on the diagram itself).
     */
    fun showEdgeDetail(edge: BpmnEdge, source: BpmnNode?, target: BpmnNode?) {
        contentPanel.removeAll()
        titleLabel.text = "连线详情"

        val sourceLabel = source?.let { it.name.ifBlank { it.id } } ?: edge.sourceRef
        val targetLabel = target?.let { it.name.ifBlank { it.id } } ?: edge.targetRef

        // 1. Basic
        addSection("基本信息", listOf(
            "ID" to edge.id,
            "名称" to (edge.name ?: ""),
            "流向" to "$sourceLabel → $targetLabel",
            "sourceRef" to edge.sourceRef,
            "targetRef" to edge.targetRef
        ).filterValues())

        // 2. Condition expression (full text, no truncation).
        val cond = edge.conditionExpression?.takeIf { it.isNotBlank() }
        if (cond != null) {
            addSection("条件表达式", listOf(FieldRow("conditionExpression", cond)))
        }

        contentPanel.add(Box.createVerticalStrut(8))
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    // ---------- section building ----------

    private data class FieldRow(
        val key: String,
        val value: String,
        val isClassLink: Boolean = false,
        val isProcessLink: Boolean = false
    )

    /** Convert (key -> value) pairs into rows, skipping blank values. */
    private fun List<Pair<String, String>>.filterValues(): List<FieldRow> =
        mapNotNull { (k, v) -> v.takeIf { it.isNotBlank() }?.let { FieldRow(k, it) } }

    private fun addSection(title: String, rows: List<FieldRow>) {
        if (rows.isEmpty()) return
        contentPanel.add(buildSectionHeader(title))
        val body = JPanel(GridBagLayout()).apply {
            background = BG
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(2, 2, 10, 2)
        }
        val gc = GridBagConstraints().apply {
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(3, 0, 3, 8)
        }
        for ((idx, row) in rows.withIndex()) {
            gc.gridx = 0; gc.gridy = idx; gc.weightx = 0.0; gc.fill = GridBagConstraints.NONE
            body.add(buildKey(row.key), gc)

            gc.gridx = 1; gc.weightx = 1.0; gc.fill = GridBagConstraints.HORIZONTAL
            val valueComponent = when {
                row.isClassLink -> buildClassLink(row.value)
                row.isProcessLink -> buildProcessLink(row.value)
                else -> buildValue(row.value)
            }
            body.add(valueComponent, gc)
        }
        body.maximumSize = Dimension(Int.MAX_VALUE, body.preferredSize.height)
        contentPanel.add(body)
    }

    private fun addMappingsSection(mappings: List<VariableMapping>) {
        contentPanel.add(buildSectionHeader("变量映射"))

        val table = JPanel(GridBagLayout()).apply {
            background = BG
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                EmptyBorder(2, 2, 10, 2),
                BorderFactory.createLineBorder(TABLE_BORDER, 1)
            )
        }
        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        // header row
        val headers = listOf("方向", "source", "target")
        for ((i, h) in headers.withIndex()) {
            gc.gridx = i; gc.gridy = 0
            gc.weightx = if (i == 0) 0.0 else 1.0
            gc.insets = Insets(0, 0, 0, 0)
            table.add(headerCell(h), gc)
        }

        for ((rowIdx, m) in mappings.withIndex()) {
            val y = rowIdx + 1
            gc.gridx = 0; gc.gridy = y; gc.weightx = 0.0
            table.add(directionCell(m.direction), gc)

            gc.gridx = 1; gc.weightx = 1.0
            table.add(textCell(m.source.ifBlank { "—" }), gc)

            gc.gridx = 2
            table.add(textCell(m.target.ifBlank { "—" }), gc)
        }
        table.maximumSize = Dimension(Int.MAX_VALUE, table.preferredSize.height)
        contentPanel.add(table)
    }

    private fun addListenersSection(title: String, listeners: List<ListenerDef>) {
        contentPanel.add(buildSectionHeader(title))

        val table = JPanel(GridBagLayout()).apply {
            background = BG
            alignmentX = LEFT_ALIGNMENT
            border = BorderFactory.createCompoundBorder(
                EmptyBorder(2, 2, 10, 2),
                BorderFactory.createLineBorder(TABLE_BORDER, 1)
            )
        }
        val gc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
        }

        // header row
        val headers = listOf("事件", "类型", "实现")
        for ((i, h) in headers.withIndex()) {
            gc.gridx = i; gc.gridy = 0
            gc.weightx = if (i == 2) 1.0 else 0.0
            gc.insets = Insets(0, 0, 0, 0)
            table.add(headerCell(h), gc)
        }

        for ((rowIdx, listener) in listeners.withIndex()) {
            val y = rowIdx + 1
            gc.gridx = 0; gc.gridy = y; gc.weightx = 0.0
            table.add(textCell(listener.event.ifBlank { "—" }), gc)

            gc.gridx = 1; gc.weightx = 0.0
            table.add(textCell(listener.implementationType.ifBlank { "—" }), gc)

            gc.gridx = 2; gc.weightx = 1.0
            table.add(textCell(listener.implementation.ifBlank { "—" }), gc)
        }
        table.maximumSize = Dimension(Int.MAX_VALUE, table.preferredSize.height)
        contentPanel.add(table)
    }

    // ---------- atoms ----------

    private fun buildSectionHeader(title: String): JPanel {
        val panel = JPanel(BorderLayout()).apply {
            background = BG
            alignmentX = LEFT_ALIGNMENT
            border = EmptyBorder(6, 0, 4, 0)
            maximumSize = Dimension(Int.MAX_VALUE, 26)
        }
        val label = JLabel(title).apply {
            foreground = SECTION_TITLE
            font = font.deriveFont(Font.BOLD, 12f)
            border = EmptyBorder(0, 0, 0, 8)
        }
        val rule = JPanel().apply {
            background = SECTION_RULE
            preferredSize = Dimension(1, 1)
        }
        panel.add(label, BorderLayout.WEST)
        panel.add(rule, BorderLayout.CENTER)
        return panel
    }

    private fun buildKey(text: String): JLabel = JLabel("$text").apply {
        font = font.deriveFont(Font.BOLD, 12f)
        foreground = KEY_FG
        verticalAlignment = SwingConstants.TOP
    }

    private fun buildValue(text: String): JComponent = selectableHtmlPane(
        "<div style='width:240px;word-wrap:break-word;'>${escapeHtml(text)}</div>",
        fg = VALUE_FG,
        fontSize = 12f
    )

    private fun buildClassLink(className: String): JComponent = selectableHtmlPane(
        "<div style='width:240px;word-wrap:break-word;'>" +
                "<a href='#' style='color:#1A73E8;text-decoration:underline;'>${escapeHtml(className)}</a>" +
                "</div>",
        fg = LINK_FG,
        fontSize = 12f
    ).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "点击跳转到类: $className"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = navigateToClass(className)
        })
    }

    private fun buildProcessLink(processId: String): JComponent = selectableHtmlPane(
        "<div style='width:240px;word-wrap:break-word;'>" +
                "<a href='#' style='color:#1A73E8;text-decoration:underline;'>${escapeHtml(processId)}</a>" +
                "</div>",
        fg = LINK_FG,
        fontSize = 12f
    ).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "点击打开子流程: $processId"
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) = navigateToProcess(processId)
        })
    }

    private fun headerCell(text: String): JLabel = JLabel(text, SwingConstants.LEFT).apply {
        font = font.deriveFont(Font.BOLD, 11f)
        foreground = KEY_FG
        background = TABLE_HEADER_BG
        isOpaque = true
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, TABLE_BORDER),
            EmptyBorder(4, 8, 4, 8)
        )
    }

    private fun textCell(text: String): JComponent = selectableHtmlPane(
        "<div style='width:140px;word-wrap:break-word;'>${escapeHtml(text)}</div>",
        fg = VALUE_FG,
        fontSize = 11f
    ).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 1, TABLE_BORDER),
            EmptyBorder(4, 8, 4, 8)
        )
    }

    /**
     * Builds a borderless, transparent, read-only [JTextPane] rendering an HTML
     * fragment so its text can be selected and copied while still looking like
     * a [JLabel].
     */
    private fun selectableHtmlPane(
        htmlBody: String,
        fg: Color = VALUE_FG,
        fontSize: Float = 12f
    ): JTextPane {
        val family = UIManager.getFont("Label.font")?.family ?: "Dialog"
        val colorHex = String.format("%06x", fg.rgb and 0xFFFFFF)
        val pane = JTextPane()
        pane.contentType = "text/html"
        pane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        pane.font = pane.font.deriveFont(Font.PLAIN, fontSize)
        pane.foreground = fg
        pane.text = "<html><body style=\"font-family:'$family';font-size:${fontSize.toInt()}pt;color:#$colorHex;margin:0;padding:0;\">" +
                htmlBody + "</body></html>"
        pane.isEditable = false
        pane.isOpaque = false
        pane.background = Color(0, 0, 0, 0)
        pane.border = null
        pane.margin = Insets(0, 0, 0, 0)
        pane.alignmentX = LEFT_ALIGNMENT
        return pane
    }

    private fun directionCell(direction: String): Component {
        val isIn = direction.equals("in", ignoreCase = true)
        val tagBg = if (isIn) IN_TAG_BG else OUT_TAG_BG
        val tagFg = if (isIn) IN_TAG_FG else OUT_TAG_FG
        val wrapper = JPanel().apply {
            background = BG
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 1, TABLE_BORDER),
                EmptyBorder(4, 8, 4, 8)
            )
        }
        val tag = JLabel(direction.uppercase()).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            foreground = tagFg
            background = tagBg
            isOpaque = true
            border = EmptyBorder(2, 8, 2, 8)
        }
        wrapper.add(tag)
        wrapper.add(Box.createHorizontalGlue())
        return wrapper
    }

    private fun navigateToClass(className: String) {
        ApplicationManager.getApplication().invokeLater {
            val psiClass = JavaPsiFacade.getInstance(project)
                .findClass(className, GlobalSearchScope.allScope(project))
            if (psiClass != null) {
                psiClass.navigate(true)
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "未找到类: $className",
                    "导航失败",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    private fun navigateToProcess(processId: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val baseDir = project.basePath?.let {
                LocalFileSystem.getInstance().findFileByPath(it)
            }
            val targetFile = baseDir?.let { findBpmnFileByProcessId(it, processId) }
            ApplicationManager.getApplication().invokeLater {
                if (targetFile != null) {
                    FileEditorManager.getInstance(project).openFile(targetFile, true)
                } else {
                    JOptionPane.showMessageDialog(
                        this,
                        "未找到 process id 为 \"$processId\" 的 BPMN 文件",
                        "子流程未找到",
                        JOptionPane.INFORMATION_MESSAGE
                    )
                }
            }
        }
    }

    private fun findBpmnFileByProcessId(dir: VirtualFile, processId: String): VirtualFile? {
        val queue = ArrayDeque<VirtualFile>()
        queue.add(dir)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isDirectory) {
                current.children?.forEach { child ->
                    // Skip common build/output/VCS directories so we don't jump into
                    // generated copies of .bpmn files (e.g. Maven target, Gradle build).
                    if (child.isDirectory && child.name in EXCLUDED_DIRS) return@forEach
                    queue.add(child)
                }
            } else if (current.extension?.equals("bpmn", ignoreCase = true) == true) {
                try {
                    val content = String(current.contentsToByteArray(), Charsets.UTF_8)
                    if (matchesProcessId(content, processId)) {
                        return current
                    }
                } catch (_: Exception) {
                    // skip unreadable files
                }
            }
        }
        return null
    }

    private val EXCLUDED_DIRS = setOf("target", "build", ".gradle", "node_modules", ".git")

    private fun matchesProcessId(xmlContent: String, processId: String): Boolean {
        val regex = Regex("""<(?:[\w-]+:)?process\b[^>]*\bid\s*=\s*[\"']${Regex.escape(processId)}[\"']""")
        return regex.containsMatchIn(xmlContent)
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
