package com.rickytech.bpmn.viewer.ui

import com.rickytech.bpmn.viewer.model.BpmnNodeType
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Polygon
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 左侧元素工具栏。
 *
 * 采用"单击选择 + 点击画布放置"的模式：
 *   - 用户在 Palette 中点击一个元素 → 该项进入选中态，外部可通过 [getSelectedType] 获取；
 *   - [BpmnEditGraphPanel] 在空白区域被点击时调用 [getSelectedType]，决定是否触发
 *     `addNodeAt(type, screenX, screenY)`，并在放置完成后调用 [clearSelection]；
 *   - 再次点击已选中的元素则取消选中，恢复普通编辑模式。
 *
 * 不持有 [BpmnEditGraphPanel] 引用，仅暴露状态查询/清除接口，由编辑面板主动协作。
 */
class BpmnPalettePanel : JPanel() {

    private var selectedType: BpmnNodeType? = null
    private var connectionToolActive: Boolean = false
    private val buttons = mutableListOf<PaletteButton>()

    /** 外部可注册的选中变化回调；当前主要用于刷新光标等可选反馈。 */
    var onNodeTypeSelected: ((BpmnNodeType?) -> Unit)? = null

    fun getSelectedType(): BpmnNodeType? = selectedType

    /** 连线工具是否激活；激活后画布上点击节点即进入连线模式。 */
    fun isConnectionToolActive(): Boolean = connectionToolActive

    fun clearSelection() {
        if (selectedType != null) {
            selectedType = null
            updateButtonStates()
            onNodeTypeSelected?.invoke(null)
        }
    }

    /** 关闭连线工具，由外部（如 ESC、模式切换）调用以同步状态。 */
    fun clearConnectionTool() {
        if (connectionToolActive) {
            connectionToolActive = false
            updateButtonStates()
        }
    }

    init {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 4, 8, 4)
        preferredSize = Dimension(150, 600)
        minimumSize = Dimension(140, 200)
        background = BG
        isOpaque = true

        addSection("事件", first = true)
        addItem("开始事件", BpmnNodeType.START_EVENT)
        addItem("结束事件", BpmnNodeType.END_EVENT)
        addItem("定时开始", BpmnNodeType.TIMER_START_EVENT)
        addItem("消息开始", BpmnNodeType.MESSAGE_START_EVENT)

        addSection("任务")
        addItem("服务任务", BpmnNodeType.SERVICE_TASK)
        addItem("用户任务", BpmnNodeType.USER_TASK)
        addItem("脚本任务", BpmnNodeType.SCRIPT_TASK)
        addItem("手工任务", BpmnNodeType.MANUAL_TASK)
        addItem("邮件任务", BpmnNodeType.MAIL_TASK)
        addItem("调用活动", BpmnNodeType.CALL_ACTIVITY)

        addSection("网关")
        addItem("排他网关", BpmnNodeType.EXCLUSIVE_GATEWAY)
        addItem("并行网关", BpmnNodeType.PARALLEL_GATEWAY)
        addItem("包含网关", BpmnNodeType.INCLUSIVE_GATEWAY)
        addItem("事件网关", BpmnNodeType.EVENT_BASED_GATEWAY)

        addSection("连接")
        addConnectionItem("顺序流")

