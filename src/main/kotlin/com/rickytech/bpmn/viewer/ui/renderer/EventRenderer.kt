package com.rickytech.bpmn.viewer.ui.renderer

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.END_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.END_FILL
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.END_STROKE
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.ICON_COLOR
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_FILL
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_TEXT
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.SELECTED_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.START_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.START_FILL
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.START_STROKE
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import kotlin.math.min

/**
 * 事件类节点（开始/结束/中间/边界事件）的圆形渲染器，包含所有事件标志图绘制。
 */
internal class EventRenderer {

    /** 开始事件：浅绿底 + 绿色描边圆。 */
    fun drawStartEvent(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        drawEvent(g, n, START_FILL, START_BORDER, START_STROKE, isSelected)
    }

    /** 结束事件：浅红底 + 深红粗描边圆。 */
    fun drawEndEvent(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        drawEvent(g, n, END_FILL, END_BORDER, END_STROKE, isSelected)
    }

    /** 带图标的开始事件。 */
    fun drawStartEventWithIcon(g: Graphics2D, n: BpmnNode, isSelected: Boolean, icon: EventIcon) {
        drawEventWithIcon(g, n, START_FILL, START_BORDER, START_STROKE, isSelected, icon)
    }

    /** 带图标的结束事件。 */
    fun drawEndEventWithIcon(g: Graphics2D, n: BpmnNode, isSelected: Boolean, icon: EventIcon) {
        drawEventWithIcon(g, n, END_FILL, END_BORDER, END_STROKE, isSelected, icon)
    }

    /** 边界事件：双圆环，附着在任务/子流程边缘。 */
    fun drawBoundaryEvent(g: Graphics2D, n: BpmnNode, isSelected: Boolean, icon: EventIcon) {
        drawEventWithIcon(g, n, NODE_FILL, NODE_BORDER, START_STROKE, isSelected, icon, throwing = false)
    }

    /** 中间事件：双圆环，可表示捕获或抛出。 */
    fun drawIntermediateEvent(
        g: Graphics2D,
        n: BpmnNode,
        isSelected: Boolean,
        icon: EventIcon,
        throwing: Boolean
    ) {
        drawEventWithIcon(g, n, NODE_FILL, NODE_BORDER, START_STROKE, isSelected, icon, throwing)
    }

    private fun drawEvent(
        g: Graphics2D,
        n: BpmnNode,
        fill: Color,
        border: Color,
        strokeW: Float,
        isSelected: Boolean
    ) {
        val ellipse = Ellipse2D.Double(n.x, n.y, n.width, n.height)
        g.color = fill
        g.fill(ellipse)
        if (isSelected) {
            g.color = SELECTED_BORDER
            g.stroke = BasicStroke(strokeW + 1.2f)
        } else {
            g.color = border
            g.stroke = BasicStroke(strokeW)
        }
        g.draw(ellipse)

        // label below the event marker
        val label = if (n.name.isNotBlank()) n.name else n.id
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(label)
        g.drawString(
            label,
            (n.x + n.width / 2.0 - tw / 2.0).toFloat(),
            (n.y + n.height + fm.ascent + 4).toFloat()
        )
    }

    private fun drawEventWithIcon(
        g: Graphics2D,
        n: BpmnNode,
        fill: Color,
        border: Color,
        strokeW: Float,
        isSelected: Boolean,
        icon: EventIcon,
        throwing: Boolean = false
    ) {
        val ellipse = Ellipse2D.Double(n.x, n.y, n.width, n.height)
        g.color = fill
        g.fill(ellipse)

        val typeName = n.type.name
        val isBoundary = typeName.startsWith("BOUNDARY")
        val isIntermediate = typeName.startsWith("INTERMEDIATE")
        val drawColor = if (isSelected) SELECTED_BORDER else border

        if (isIntermediate || isBoundary) {
            // double-circle event
            val outerStroke = if (isBoundary && !n.cancelActivity) {
                BasicStroke(
                    strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                    0f, floatArrayOf(4f, 3f), 0f
                )
            } else {
                BasicStroke(strokeW)
            }
            g.color = drawColor
            g.stroke = outerStroke
            g.draw(ellipse)
            val inner = Ellipse2D.Double(n.x + 3.0, n.y + 3.0, n.width - 6.0, n.height - 6.0)
            g.stroke = BasicStroke(strokeW)
            g.draw(inner)
        } else {
            g.color = drawColor
            g.stroke = if (isSelected) BasicStroke(strokeW + 1.2f) else BasicStroke(strokeW)
            g.draw(ellipse)
        }

        val cx = n.x + n.width / 2.0
        val cy = n.y + n.height / 2.0
        val r = (min(n.width, n.height) / 2.0) - (if (isIntermediate || isBoundary) 8.0 else 6.0)
        when (icon) {
            EventIcon.TIMER     -> drawTimerIcon(g, cx, cy, r)
            EventIcon.MESSAGE   -> drawMessageIcon(g, cx, cy, r, throwing)
            EventIcon.SIGNAL    -> drawSignalIcon(g, cx, cy, r, throwing)
            EventIcon.ERROR     -> drawErrorIcon(g, cx, cy, r, throwing)
            EventIcon.TERMINATE -> drawTerminateIcon(g, cx, cy, r)
            EventIcon.CANCEL    -> drawCancelIcon(g, cx, cy, r)
            EventIcon.COMPENSATE -> drawCompensateIcon(g, cx, cy, r, throwing)
        }

        // label below the event marker
        val label = if (n.name.isNotBlank()) n.name else n.id
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(label)
        g.drawString(
            label,
            (n.x + n.width / 2.0 - tw / 2.0).toFloat(),
            (n.y + n.height + fm.ascent + 4).toFloat()
        )
    }

