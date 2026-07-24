package com.rickytech.bpmn.viewer.ui

import com.intellij.openapi.diagnostic.Logger
import com.rickytech.bpmn.viewer.edit.AddEdgeCommand
import com.rickytech.bpmn.viewer.edit.AddNodeCommand
import com.rickytech.bpmn.viewer.edit.CommandStack
import com.rickytech.bpmn.viewer.edit.EditableEdge
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.edit.MoveNodeCommand
import com.rickytech.bpmn.viewer.edit.RemoveEdgeCommand
import com.rickytech.bpmn.viewer.edit.RemoveNodeCommand
import com.rickytech.bpmn.viewer.layout.ManhattanGridRouter
import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.geom.Point2D
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities

/**
 * 可编辑的 BPMN 图形面板。
 *
 * 复用 [BpmnNodeRenderer] / [BpmnEdgeRenderer] 渲染节点和连线，并在此之上实现：
 *   - 节点点击选中、拖拽移动（释放时生成 [MoveNodeCommand]）
 *   - Shift + 点击进入连线模式，再次点击目标节点生成 [AddEdgeCommand]
 *   - Delete 键 / 右键菜单删除节点或连线
 *   - Ctrl + 滚轮缩放、空白区域拖拽平移
 *   - Esc 取消连线模式
 *   - 通过 [addNodeAt] 供 Palette 调用以放置新节点
 *
 * 模型变更通过 [commandStack] 的 onStateChanged 回调驱动重绘。
 */