        // 占位，避免 BoxLayout 把按钮拉伸。
        add(Box.createVerticalGlue())
    }

    // ── 内部构建 ────────────────────────────────────────────────────────

    private fun addSection(title: String, first: Boolean = false) {
        if (!first) add(Box.createVerticalStrut(SECTION_TOP_GAP))
        val label = JLabel(title)
        label.font = label.font.deriveFont(Font.BOLD, 12f)
        label.foreground = SECTION_FG
        label.alignmentX = Component.LEFT_ALIGNMENT
        label.border = BorderFactory.createEmptyBorder(2, 4, 4, 4)
        label.maximumSize = Dimension(Int.MAX_VALUE, 20)
        add(label)
    }

    private fun addItem(text: String, type: BpmnNodeType) {
        val btn = PaletteButton(text, type, BpmnTypeIcon(type)) { clicked ->
            toggleSelection(clicked)
        }
        btn.alignmentX = Component.LEFT_ALIGNMENT
        buttons.add(btn)
        add(btn)
        add(Box.createVerticalStrut(2))
    }

    /** 连线工具按钮：不关联节点类型，激活后由画布读取 [isConnectionToolActive]。 */
    private fun addConnectionItem(text: String) {
        val btn = PaletteButton(text, null, SequenceFlowIcon()) { clicked ->
            toggleConnectionTool(clicked)
        }
        btn.alignmentX = Component.LEFT_ALIGNMENT
        buttons.add(btn)
        add(btn)
        add(Box.createVerticalStrut(2))
    }

    private fun toggleSelection(btn: PaletteButton) {
        val type = btn.nodeType ?: return
        selectedType = if (selectedType == type) null else type
        // 节点工具与连线工具互斥。
        if (selectedType != null && connectionToolActive) connectionToolActive = false
        updateButtonStates()
        onNodeTypeSelected?.invoke(selectedType)
    }

    private fun toggleConnectionTool(@Suppress("UNUSED_PARAMETER") btn: PaletteButton) {
        connectionToolActive = !connectionToolActive
        if (connectionToolActive && selectedType != null) {
            selectedType = null
            onNodeTypeSelected?.invoke(null)
        }
        updateButtonStates()
    }

    private fun updateButtonStates() {
        for (b in buttons) {
            val sel = if (b.nodeType != null) b.nodeType == selectedType else connectionToolActive
            b.setSelected(sel)
        }
    }

    // ── 内部组件 ────────────────────────────────────────────────────────

    /** 调色板按钮：手绘背景 / 边框，避免 LookAndFeel 影响视觉一致性。
     *  [nodeType] 为 null 时表示这是一个非节点工具按钮（如连线工具）。 */
    private class PaletteButton(
        text: String,
        val nodeType: BpmnNodeType?,
        icon: Icon,
        private val onClick: (PaletteButton) -> Unit
    ) : JPanel() {

        private val label: JLabel = JLabel(text, icon, JLabel.LEADING)
        private var hover: Boolean = false
        private var selected: Boolean = false

        init {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            isOpaque = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            border = BorderFactory.createEmptyBorder(0, 6, 0, 8)
            preferredSize = Dimension(130, ITEM_HEIGHT)
            maximumSize = Dimension(Int.MAX_VALUE, ITEM_HEIGHT)
            minimumSize = Dimension(80, ITEM_HEIGHT)

            label.font = label.font.deriveFont(Font.PLAIN, 12f)
            label.foreground = TEXT_FG
            label.iconTextGap = 6
            add(label)
            add(Box.createHorizontalGlue())

            val mouse = object : MouseAdapter() {
                override fun mouseEntered(e: MouseEvent) {
                    hover = true
                    repaint()
                }
                override fun mouseExited(e: MouseEvent) {
                    hover = false
                    repaint()
                }
                override fun mouseClicked(e: MouseEvent) {
                    onClick(this@PaletteButton)
                }
            }
            addMouseListener(mouse)
        }

        fun setSelected(value: Boolean) {
            if (selected != value) {
                selected = value
                repaint()
            }
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                val bg = when {
                    selected -> SELECTED_BG
                    hover -> HOVER_BG
                    else -> DEFAULT_BG
                }
                g2.color = bg
                g2.fillRoundRect(0, 0, width - 1, height - 1, 6, 6)
                if (selected) {
                    g2.color = SELECTED_BORDER
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 6, 6)
                } else if (hover) {
                    g2.color = HOVER_BORDER
                    g2.drawRoundRect(0, 0, width - 1, height - 1, 6, 6)
                }
            } finally {
                g2.dispose()
            }
            super.paintComponent(g)
        }
    }

    /**
     * 为不同 BPMN 节点类型绘制 20×20 的迷你预览图标，与画布渲染风格保持一致：
     *   - 事件：圆形（开始绿、结束红）
     *   - 任务：蓝色圆角矩形 + 角标
     *   - 网关：橙色菱形 + 中心符号
     */
    private class BpmnTypeIcon(private val type: BpmnNodeType) : Icon {

        override fun getIconWidth(): Int = ICON_SIZE
        override fun getIconHeight(): Int = ICON_SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON
                )
                when (type) {
                    BpmnNodeType.START_EVENT -> drawCircle(g2, x, y, EVENT_GREEN, fill = true)
                    BpmnNodeType.END_EVENT -> drawCircle(g2, x, y, EVENT_RED, fill = false, stroke = 3f)
                    BpmnNodeType.TIMER_START_EVENT -> {
                        drawCircle(g2, x, y, EVENT_GREEN, fill = false, stroke = 1.6f)
                        drawClock(g2, x, y)
                    }
                    BpmnNodeType.MESSAGE_START_EVENT -> {
                        drawCircle(g2, x, y, EVENT_GREEN, fill = false, stroke = 1.6f)
                        drawEnvelope(g2, x, y)
                    }
                    BpmnNodeType.SERVICE_TASK -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                        drawGear(g2, x + 3, y + 3)
                    }
                    BpmnNodeType.USER_TASK -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                        drawPerson(g2, x + 3, y + 3)
                    }
                    BpmnNodeType.SCRIPT_TASK -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                        drawScript(g2, x + 3, y + 3)
                    }
                    BpmnNodeType.MANUAL_TASK -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                        drawHand(g2, x + 3, y + 3)
                    }
                    BpmnNodeType.MAIL_TASK,
                    BpmnNodeType.SEND_TASK,
                    BpmnNodeType.RECEIVE_TASK -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                        drawSmallEnvelope(g2, x + 3, y + 3)
                    }
                    BpmnNodeType.CALL_ACTIVITY -> {
                        drawTaskRect(g2, x, y, TASK_BLUE, bold = true)
                    }
                    BpmnNodeType.EXCLUSIVE_GATEWAY -> {
                        drawDiamond(g2, x, y, GATEWAY_ORANGE)
                        drawX(g2, x, y)
                    }
                    BpmnNodeType.PARALLEL_GATEWAY -> {
                        drawDiamond(g2, x, y, GATEWAY_ORANGE)
                        drawPlus(g2, x, y)
                    }
                    BpmnNodeType.INCLUSIVE_GATEWAY -> {
                        drawDiamond(g2, x, y, GATEWAY_ORANGE)
                        drawO(g2, x, y)
                    }
                    BpmnNodeType.EVENT_BASED_GATEWAY -> {
                        drawDiamond(g2, x, y, GATEWAY_ORANGE)
                        drawStar(g2, x, y)
                    }
                    else -> drawTaskRect(g2, x, y, TASK_BLUE, bold = false)
                }
            } finally {
                g2.dispose()
            }
        }

        // ── 基础图形 ──────────────────────────────────────────────

        private fun drawCircle(
            g2: Graphics2D,
            x: Int,
            y: Int,
            color: Color,
            fill: Boolean,
            stroke: Float = 1.6f
        ) {
            val pad = 2
            val w = ICON_SIZE - pad * 2
            if (fill) {
                g2.color = color
                g2.fillOval(x + pad, y + pad, w, w)
            } else {
                g2.color = color
                g2.stroke = BasicStroke(stroke)
                g2.drawOval(x + pad, y + pad, w, w)
            }
        }

        private fun drawTaskRect(g2: Graphics2D, x: Int, y: Int, color: Color, bold: Boolean) {
            val pad = 2
            val w = ICON_SIZE - pad * 2
            g2.color = TASK_FILL
            g2.fillRoundRect(x + pad, y + pad, w, w - 2, 4, 4)
            g2.color = color
            g2.stroke = BasicStroke(if (bold) 2.2f else 1.4f)
            g2.drawRoundRect(x + pad, y + pad, w, w - 2, 4, 4)
        }

        private fun drawDiamond(g2: Graphics2D, x: Int, y: Int, color: Color) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            val r = ICON_SIZE / 2 - 1
            val poly = Polygon(
                intArrayOf(cx, cx + r, cx, cx - r),
                intArrayOf(cy - r, cy, cy + r, cy),
                4
            )
            g2.color = GATEWAY_FILL
            g2.fillPolygon(poly)
            g2.color = color
            g2.stroke = BasicStroke(1.6f)
            g2.drawPolygon(poly)
        }

        // ── 角标符号 ──────────────────────────────────────────────

        private fun drawClock(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            g2.color = EVENT_GREEN
            g2.stroke = BasicStroke(1.2f)
            // 时针（向上）
            g2.drawLine(cx, cy, cx, cy - 4)
            // 分针（向右）
            g2.drawLine(cx, cy, cx + 3, cy)
        }

        private fun drawEnvelope(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            g2.color = EVENT_GREEN
            g2.stroke = BasicStroke(1.2f)
            val w = 8
            val h = 5
            g2.drawRect(cx - w / 2, cy - h / 2, w, h)
            g2.drawLine(cx - w / 2, cy - h / 2, cx, cy + 1)
            g2.drawLine(cx + w / 2, cy - h / 2, cx, cy + 1)
        }

        private fun drawSmallEnvelope(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + 7
            val cy = y + 7
            g2.color = TASK_BLUE
            g2.stroke = BasicStroke(1.2f)
            val w = 8
            val h = 5
            g2.drawRect(cx - w / 2, cy - h / 2, w, h)
            g2.drawLine(cx - w / 2, cy - h / 2, cx, cy + 1)
            g2.drawLine(cx + w / 2, cy - h / 2, cx, cy + 1)
        }

        private fun drawGear(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + 7
            val cy = y + 7
            g2.color = TASK_BLUE
            g2.stroke = BasicStroke(1.2f)
            // 简化齿轮：圆 + 4 根短突起
            g2.drawOval(cx - 3, cy - 3, 6, 6)
            g2.drawLine(cx, cy - 5, cx, cy - 3)
            g2.drawLine(cx, cy + 3, cx, cy + 5)
            g2.drawLine(cx - 5, cy, cx - 3, cy)
            g2.drawLine(cx + 3, cy, cx + 5, cy)
        }

        private fun drawPerson(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + 7
            val cy = y + 7
            g2.color = TASK_BLUE
            g2.stroke = BasicStroke(1.2f)
            // 头部
            g2.drawOval(cx - 2, cy - 5, 4, 4)
            // 身体（梯形/三角）
            g2.drawLine(cx - 4, cy + 4, cx, cy)
            g2.drawLine(cx + 4, cy + 4, cx, cy)
            g2.drawLine(cx - 4, cy + 4, cx + 4, cy + 4)
        }

        private fun drawScript(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + 7
            val cy = y + 7
            g2.color = TASK_BLUE
            g2.stroke = BasicStroke(1f)
            // 三条横线表示文档
            g2.drawLine(cx - 4, cy - 3, cx + 3, cy - 3)
            g2.drawLine(cx - 4, cy, cx + 3, cy)
            g2.drawLine(cx - 4, cy + 3, cx + 1, cy + 3)
        }

        private fun drawHand(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + 7
            val cy = y + 7
            g2.color = TASK_BLUE
            g2.stroke = BasicStroke(1.2f)
            // 简化为"五指"：一个掌心 + 四根短线
            g2.drawRoundRect(cx - 3, cy - 1, 6, 5, 2, 2)
            g2.drawLine(cx - 2, cy - 1, cx - 2, cy - 4)
            g2.drawLine(cx, cy - 1, cx, cy - 5)
            g2.drawLine(cx + 2, cy - 1, cx + 2, cy - 4)
        }

        private fun drawX(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            g2.color = GATEWAY_ORANGE
            g2.stroke = BasicStroke(1.8f)
            g2.drawLine(cx - 3, cy - 3, cx + 3, cy + 3)
            g2.drawLine(cx + 3, cy - 3, cx - 3, cy + 3)
        }

        private fun drawPlus(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            g2.color = GATEWAY_ORANGE
            g2.stroke = BasicStroke(1.8f)
            g2.drawLine(cx - 4, cy, cx + 4, cy)
            g2.drawLine(cx, cy - 4, cx, cy + 4)
        }

        private fun drawO(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            g2.color = GATEWAY_ORANGE
            g2.stroke = BasicStroke(1.8f)
            g2.drawOval(cx - 3, cy - 3, 6, 6)
        }

        private fun drawStar(g2: Graphics2D, x: Int, y: Int) {
            val cx = x + ICON_SIZE / 2
            val cy = y + ICON_SIZE / 2
            // 五角星：5 个外顶点 + 5 个内顶点交替
            val outer = 4.0
            val inner = 1.7
            val xs = IntArray(10)
            val ys = IntArray(10)
            for (i in 0 until 10) {
                val angle = -Math.PI / 2 + i * Math.PI / 5
                val r = if (i % 2 == 0) outer else inner
                xs[i] = (cx + r * Math.cos(angle)).toInt()
                ys[i] = (cy + r * Math.sin(angle)).toInt()
            }
            val poly = Polygon(xs, ys, 10)
            g2.color = GATEWAY_ORANGE
            g2.stroke = BasicStroke(1f)
            g2.drawPolygon(poly)
        }

        companion object {
            private const val ICON_SIZE = 20

            private val EVENT_GREEN = Color(0x4C, 0xAF, 0x50)
            private val EVENT_RED = Color(0xE5, 0x39, 0x35)
            private val TASK_BLUE = Color(0x1A, 0x73, 0xE8)
            private val TASK_FILL = Color(0xE8, 0xF0, 0xFE)
            private val GATEWAY_ORANGE = Color(0xF5, 0x9D, 0x07)
            private val GATEWAY_FILL = Color(0xFE, 0xF1, 0xDD)
        }
    }

    /** 顺序流图标：水平实线 + 右端三角箭头，与画布连线渲染风格一致。 */
    private class SequenceFlowIcon : Icon {
        override fun getIconWidth(): Int = ICON_SIZE
        override fun getIconHeight(): Int = ICON_SIZE

        override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
            val g2 = g.create() as Graphics2D
            try {
                g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                val cy = y + ICON_SIZE / 2
                val x0 = x + 2
                val x1 = x + ICON_SIZE - 4
                g2.color = ARROW_COLOR
                g2.stroke = BasicStroke(1.6f)
                g2.drawLine(x0, cy, x1, cy)
                // 三角箭头
                val head = Polygon(
                    intArrayOf(x1 + 4, x1 - 1, x1 - 1),
                    intArrayOf(cy, cy - 3, cy + 3),
                    3
                )
                g2.fillPolygon(head)
            } finally {
                g2.dispose()
            }
        }

        companion object {
            private const val ICON_SIZE = 20
            private val ARROW_COLOR = Color(0x42, 0x42, 0x42)
        }
    }

    companion object {
        private val BG = Color(0xF5, 0xF5, 0xF5)
        private val SECTION_FG = Color(0x42, 0x42, 0x42)
        private val TEXT_FG = Color(0x21, 0x21, 0x21)

        private val DEFAULT_BG = Color(0xF5, 0xF5, 0xF5)
        private val HOVER_BG = Color(0xE3, 0xF2, 0xFD)
        private val HOVER_BORDER = Color(0x90, 0xCA, 0xF9)
        private val SELECTED_BG = Color(0xBB, 0xDE, 0xFB)
        private val SELECTED_BORDER = Color(0x1A, 0x73, 0xE8)

        private const val ITEM_HEIGHT = 28
        private const val SECTION_TOP_GAP = 8
    }
}