    // ─── Event icons ─────────────────────────────────────────────────────

    private fun drawTimerIcon(g: Graphics2D, cx: Double, cy: Double, r: Double) {
        if (r <= 0) return
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        g.draw(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2))
        // tick marks at 12/3/6/9
        g.draw(Line2D.Double(cx, cy - r, cx, cy - r * 0.75))
        g.draw(Line2D.Double(cx, cy + r * 0.75, cx, cy + r))
        g.draw(Line2D.Double(cx - r, cy, cx - r * 0.75, cy))
        g.draw(Line2D.Double(cx + r * 0.75, cy, cx + r, cy))
        // hour & minute hands
        g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.draw(Line2D.Double(cx, cy, cx, cy - r * 0.6))
        g.draw(Line2D.Double(cx, cy, cx + r * 0.5, cy + r * 0.4))
    }

    private fun drawMessageIcon(g: Graphics2D, cx: Double, cy: Double, r: Double, throwing: Boolean) {
        if (r <= 0) return
        val w = r * 1.7
        val h = r * 1.15
        val x = cx - w / 2.0
        val y = cy - h / 2.0
        val rect = Rectangle2D.Double(x, y, w, h)
        if (throwing) {
            g.color = ICON_COLOR
            g.fill(rect)
            g.color = NODE_FILL
        } else {
            g.color = ICON_COLOR
        }
        g.stroke = BasicStroke(1.0f)
        g.draw(rect)
        val flap = Path2D.Double()
        flap.moveTo(x, y)
        flap.lineTo(cx, y + h * 0.55)
        flap.lineTo(x + w, y)
        g.draw(flap)
    }

    private fun drawSignalIcon(g: Graphics2D, cx: Double, cy: Double, r: Double, throwing: Boolean) {
        if (r <= 0) return
        val tri = Path2D.Double()
        tri.moveTo(cx, cy - r)
        tri.lineTo(cx + r * 0.95, cy + r * 0.65)
        tri.lineTo(cx - r * 0.95, cy + r * 0.65)
        tri.closePath()
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (throwing) g.fill(tri)
        g.draw(tri)
    }

    private fun drawErrorIcon(g: Graphics2D, cx: Double, cy: Double, r: Double, throwing: Boolean) {
        if (r <= 0) return
        val path = Path2D.Double()
        path.moveTo(cx - r * 0.8, cy + r * 0.6)
        path.lineTo(cx - r * 0.05, cy - r * 0.15)
        path.lineTo(cx + r * 0.15, cy + r * 0.25)
        path.lineTo(cx + r * 0.8, cy - r * 0.6)
        path.lineTo(cx + r * 0.25, cy + r * 0.05)
        path.lineTo(cx - r * 0.25, cy - r * 0.05)
        path.closePath()
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (throwing) g.fill(path)
        g.draw(path)
    }

    private fun drawTerminateIcon(g: Graphics2D, cx: Double, cy: Double, r: Double) {
        if (r <= 0) return
        val ir = r * 0.75
        g.color = Color.BLACK
        g.fill(Ellipse2D.Double(cx - ir, cy - ir, ir * 2, ir * 2))
    }

    private fun drawCancelIcon(g: Graphics2D, cx: Double, cy: Double, r: Double) {
        if (r <= 0) return
        g.color = ICON_COLOR
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val s = r * 0.75
        g.draw(Line2D.Double(cx - s, cy - s, cx + s, cy + s))
        g.draw(Line2D.Double(cx + s, cy - s, cx - s, cy + s))
    }

    private fun drawCompensateIcon(g: Graphics2D, cx: Double, cy: Double, r: Double, throwing: Boolean) {
        if (r <= 0) return
        val s = r * 0.55
        val left = Path2D.Double()
        left.moveTo(cx - s * 1.6, cy)
        left.lineTo(cx - s * 0.2, cy - s)
        left.lineTo(cx - s * 0.2, cy + s)
        left.closePath()
        val right = Path2D.Double()
        right.moveTo(cx - s * 0.2, cy)
        right.lineTo(cx + s * 1.2, cy - s)
        right.lineTo(cx + s * 1.2, cy + s)
        right.closePath()
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        if (throwing) {
            g.fill(left); g.fill(right)
        }
        g.draw(left); g.draw(right)
    }
}

/**
 * 事件标志图种类。
 */
internal enum class EventIcon { TIMER, MESSAGE, SIGNAL, ERROR, TERMINATE, CANCEL, COMPENSATE }
