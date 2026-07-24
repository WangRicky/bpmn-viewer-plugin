package com.rickytech.bpmn.viewer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.layout.ManhattanGridRouter
import com.rickytech.bpmn.viewer.layout.SugiyamaLayout
import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.parser.BpmnParser
import com.rickytech.bpmn.viewer.serializer.BpmnSerializer
import com.rickytech.bpmn.viewer.ui.BpmnDetailPanel
import com.rickytech.bpmn.viewer.ui.BpmnEditGraphPanel
import com.rickytech.bpmn.viewer.ui.BpmnGraphPanel
import com.rickytech.bpmn.viewer.ui.BpmnPalettePanel
import com.rickytech.bpmn.viewer.ui.BpmnPropertyPanel
import com.rickytech.bpmn.viewer.validation.BpmnValidator
import com.rickytech.bpmn.viewer.validation.Severity
import com.rickytech.bpmn.viewer.validation.ValidationIssue
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.ActionEvent
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.beans.PropertyChangeListener
import javax.swing.AbstractAction
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.KeyStroke
import javax.swing.SwingConstants
import javax.swing.SwingUtilities

class BpmnFileEditor(
    private val project: Project,
    private val virtualFile: VirtualFile
) : UserDataHolderBase(), FileEditor {

    private val rootPanel: JPanel = JPanel(BorderLayout())
    private val contentPanel: JPanel = JPanel(BorderLayout())
    private val headerPanel: JPanel = JPanel(BorderLayout())

    // 解析后并完成自动布局/边路由的只读模型；提升为成员变量以便切换到编辑模式时复用。
    private var laidOut: BpmnModel? = null

    // 视图模式持有的查看组件
    private var viewGraphPanel: BpmnGraphPanel? = null
    private var viewDetailPanel: BpmnDetailPanel? = null
    private var viewSplitPane: JSplitPane? = null

    // 编辑模式状态
    private var editMode: Boolean = false
    private var editableModel: EditableModel? = null
    private var commandStack: CommandStack? = null
    private var editGraphPanel: BpmnEditGraphPanel? = null
    private var propertyPanel: BpmnPropertyPanel? = null
    private var palettePanel: BpmnPalettePanel? = null

    // Header 按钮
    private val toggleButton: JButton = JButton("切换编辑")
    private val saveButton: JButton = JButton("保存")
    private val undoButton: JButton = JButton("↶ 撤销")
    private val redoButton: JButton = JButton("↷ 重做")
    private val titleLabel: JLabel = JLabel(" ")

    companion object {
        private val LOG = Logger.getInstance(BpmnFileEditor::class.java)
    }

    init {
        try {
            val bytes = virtualFile.contentsToByteArray()
            val xml = String(bytes, Charsets.UTF_8)
            val laidOutModel: BpmnModel = parseAndLayout(xml)
            this.laidOut = laidOutModel

            // 初始构建查看模式 UI。
            buildViewModeContent(laidOutModel)

            // Header
            updateTitle(laidOutModel)
            wireHeaderButtons()
            buildHeader()

            rootPanel.add(headerPanel, BorderLayout.NORTH)
            rootPanel.add(contentPanel, BorderLayout.CENTER)

            updateButtons()
            registerKeyBindings()

            // 如果流程为空（如新建的 .bpmn 文件），自动进入编辑模式，
            // 让用户立即可以从 Palette 拖入节点开始编辑。
            val activeNodes = laidOutModel.nodes.filter {
                it.type != BpmnNodeType.POOL && it.type != BpmnNodeType.LANE
            }
            if (activeNodes.isEmpty() && laidOutModel.edges.isEmpty()) {
                // 必须延迟到 UI 组件全部初始化完成之后再触发模式切换，
                // 否则 buildViewModeContent 之后立刻 switchToEditMode 会出现界面闪烁。
                SwingUtilities.invokeLater { switchToEditMode() }
            }
        } catch (t: Throwable) {
            LOG.error("Failed to render BPMN file: ${virtualFile.path}", t)
            val err = JLabel("Failed to render BPMN: ${t.message}", SwingConstants.CENTER)
            err.foreground = Color.RED
            rootPanel.add(err, BorderLayout.CENTER)
        }
    }

    // ── Header ──────────────────────────────────────────────────────────

    private fun updateTitle(model: BpmnModel) {
        val nodes = model.nodes.size
        val edges = model.edges.size
        val name = model.processName.ifBlank { model.processId }
        titleLabel.text = " $name  ($nodes nodes, $edges edges) "
    }

    private fun buildHeader() {
        headerPanel.removeAll()
        headerPanel.border = BorderFactory.createMatteBorder(0, 0, 1, 0, Color(0xE0, 0xE0, 0xE0))

        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 13f)
        titleLabel.border = BorderFactory.createEmptyBorder(6, 10, 6, 10)
        headerPanel.add(titleLabel, BorderLayout.WEST)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 2))
        buttons.add(undoButton)
        buttons.add(redoButton)
        buttons.add(saveButton)
        buttons.add(toggleButton)
        headerPanel.add(buttons, BorderLayout.EAST)

        headerPanel.revalidate()
        headerPanel.repaint()
    }

    private fun wireHeaderButtons() {
        toggleButton.addActionListener {
            if (editMode) switchToViewMode() else switchToEditMode()
        }
        saveButton.addActionListener { saveFile() }
        undoButton.addActionListener { undo() }
        redoButton.addActionListener { redo() }
    }

    private fun updateButtons() {
        toggleButton.text = if (editMode) "切换查看" else "切换编辑"
        saveButton.isEnabled = editMode && (commandStack?.isModified == true)
        undoButton.isEnabled = editMode && (commandStack?.canUndo() == true)
        redoButton.isEnabled = editMode && (commandStack?.canRedo() == true)
    }

    // ── 视图模式 ────────────────────────────────────────────────────────

    private fun buildViewModeContent(model: BpmnModel) {
        val detailPanel = BpmnDetailPanel(project)
        val graphPanel = BpmnGraphPanel(
            model,
            onNodeSelected = { node ->
                if (node != null) detailPanel.showNodeDetail(node) else detailPanel.clear()
            },
            onEdgeSelected = { edge ->
                if (edge != null) {
                    val source = model.nodes.firstOrNull { it.id == edge.sourceRef }
                    val target = model.nodes.firstOrNull { it.id == edge.targetRef }
                    detailPanel.showEdgeDetail(edge, source, target)
                } else {
                    detailPanel.clear()
                }
            }
        )

        // Note: BpmnGraphPanel handles its own pan (drag) and zoom (Ctrl+wheel),
        // so we deliberately DO NOT wrap it in a JScrollPane.
        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graphPanel, detailPanel).apply {
            resizeWeight = 0.7
            dividerSize = 4
            isContinuousLayout = true
        }

        viewGraphPanel = graphPanel
        viewDetailPanel = detailPanel
        viewSplitPane = splitPane

        contentPanel.removeAll()
        contentPanel.add(splitPane, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    private fun rebuildContentForViewMode() {
        val model = laidOut ?: return
        // 释放编辑模式资源
        editGraphPanel = null
        propertyPanel = null
        palettePanel = null
        editableModel = null
        commandStack = null
        buildViewModeContent(model)
    }

    // ── 编辑模式 ────────────────────────────────────────────────────────

    private fun switchToEditMode() {
        if (editMode) return
        val baseModel = laidOut ?: return
        editMode = true

        val em = EditableModel.fromBpmnModel(baseModel)
        val cs = CommandStack()
        val palette = BpmnPalettePanel()
        // 面板内部不再自行设置 cs.onStateChanged，这里由 FileEditor 统一编排：
        // 命令栈变更 → 图形重绘 + 属性面板刷新 + 按钮状态更新。
        val props = BpmnPropertyPanel(em, cs)
        val graph = BpmnEditGraphPanel(
            editModel = em,
            commandStack = cs,
            onNodeSelected = { node ->
                if (node != null) props.showNodeProperties(node) else props.clear()
            },
            onEdgeSelected = { edge ->
                if (edge != null) props.showEdgeProperties(edge) else props.clear()
            }
        )
        graph.palettePanel = palette

        cs.onStateChanged = {
            graph.refreshModel()
            props.refreshFromModel()
            updateButtons()
        }

        editableModel = em
        commandStack = cs
        palettePanel = palette
        propertyPanel = props
        editGraphPanel = graph

        // 编辑模式初始化时，重新计算所有边的路由点，
        // 确保连接线在首次显示时即可见。
        graph.initRoutes()

        rebuildContentForEditMode()
        updateButtons()
    }

    private fun switchToViewMode() {
        if (!editMode) return
        if (commandStack?.isModified == true) {
            val result = JOptionPane.showConfirmDialog(
                rootPanel,
                "存在未保存的修改，是否放弃这些改动并切换到查看模式？",
                "切换模式",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            )
            if (result != JOptionPane.YES_OPTION) return
        }
        editMode = false
        rebuildContentForViewMode()
        updateButtons()
    }

    private fun rebuildContentForEditMode() {
        val palette = palettePanel ?: return
        val graph = editGraphPanel ?: return
        val props = propertyPanel ?: return

        val paletteScroll = JScrollPane(palette).apply {
            border = null
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 16
            preferredSize = Dimension(150, 600)
            minimumSize = Dimension(140, 200)
        }
        // BpmnPropertyPanel 内部已含 JScrollPane，这里不再套一层避免双层滚动。
        props.preferredSize = Dimension(290, 600)
        props.minimumSize = Dimension(240, 200)
        val rightPanel: JComponent = props

        val centerRight = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, graph, rightPanel).apply {
            resizeWeight = 0.75
            dividerSize = 4
            isContinuousLayout = true
        }
        val outer = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, paletteScroll, centerRight).apply {
            resizeWeight = 0.0
            dividerSize = 4
            isContinuousLayout = true
        }

        contentPanel.removeAll()
        contentPanel.add(outer, BorderLayout.CENTER)
        contentPanel.revalidate()
        contentPanel.repaint()
    }

    // ── 撤销 / 重做 / 保存 ─────────────────────────────────────────────

    private fun undo() {
        val model = editableModel ?: return
        val stack = commandStack ?: return
        stack.undo(model)
        updateButtons()
    }

    private fun redo() {
        val model = editableModel ?: return
        val stack = commandStack ?: return
        stack.redo(model)
        updateButtons()
    }

    private fun saveFile() {
        val model = editableModel ?: return
        val stack = commandStack ?: return

        // 1. 校验
        val issues = BpmnValidator().validate(model)
        val errors = issues.filter { it.severity == Severity.ERROR }
        if (errors.isNotEmpty()) {
            showValidationDialog(
                title = "校验错误",
                headerText = "保存失败，存在以下错误：",
                issues = errors,
                messageType = JOptionPane.ERROR_MESSAGE,
                showCancel = false
            )
            return
        }
        val warnings = issues.filter { it.severity == Severity.WARNING }
        if (warnings.isNotEmpty()) {
            val proceed = showValidationDialog(
                title = "校验警告",
                headerText = "存在以下警告，是否继续保存？",
                issues = warnings,
                messageType = JOptionPane.WARNING_MESSAGE,
                showCancel = true
            )
            if (!proceed) return
        }

        // 2. 序列化
        val xml = try {
            BpmnSerializer().serialize(model)
        } catch (t: Throwable) {
            LOG.error("Failed to serialize BPMN model", t)
            JOptionPane.showMessageDialog(
                rootPanel,
                "序列化失败：${t.message}",
                "保存失败",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        // 3. 写回文件
        try {
            ApplicationManager.getApplication().runWriteAction {
                virtualFile.setBinaryContent(xml.toByteArray(Charsets.UTF_8))
            }
        } catch (t: Throwable) {
            LOG.error("Failed to write BPMN file: ${virtualFile.path}", t)
            JOptionPane.showMessageDialog(
                rootPanel,
                "写入文件失败：${t.message}",
                "保存失败",
                JOptionPane.ERROR_MESSAGE
            )
            return
        }

        // 4. 标记已保存
        stack.markSaved()

        // 5. 更新查看模式数据源：从已写入的 virtualFile 重新解析，
        //    避免 toSnapshot() 遗漏解析器才能提取的字段（如某些 extensionElements）。
        //    这里复用与首次打开一致的 parseAndLayout 流程，确保渲染数据完整。
        try {
            val savedXml = String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
            laidOut = parseAndLayout(savedXml)
        } catch (t: Throwable) {
            LOG.error("Failed to re-parse saved BPMN file: ${virtualFile.path}", t)
            // 回退到旧逻辑，避免保存后查看模式无数据
            laidOut = ManhattanGridRouter().route(model.toSnapshot())
        }

        updateButtons()
    }

    // ── 校验弹窗 ─────────────────────────────────────────────

    /**
     * 展示校验问题列表。每条有 nodeId 的 issue 随行提供“定位”按钮，
     * 点击后调用 [BpmnEditGraphPanel.selectAndScrollTo] 并关闭弹窗。
     *
     * @param showCancel 为 true 时提供“继续保存 / 取消”选项；为 false 时仅提供“关闭”。
     * @return 用户是否选择继续保存（showCancel=false 时返回 false）。
     */
    private fun showValidationDialog(
        title: String,
        headerText: String,
        issues: List<ValidationIssue>,
        messageType: Int,
        showCancel: Boolean
    ): Boolean {
        val listPanel = JPanel()
        listPanel.layout = BoxLayout(listPanel, BoxLayout.Y_AXIS)
        listPanel.background = Color.WHITE
        listPanel.border = BorderFactory.createEmptyBorder(4, 4, 4, 4)

        val header = JLabel(headerText)
        header.alignmentX = Component.LEFT_ALIGNMENT
        header.border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
        listPanel.add(header)

        val graph = editGraphPanel
        val closeRefs = mutableListOf<() -> Unit>()

        for (issue in issues) {
            val row = JPanel(FlowLayout(FlowLayout.LEFT, 4, 2))
            row.background = Color.WHITE
            row.alignmentX = Component.LEFT_ALIGNMENT
            val prefix = when (issue.severity) {
                Severity.ERROR -> "❌"
                Severity.WARNING -> "⚠"
            }
            val label = JLabel("$prefix  ${issue.message}")
            row.add(label)
            val nodeId = issue.nodeId
            if (nodeId != null && graph != null) {
                val locateBtn = JButton("定位")
                locateBtn.margin = java.awt.Insets(0, 6, 0, 6)
                locateBtn.addActionListener {
                    if (!editMode) switchToEditMode()
                    editGraphPanel?.selectAndScrollTo(nodeId)
                    closeRefs.firstOrNull()?.invoke()
                }
                row.add(locateBtn)
            }
            row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
            listPanel.add(row)
        }

        listPanel.add(Box.createVerticalGlue())

        val scroll = JScrollPane(listPanel).apply {
            border = BorderFactory.createLineBorder(Color(0xE0, 0xE0, 0xE0))
            preferredSize = Dimension(560, (issues.size.coerceAtMost(8) * 30 + 60))
            verticalScrollBar.unitIncrement = 16
        }

        val options: Array<Any>
        val initial: Any
        if (showCancel) {
            options = arrayOf("继续保存", "取消")
            initial = options[1]
        } else {
            options = arrayOf("关闭")
            initial = options[0]
        }

        val pane = JOptionPane(
            scroll,
            messageType,
            if (showCancel) JOptionPane.YES_NO_OPTION else JOptionPane.DEFAULT_OPTION,
            null,
            options,
            initial
        )
        val dialog = pane.createDialog(rootPanel, title)
        closeRefs += {
            pane.value = options.last()
            dialog.isVisible = false
        }
        dialog.isResizable = true
        dialog.isVisible = true
        dialog.dispose()

        if (!showCancel) return false
        val value = pane.value
        return value == options[0]
    }

    // ── 解析 + 布局（与初始化共享流程） ─────────────────────────────────

    private fun parseAndLayout(xml: String): BpmnModel {
        val parsed: BpmnModel = BpmnParser().parse(xml)

        // Flatten sub-process internals into the top-level model so the
        // existing rendering pipeline can draw nested nodes/edges.
        // Child nodes carry absolute DI coordinates (resolved from BPMNDiagram),
        // therefore they can be placed at the top level without translation.
        val flattenedNodes = mutableListOf<BpmnNode>()
        val flattenedEdges = mutableListOf<BpmnEdge>()
        fun flattenSubProcess(nodes: List<BpmnNode>, edges: List<BpmnEdge>) {
            for (node in nodes) {
                flattenedNodes += node
                if (node.childNodes.isNotEmpty()) {
                    flattenSubProcess(node.childNodes, node.childEdges)
                }
            }
            flattenedEdges += edges
        }
        flattenSubProcess(parsed.nodes, parsed.edges)
        val flatModel = parsed.copy(
            nodes = flattenedNodes,
            edges = flattenedEdges
        )

        // 判断是否拥有 DI 坐标。
        val activeNodes = flatModel.nodes.filter {
            it.type != BpmnNodeType.POOL &&
            it.type != BpmnNodeType.LANE
        }
        val nodesWithCoords = activeNodes.count { it.x != 0.0 || it.y != 0.0 }
        val hasDiCoordinates = activeNodes.isNotEmpty() &&
            nodesWithCoords.toDouble() / activeNodes.size > 0.5
        LOG.info("Parsed ${flatModel.nodes.size} nodes (${activeNodes.size} active, $nodesWithCoords with coords). hasDiCoordinates=$hasDiCoordinates")

        return if (hasDiCoordinates) {
            ManhattanGridRouter().route(flatModel)
        } else {
            SugiyamaLayout().layout(flatModel)
        }
    }

    // ── 快捷键 ──────────────────────────────────────────────────────────

    private fun registerKeyBindings() {
        val im = rootPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        val am = rootPanel.actionMap

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "bpmn.save")
        am.put("bpmn.save", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (editMode) saveFile()
            }
        })

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "bpmn.undo")
        am.put("bpmn.undo", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (editMode) undo()
            }
        })

        im.put(
            KeyStroke.getKeyStroke(
                KeyEvent.VK_Z,
                InputEvent.CTRL_DOWN_MASK or InputEvent.SHIFT_DOWN_MASK
            ),
            "bpmn.redo"
        )
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "bpmn.redo")
        am.put("bpmn.redo", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent) {
                if (editMode) redo()
            }
        })
    }

    // ── FileEditor ──────────────────────────────────────────────────────

    override fun getComponent(): JComponent = rootPanel
    override fun getPreferredFocusedComponent(): JComponent = rootPanel
    override fun getName(): String = "BPMN Viewer"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = commandStack?.isModified ?: false
    override fun isValid(): Boolean = virtualFile.isValid
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun dispose() {}
    override fun getCurrentLocation(): FileEditorLocation? = null
    override fun getFile(): VirtualFile = virtualFile
}