class BpmnEditGraphPanel(
    private val editModel: EditableModel,
    private val commandStack: CommandStack,
    private val onNodeSelected: ((EditableNode?) -> Unit)? = null,
    private val onEdgeSelected: ((EditableEdge?) -> Unit)? = null
) : JPanel() {

    /**
     * 可选的 Palette 引用：在空白处点击且 Palette 有选中类型时，
     * 直接在该位置放置节点，并清除 Palette 选中态。
     */
    var palettePanel: BpmnPalettePanel? = null

    // ── View transform ──────────────────────────────────────────────────
    private var scale: Double = 1.0
    private var translateX: Double = 0.0
    private var translateY: Double = 0.0

    // ── Interaction tracking ────────────────────────────────────────────
    private enum class InteractionState {
        IDLE,
        NODE_SELECTED,
        DRAGGING_NODE,
        CONNECTING_FROM,
        PANNING
    }

    private var state: InteractionState = InteractionState.IDLE

    // Selection
    private var selectedNodeId: String? = null
    private var selectedEdgeId: String? = null

    // Drag tracking
    private var pressX: Int = 0
    private var pressY: Int = 0
    private var lastDragX: Int = 0
    private var lastDragY: Int = 0

    // Node drag — model-space tracking
    private var dragNodeId: String? = null
    private var dragNodeOriginX: Double = 0.0
    private var dragNodeOriginY: Double = 0.0
    private var dragNodeOffsetX: Double = 0.0
    private var dragNodeOffsetY: Double = 0.0

    // Connect-from preview
    private var connectSourceNode: EditableNode? = null
    private var currentMouseX: Int = 0
    private var currentMouseY: Int = 0

    // Repaint throttling for drag
    private var lastRepaintTime: Long = 0L

    // ── Renderers & cached snapshot ─────────────────────────────────────
    private val nodeRenderer = BpmnNodeRenderer()
    private val edgeRenderer = BpmnEdgeRenderer(BG)
    private var cachedSnapshot: BpmnModel = editModel.toSnapshot()

    companion object {
        private val LOG = Logger.getInstance(BpmnEditGraphPanel::class.java)

        private val BG = Color(0xFA, 0xFA, 0xFA)
        private val PREVIEW_COLOR = Color(0x1A, 0x73, 0xE8)

        private const val MIN_SCALE = 0.3
        private const val MAX_SCALE = 3.0
        private const val CLICK_TOLERANCE = 5
        private const val DRAG_REPAINT_INTERVAL_MS = 30L

        // Default sizes by node category.
        private const val TASK_DEFAULT_W = 100.0
        private const val TASK_DEFAULT_H = 80.0
        private const val GATEWAY_DEFAULT_SIZE = 50.0
        private const val EVENT_DEFAULT_SIZE = 36.0
    }

    init {
        background = BG
        cursor = Cursor.getDefaultCursor()
        isOpaque = true
        isFocusable = true
        focusTraversalKeysEnabled = false
        preferredSize = Dimension(1200, 800)

        // 注意：commandStack.onStateChanged 由 BpmnFileEditor 统一编排，
        // 这里不再自行设置，避免覆盖其他面板的回调。

        installListeners()
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * 初始化或重新计算所有边的路由点。
     * 应在面板创建后、首次显示前调用一次，确保 fresh routing
     * （包括最新的避让逻辑）。
     */
    fun initRoutes() {
        rerouteEdges()
    }

    /**
     * 重新生成快照并重绘。CommandStack 状态变更时自动调用。
     */
    fun refreshModel() {
        cachedSnapshot = editModel.toSnapshot()
        // Drop selection if the underlying entity disappeared.
        if (selectedNodeId != null && cachedSnapshot.nodes.none { it.id == selectedNodeId }) {
            selectedNodeId = null
            onNodeSelected?.invoke(null)
        }
        if (selectedEdgeId != null && cachedSnapshot.edges.none { it.id == selectedEdgeId }) {
            selectedEdgeId = null
            onEdgeSelected?.invoke(null)
        }
        repaint()
    }

    /**
     * Palette 调用：在屏幕坐标 [screenX]/[screenY] 处放置一个 [type] 节点。
     */
    fun addNodeAt(type: BpmnNodeType, screenX: Int, screenY: Int) {
        val (w, h) = defaultSizeFor(type)
        val p = screenToModel(screenX, screenY)
        val id = "${type.name.lowercase()}_${System.currentTimeMillis() % 100000}"
        val node = EditableNode(
            id = id,
            name = "",
            type = type,
            x = p.x - w / 2.0,
            y = p.y - h / 2.0,
            width = w,
            height = h
        )
        commandStack.execute(AddNodeCommand(node), editModel)
        // Select the newly created node so the property panel can show it.
        selectedNodeId = id
        selectedEdgeId = null
        state = InteractionState.NODE_SELECTED
        editModel.nodes.firstOrNull { it.id == id }?.let { onNodeSelected?.invoke(it) }
        repaint()
    }

    /**
     * 定位到指定节点：选中该节点并平移画布使其在可视区域居中。
     */
    fun selectAndScrollTo(nodeId: String) {
        val node = cachedSnapshot.nodes.firstOrNull { it.id == nodeId }
            ?: editModel.nodes.firstOrNull { it.id == nodeId }?.let {
                cachedSnapshot.nodes.firstOrNull { n -> n.id == it.id }
            }
            ?: return

        selectedNodeId = nodeId
        selectedEdgeId = null
        state = InteractionState.NODE_SELECTED

        val viewW = if (width > 0) width else preferredSize.width
        val viewH = if (height > 0) height else preferredSize.height
        val centerX = node.x + node.width / 2.0
        val centerY = node.y + node.height / 2.0
        translateX = viewW / 2.0 - centerX * scale
        translateY = viewH / 2.0 - centerY * scale

        repaint()
        editModel.nodes.firstOrNull { it.id == nodeId }?.let { onNodeSelected?.invoke(it) }
        onEdgeSelected?.invoke(null)
    }

    private fun defaultSizeFor(type: BpmnNodeType): Pair<Double, Double> = when (type) {
        BpmnNodeType.EXCLUSIVE_GATEWAY,
        BpmnNodeType.PARALLEL_GATEWAY,
        BpmnNodeType.INCLUSIVE_GATEWAY,
        BpmnNodeType.EVENT_BASED_GATEWAY -> GATEWAY_DEFAULT_SIZE to GATEWAY_DEFAULT_SIZE

        BpmnNodeType.START_EVENT, BpmnNodeType.END_EVENT,
        BpmnNodeType.TIMER_START_EVENT, BpmnNodeType.MESSAGE_START_EVENT,
        BpmnNodeType.SIGNAL_START_EVENT, BpmnNodeType.ERROR_START_EVENT,
        BpmnNodeType.ERROR_END_EVENT, BpmnNodeType.TERMINATE_END_EVENT,
        BpmnNodeType.CANCEL_END_EVENT,
        BpmnNodeType.BOUNDARY_TIMER, BpmnNodeType.BOUNDARY_ERROR,
        BpmnNodeType.BOUNDARY_MESSAGE, BpmnNodeType.BOUNDARY_SIGNAL,
        BpmnNodeType.BOUNDARY_CANCEL, BpmnNodeType.BOUNDARY_COMPENSATE,
        BpmnNodeType.INTERMEDIATE_TIMER_CATCH, BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH,
        BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH, BpmnNodeType.INTERMEDIATE_SIGNAL_THROW,
        BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW -> EVENT_DEFAULT_SIZE to EVENT_DEFAULT_SIZE

        else -> TASK_DEFAULT_W to TASK_DEFAULT_H
    }

    // ── Listener wiring ─────────────────────────────────────────────────

    private fun installListeners() {
        addMouseWheelListener { e: MouseWheelEvent ->
            if (e.isControlDown) {
                val factor = if (e.wheelRotation < 0) 1.1 else 1.0 / 1.1
                val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
                if (newScale != scale) {
                    val mx = e.x.toDouble()
                    val my = e.y.toDouble()
                    translateX = mx - (mx - translateX) * (newScale / scale)
                    translateY = my - (my - translateY) * (newScale / scale)
                    scale = newScale
                    repaint()
                }
                e.consume()
            }
        }

        val mouseHandler = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                requestFocusInWindow()
                pressX = e.x
                pressY = e.y
                lastDragX = e.x
                lastDragY = e.y
                currentMouseX = e.x
                currentMouseY = e.y

                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e)
                    return
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return

                val node = nodeAt(e.x, e.y)

                // Shift+click on a node: enter connect mode (or finish a pending connection).
                if (e.isShiftDown && node != null) {
                    if (state == InteractionState.CONNECTING_FROM && connectSourceNode != null) {
                        finishConnection(node)
                    } else {
                        startConnection(node)
                    }
                    return
                }

                // Palette 连线工具激活时，点击节点即进入连线模式（类似 Shift+Click）。
                // 连线完成后保持工具激活状态，由 ESC 或再次点击按钮取消。
                if (palettePanel?.isConnectionToolActive() == true && node != null) {
                    if (state == InteractionState.CONNECTING_FROM && connectSourceNode != null) {
                        finishConnection(node)
                    } else {
                        startConnection(node)
                    }
                    return
                }

                // While connecting, a left click on a node finalises the edge;
                // a click elsewhere cancels the operation.
                if (state == InteractionState.CONNECTING_FROM) {
                    if (node != null) finishConnection(node) else cancelConnection()
                    return
                }

                if (node != null) {
                    // Pre-arm a node drag — the actual drag triggers once the
                    // pointer moves beyond CLICK_TOLERANCE.
                    selectedNodeId = node.id
                    selectedEdgeId = null
                    state = InteractionState.NODE_SELECTED
                    val mp = screenToModel(e.x, e.y)
                    dragNodeId = node.id
                    dragNodeOriginX = node.x
                    dragNodeOriginY = node.y
                    dragNodeOffsetX = mp.x - node.x
                    dragNodeOffsetY = mp.y - node.y
                    onEdgeSelected?.invoke(null)
                    editModel.nodes.firstOrNull { it.id == node.id }?.let { onNodeSelected?.invoke(it) }
                    repaint()
                } else {
                    // Empty area — hit-test edges, otherwise prepare to pan.
                    val edge = edgeAt(e.x, e.y)
                    if (edge != null) {
                        selectedEdgeId = edge.id
                        selectedNodeId = null
                        state = InteractionState.IDLE
                        onNodeSelected?.invoke(null)
                        editModel.edges.firstOrNull { it.id == edge.id }?.let { onEdgeSelected?.invoke(it) }
                        repaint()
                    } else {
                        // 连线工具激活时，空白点击不平移也不放置节点，等待点击源节点。
                        if (palettePanel?.isConnectionToolActive() == true) {
                            selectedNodeId = null
                            selectedEdgeId = null
                            onNodeSelected?.invoke(null)
                            onEdgeSelected?.invoke(null)
                            repaint()
                            return
                        }
                        // Palette 放置：空白点击且 Palette 有选中类型 → 直接放置节点。
                        val paletteType = palettePanel?.getSelectedType()
                        if (paletteType != null) {
                            addNodeAt(paletteType, e.x, e.y)
                            palettePanel?.clearSelection()
                            return
                        }
                        selectedNodeId = null
                        selectedEdgeId = null
                        state = InteractionState.PANNING
                        onNodeSelected?.invoke(null)
                        onEdgeSelected?.invoke(null)
                        repaint()
                    }
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                currentMouseX = e.x
                currentMouseY = e.y

                if (!SwingUtilities.isLeftMouseButton(e)) return

                when (state) {
                    InteractionState.NODE_SELECTED, InteractionState.DRAGGING_NODE -> {
                        val nodeId = dragNodeId ?: return
                        if (state == InteractionState.NODE_SELECTED) {
                            val moved = Math.abs(e.x - pressX) > CLICK_TOLERANCE ||
                                    Math.abs(e.y - pressY) > CLICK_TOLERANCE
                            if (!moved) return
                            state = InteractionState.DRAGGING_NODE
                            cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                        }
                        val mp = screenToModel(e.x, e.y)
                        val target = editModel.nodes.firstOrNull { it.id == nodeId } ?: return
                        target.x = mp.x - dragNodeOffsetX
                        target.y = mp.y - dragNodeOffsetY
                        throttledRepaint()
                    }
                    InteractionState.PANNING -> {
                        val dx = e.x - lastDragX
                        val dy = e.y - lastDragY
                        translateX += dx
                        translateY += dy
                        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                        repaint()
                    }
                    else -> { /* no-op */ }
                }

                lastDragX = e.x
                lastDragY = e.y
            }

            override fun mouseReleased(e: MouseEvent) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    handleRightClick(e)
                    return
                }
                if (!SwingUtilities.isLeftMouseButton(e)) return

                when (state) {
                    InteractionState.DRAGGING_NODE -> finishNodeDrag()
                    InteractionState.PANNING -> {
                        state = InteractionState.IDLE
                        cursor = Cursor.getDefaultCursor()
                    }
                    InteractionState.NODE_SELECTED -> {
                        // Pure click on a node — keep selection, drop drag arming.
                        dragNodeId = null
                        cursor = Cursor.getDefaultCursor()
                    }
                    else -> {
                        cursor = Cursor.getDefaultCursor()
                    }
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                currentMouseX = e.x
                currentMouseY = e.y
                if (state == InteractionState.CONNECTING_FROM) {
                    repaint()
                    return
                }
                val node = nodeAt(e.x, e.y)
                val edge = if (node == null) edgeAt(e.x, e.y) else null
                cursor = if (node != null || edge != null)
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)

        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_ESCAPE -> {
                        if (state == InteractionState.CONNECTING_FROM) cancelConnection()
                    }
                    KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> deleteSelected()
                }
            }
        })
    }

    private fun throttledRepaint() {
        val now = System.currentTimeMillis()
        if (now - lastRepaintTime >= DRAG_REPAINT_INTERVAL_MS) {
            lastRepaintTime = now
            repaint()
        }
    }

    // ── Connect-from helpers ────────────────────────────────────────────

    private fun startConnection(node: BpmnNode) {
        val src = editModel.nodes.firstOrNull { it.id == node.id } ?: return
        connectSourceNode = src
        state = InteractionState.CONNECTING_FROM
        selectedNodeId = src.id
        selectedEdgeId = null
        cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
        repaint()
    }

    private fun finishConnection(node: BpmnNode) {
        val src = connectSourceNode
        if (src == null) {
            cancelConnection()
            return
        }
        if (src.id == node.id) {
            // Self-loop is rarely intentional during simple drag-connect; bail out.
            cancelConnection()
            return
        }
        val edgeId = "flow_${System.currentTimeMillis()}"
        val edge = EditableEdge(
            id = edgeId,
            sourceRef = src.id,
            targetRef = node.id,
            routePoints = mutableListOf(
                (src.x + src.width / 2.0) to (src.y + src.height / 2.0),
                (node.x + node.width / 2.0) to (node.y + node.height / 2.0)
            )
        )
        commandStack.execute(AddEdgeCommand(edge), editModel)
        // Re-route everything once the edge is registered.
        rerouteEdges()
        connectSourceNode = null
        state = InteractionState.IDLE
        cursor = Cursor.getDefaultCursor()
        selectedEdgeId = edgeId
        selectedNodeId = null
        editModel.edges.firstOrNull { it.id == edgeId }?.let { onEdgeSelected?.invoke(it) }
        onNodeSelected?.invoke(null)
        repaint()
    }

    private fun cancelConnection() {
        connectSourceNode = null
        state = InteractionState.IDLE
        cursor = Cursor.getDefaultCursor()
        repaint()
    }

    // ── Drag finalisation ───────────────────────────────────────────────

    private fun finishNodeDrag() {
        val nodeId = dragNodeId
        if (nodeId == null) {
            state = if (selectedNodeId != null) InteractionState.NODE_SELECTED else InteractionState.IDLE
            cursor = Cursor.getDefaultCursor()
            return
        }
        val node = editModel.nodes.firstOrNull { it.id == nodeId }
        if (node == null) {
            dragNodeId = null
            state = InteractionState.IDLE
            cursor = Cursor.getDefaultCursor()
            return
        }
        val toX = node.x
        val toY = node.y
        // Reset to the original position so the command's execute() lays down
        // the canonical new coordinates and undo() can restore the old ones.
        node.x = dragNodeOriginX
        node.y = dragNodeOriginY
        if (toX != dragNodeOriginX || toY != dragNodeOriginY) {
            commandStack.execute(
                MoveNodeCommand(nodeId, dragNodeOriginX, dragNodeOriginY, toX, toY),
                editModel
            )
            rerouteEdges()
        }
        dragNodeId = null
        state = InteractionState.NODE_SELECTED
        cursor = Cursor.getDefaultCursor()
        repaint()
    }

    /**
     * Recomputes every edge's routePoints via [EdgeRouter] and writes the
     * results back into [editModel] so the model stays consistent.
     */
    private fun rerouteEdges() {
        val routed = ManhattanGridRouter().route(editModel.toSnapshot())
        val routeById = routed.edges.associateBy { it.id }
        for (e in editModel.edges) {
            val pts = routeById[e.id]?.routePoints ?: continue
            e.routePoints = pts.toMutableList()
        }
        cachedSnapshot = editModel.toSnapshot()
    }

    // ── Right-click & deletion ──────────────────────────────────────────

    private fun handleRightClick(e: MouseEvent) {
        val node = nodeAt(e.x, e.y)
        val edge = if (node == null) edgeAt(e.x, e.y) else null
        if (node != null) {
            selectedNodeId = node.id
            selectedEdgeId = null
            state = InteractionState.NODE_SELECTED
            onEdgeSelected?.invoke(null)
            editModel.nodes.firstOrNull { it.id == node.id }?.let { onNodeSelected?.invoke(it) }
            repaint()
            showContextMenu(e.x, e.y, hasSelection = true)
        } else if (edge != null) {
            selectedEdgeId = edge.id
            selectedNodeId = null
            onNodeSelected?.invoke(null)
            editModel.edges.firstOrNull { it.id == edge.id }?.let { onEdgeSelected?.invoke(it) }
            repaint()
            showContextMenu(e.x, e.y, hasSelection = true)
        } else {
            showContextMenu(e.x, e.y, hasSelection = false)
        }
    }

    private fun showContextMenu(x: Int, y: Int, hasSelection: Boolean) {
        val menu = JPopupMenu()
        val delete = JMenuItem("删除")
        delete.isEnabled = hasSelection
        delete.addActionListener { deleteSelected() }
        menu.add(delete)
        menu.show(this, x, y)
    }

    private fun deleteSelected() {
        val nodeId = selectedNodeId
        val edgeId = selectedEdgeId
        when {
            nodeId != null -> {
                commandStack.execute(RemoveNodeCommand(nodeId), editModel)
                rerouteEdges()
                selectedNodeId = null
                state = InteractionState.IDLE
                onNodeSelected?.invoke(null)
                repaint()
            }
            edgeId != null -> {
                commandStack.execute(RemoveEdgeCommand(edgeId), editModel)
                selectedEdgeId = null
                state = InteractionState.IDLE
                onEdgeSelected?.invoke(null)
                repaint()
            }
        }
    }

    // ── Coordinate / hit-testing ────────────────────────────────────────

    private fun screenToModel(screenX: Int, screenY: Int): Point2D {
        val tx = AffineTransform()
        tx.translate(translateX, translateY)
        tx.scale(scale, scale)
        return try {
            tx.createInverse().transform(
                Point2D.Double(screenX.toDouble(), screenY.toDouble()), null
            )
        } catch (_: Exception) {
            Point2D.Double(screenX.toDouble(), screenY.toDouble())
        }
    }

    private fun nodeAt(px: Int, py: Int): BpmnNode? {
        val p = screenToModel(px, py)
        val mx = p.x
        val my = p.y
        val nodes = cachedSnapshot.nodes
        for (i in nodes.indices.reversed()) {
            val n = nodes[i]
            val hit = mx >= n.x && mx <= n.x + n.width && my >= n.y && my <= n.y + n.height
            if (hit) return n
        }
        return null
    }

    private fun edgeAt(px: Int, py: Int): BpmnEdge? {
        val p = screenToModel(px, py)
        val mx = p.x
        val my = p.y
        val tolerance = (CLICK_TOLERANCE.toDouble() / scale).coerceAtLeast(2.0)
        for (e in cachedSnapshot.edges) {
            val pts = e.routePoints
            if (pts.size < 2) continue
            for (i in 0 until pts.size - 1) {
                val d = pointToSegmentDistance(
                    mx, my,
                    pts[i].first, pts[i].second,
                    pts[i + 1].first, pts[i + 1].second
                )
                if (d <= tolerance) return e
            }
        }
        return null
    }

    private fun pointToSegmentDistance(
        px: Double, py: Double,
        x1: Double, y1: Double,
        x2: Double, y2: Double
    ): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0.0 && dy == 0.0) return Point2D.distance(px, py, x1, y1)
        val t = (((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)).coerceIn(0.0, 1.0)
        val cx = x1 + t * dx
        val cy = y1 + t * dy
        return Point2D.distance(px, py, cx, cy)
    }

    // ── Painting ────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

            g2.color = BG
            g2.fillRect(0, 0, width, height)

            g2.translate(translateX, translateY)
            g2.scale(scale, scale)

            val snapshot = cachedSnapshot
            for (e in snapshot.edges) edgeRenderer.drawEdge(g2, e, e.id == selectedEdgeId)
            for (n in snapshot.nodes) nodeRenderer.drawNode(g2, n, n.id == selectedNodeId)

            if (state == InteractionState.CONNECTING_FROM && connectSourceNode != null) {
                drawConnectionPreview(g2)
            }
        } finally {
            g2.dispose()
        }
    }

    private fun drawConnectionPreview(g2: Graphics2D) {
        val src = connectSourceNode ?: return
        val mp = screenToModel(currentMouseX, currentMouseY)
        val sx = src.x + src.width / 2.0
        val sy = src.y + src.height / 2.0
        val prevStroke = g2.stroke
        val prevColor = g2.color
        try {
            g2.stroke = BasicStroke(
                1.4f,
                BasicStroke.CAP_ROUND,
                BasicStroke.JOIN_ROUND,
                0f,
                floatArrayOf(6f, 4f),
                0f
            )
            g2.color = PREVIEW_COLOR
            g2.draw(Line2D.Double(sx, sy, mp.x, mp.y))
        } finally {
            g2.stroke = prevStroke
            g2.color = prevColor
        }
    }
}
