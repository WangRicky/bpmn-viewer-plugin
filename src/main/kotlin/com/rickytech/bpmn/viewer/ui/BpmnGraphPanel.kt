package com.rickytech.bpmn.viewer.ui

import com.intellij.openapi.diagnostic.Logger
import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import java.awt.geom.Point2D
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager
import kotlin.math.max

/**
 * Read-only Swing rendering panel for a laid-out BPMN model.
 *
 * Visual language inspired by bpmn.io / Eclipse Activiti Designer: white
 * surfaces, slate-gray strokes, refined typography. Style logic lives entirely
 * in this file so that diagram aesthetics stay decoupled from layout.
 *
 * Interaction:
 *   - Ctrl + mouse wheel : zoom (0.3x .. 3.0x)
 *   - Click + drag       : pan
 *   - Hover a node       : tooltip with full details
 */
class BpmnGraphPanel(
    private val model: BpmnModel,
    private val onNodeSelected: ((BpmnNode?) -> Unit)? = null,
    private val onEdgeSelected: ((BpmnEdge?) -> Unit)? = null
) : JPanel() {

    private var scale: Double = 1.0
    private var translateX: Double = 0.0
    private var translateY: Double = 0.0

    private var lastDragX: Int = 0
    private var lastDragY: Int = 0
    private var pressX: Int = 0
    private var pressY: Int = 0
    private var dragging: Boolean = false

    private var selectedNode: BpmnNode? = null
    private var selectedEdge: BpmnEdge? = null

    private val nodeRenderer = BpmnNodeRenderer()
    private val edgeRenderer = BpmnEdgeRenderer(BG)

    companion object {
        private val LOG = Logger.getInstance(BpmnGraphPanel::class.java)

        // ── Surface ──────────────────────────────────────────────────────────
        private val BG = Color(0xFA, 0xFA, 0xFA)

        // ── Interaction ─────────────────────────────────────────────────────
        private const val MIN_SCALE = 0.3
        private const val MAX_SCALE = 3.0
        private const val CLICK_TOLERANCE = 5
    }

    init {
        background = BG
        cursor = Cursor.getDefaultCursor()
        isOpaque = true
        isFocusable = true
        ToolTipManager.sharedInstance().registerComponent(this)
        ToolTipManager.sharedInstance().initialDelay = 200
        preferredSize = computeCanvasSize()

        LOG.info("BpmnGraphPanel created with ${model.nodes.size} nodes, ${model.edges.size} edges; " +
                "onNodeSelected=${if (onNodeSelected == null) "NULL" else "set"}")

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
                lastDragX = e.x
                lastDragY = e.y
                pressX = e.x
                pressY = e.y
                dragging = false
                requestFocusInWindow()
            }

            override fun mouseReleased(e: MouseEvent) {
                val dx = Math.abs(e.x - pressX)
                val dy = Math.abs(e.y - pressY)
                val isClick = !dragging && dx <= CLICK_TOLERANCE && dy <= CLICK_TOLERANCE &&
                        SwingUtilities.isLeftMouseButton(e)
                if (isClick) {
                    val model = screenToModel(e.x, e.y)
                    val node = nodeAt(e.x, e.y)
                    val edge = if (node == null) edgeAt(e.x, e.y) else null
                    LOG.info("click panel=(${e.x},${e.y}) model=(${"%.1f".format(model.x)},${"%.1f".format(model.y)}) " +
                            "translate=(${"%.1f".format(translateX)},${"%.1f".format(translateY)}) scale=${"%.2f".format(scale)} " +
                            "-> ${node?.id ?: edge?.id ?: "<empty>"}")
                    selectedNode = node
                    selectedEdge = edge
                    when {
                        node != null -> {
                            onNodeSelected?.invoke(node)
                        }
                        edge != null -> {
                            onEdgeSelected?.invoke(edge)
                        }
                        else -> {
                            onNodeSelected?.invoke(null)
                            onEdgeSelected?.invoke(null)
                        }
                    }
                    repaint()
                }
                dragging = false
                cursor = Cursor.getDefaultCursor()
            }

            override fun mouseDragged(e: MouseEvent) {
                val dx = e.x - lastDragX
                val dy = e.y - lastDragY
                lastDragX = e.x
                lastDragY = e.y
                if (!dragging &&
                    (Math.abs(e.x - pressX) > CLICK_TOLERANCE || Math.abs(e.y - pressY) > CLICK_TOLERANCE)) {
                    dragging = true
                    cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
                }
                if (dragging) {
                    translateX += dx
                    translateY += dy
                    repaint()
                }
            }

            override fun mouseMoved(e: MouseEvent) {
                val node = nodeAt(e.x, e.y)
                val edge = if (node == null) edgeAt(e.x, e.y) else null
                toolTipText = node?.let { tooltipFor(it) } ?: edge?.let { tooltipForEdge(it) }
                cursor = if (node != null || edge != null)
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else Cursor.getDefaultCursor()
            }
        }
        addMouseListener(mouseHandler)
        addMouseMotionListener(mouseHandler)
    }

    private fun computeCanvasSize(): Dimension {
        val all = model.nodes + model.pools + model.lanes
        val positioned = all.filter { hasDiCoordinates(it) }
        if (positioned.isEmpty() && model.nodes.isEmpty()) return Dimension(800, 600)
        val pool = if (positioned.isNotEmpty()) positioned else model.nodes
        val maxX = pool.maxOf { it.x + it.width } + 80
        val maxY = pool.maxOf { it.y + it.height } + 80
        return Dimension(max(800, maxX.toInt()), max(600, maxY.toInt()))
    }

    /**
     * Returns true when the node received explicit DI coordinates from the
     * BPMN diagram. Pools/lanes without DI metadata keep their defaults
     * (x=0,y=0,width=160,height=60) and must not be drawn at the origin.
     */
    private fun hasDiCoordinates(n: BpmnNode): Boolean {
        // Real pool/lane DI dimensions are always far larger than the BpmnNode
        // defaults (160x60). Treat the default geometry as "unset".
        if (n.x == 0.0 && n.y == 0.0 && n.width == 160.0 && n.height == 60.0) return false
        return true
    }

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
        // Activity nodes sit on top, so they get hit-test priority.
        for (i in model.nodes.indices.reversed()) {
            val n = model.nodes[i]
            val hit = mx >= n.x && mx <= n.x + n.width && my >= n.y && my <= n.y + n.height
            if (hit) return n
        }
        // Lanes next: only consider those with real DI coordinates.
        for (i in model.lanes.indices.reversed()) {
            val l = model.lanes[i]
            if (!hasDiCoordinates(l)) continue
            val hit = mx >= l.x && mx <= l.x + l.width && my >= l.y && my <= l.y + l.height
            if (hit) return l
        }
        // Pools last (largest container).
        for (i in model.pools.indices.reversed()) {
            val pool = model.pools[i]
            if (!hasDiCoordinates(pool)) continue
            val hit = mx >= pool.x && mx <= pool.x + pool.width && my >= pool.y && my <= pool.y + pool.height
            if (hit) return pool
        }
        return null
    }

    /**
     * Returns the edge whose polyline is within ~5 screen pixels of [px], [py].
     * Tolerance is converted into model space so it stays usable at any zoom.
     */
    private fun edgeAt(px: Int, py: Int): BpmnEdge? {
        val p = screenToModel(px, py)
        val mx = p.x
        val my = p.y
        val tolerance = (CLICK_TOLERANCE.toDouble() / scale).coerceAtLeast(2.0)
        for (e in model.edges) {
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

    private fun tooltipFor(n: BpmnNode): String {
        val sb = StringBuilder("<html><b>")
        sb.append(escape(if (n.name.isNotBlank()) n.name else n.id)).append("</b><br/>")
        sb.append("Type: ").append(n.type.name).append("<br/>")
        sb.append("Id: ").append(escape(n.id)).append("<br/>")
        n.javaClass?.let { sb.append("Class: ").append(escape(it)).append("<br/>") }
        n.delegateExpression?.let { sb.append("Delegate: ").append(escape(it)).append("<br/>") }
        n.calledElement?.let { sb.append("Called: ").append(escape(it)).append("<br/>") }
        sb.append("</html>")
        return sb.toString()
    }

    private fun tooltipForEdge(e: BpmnEdge): String {
        val sb = StringBuilder("<html><b>")
        sb.append(escape(e.name?.takeIf { it.isNotBlank() } ?: e.id)).append("</b><br/>")
        sb.append("Id: ").append(escape(e.id)).append("<br/>")
        sb.append(escape(e.sourceRef)).append(" → ").append(escape(e.targetRef))
        e.conditionExpression?.takeIf { it.isNotBlank() }?.let {
            sb.append("<br/>条件: ").append(escape(it.take(80)))
        }
        sb.append("</html>")
        return sb.toString()
    }

    private fun escape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

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

            // 1. Pools (lowest layer, background container)
            for (p in model.pools) {
                if (!hasDiCoordinates(p)) continue
                nodeRenderer.drawNode(g2, p, p == selectedNode)
            }
            // 2. Lanes inside pools
            for (l in model.lanes) {
                if (!hasDiCoordinates(l)) continue
                nodeRenderer.drawNode(g2, l, l == selectedNode)
            }
            // 3. Edges, then 4. activity nodes on top
            for (e in model.edges) edgeRenderer.drawEdge(g2, e, e == selectedEdge)
            for (n in model.nodes) nodeRenderer.drawNode(g2, n, n == selectedNode)
        } finally {
            g2.dispose()
        }
    }
}
