package com.rickytech.bpmn.viewer.ui.renderer

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.ICON_COLOR
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_FILL
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_TEXT
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.SELECTED_BORDER
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * 任务（Task）类节点的圆角矩形渲染器，内含所有任务图标的具体绘制实现。
 */
internal class TaskRenderer {

    /**
     * 绘制任务节点：圆角矩形 + 左上角图标 + 居中标题 + 多实例标记。
     */
    fun drawTask(
        g: Graphics2D,
        n: BpmnNode,
        isSelected: Boolean,
        icon: NodeIcon,
        borderStroke: Float
    ) {
        val rect = RoundRectangle2D.Double(n.x, n.y, n.width, n.height, 12.0, 12.0)

        // very subtle shadow for depth
        val prevComp = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f)
        g.color = Color.BLACK
        g.fill(RoundRectangle2D.Double(n.x + 1.5, n.y + 2.5, n.width, n.height, 12.0, 12.0))
        g.composite = prevComp

        // body
        g.color = NODE_FILL
        g.fill(rect)
        if (isSelected) {
            g.color = SELECTED_BORDER
            g.stroke = BasicStroke(maxOf(borderStroke, 2.4f))
        } else {
            g.color = NODE_BORDER
            g.stroke = BasicStroke(borderStroke)
        }
        g.draw(rect)

        // top-left marker icon
        val iconSize = 16.0
        val iconX = n.x + 8.0
        val iconY = n.y + 8.0
        when (icon) {
            NodeIcon.SERVICE       -> drawGearIcon(g, iconX, iconY, iconSize)
            NodeIcon.USER          -> drawUserIcon(g, iconX, iconY, iconSize)
            NodeIcon.MANUAL        -> drawHandIcon(g, iconX, iconY, iconSize)
            NodeIcon.CALL          -> { /* call activity uses a thicker frame instead of an icon */ }
            NodeIcon.SCRIPT        -> drawScriptIcon(g, iconX, iconY, iconSize)
            NodeIcon.BUSINESS_RULE -> drawBusinessRuleIcon(g, iconX, iconY, iconSize)
            NodeIcon.RECEIVE       -> drawReceiveIcon(g, iconX, iconY, iconSize)
            NodeIcon.SEND          -> drawSendIcon(g, iconX, iconY, iconSize)
            NodeIcon.MAIL          -> drawMailIcon(g, iconX, iconY, iconSize)
        }

        // centered title
        val title = n.name.ifBlank { n.id }
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 13)
        val fm = g.fontMetrics
        val maxWidth = (n.width - 24).toInt()
        val clipped = clipText(title, fm, maxWidth)
        val tw = fm.stringWidth(clipped)
        val tx = (n.x + n.width / 2.0 - tw / 2.0).toFloat()
        val ty = (n.y + n.height / 2.0 + fm.ascent / 2.0 - 2).toFloat()
        g.drawString(clipped, tx, ty)

        // multi-instance marker (bottom-center)
        n.isSequential?.let { seq ->
            drawMultiInstanceMarker(g, n, seq)
        }
    }

    private fun drawMultiInstanceMarker(g: Graphics2D, n: BpmnNode, sequential: Boolean) {
        val cx = n.x + n.width / 2.0
        val baseY = n.y + n.height - 6.0
        val markerLen = 10.0
        val gap = 3.0
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER)
        if (sequential) {
            // three vertical bars side-by-side ‖‖‖
            for (i in -1..1) {
                val mx = cx + i * gap
                g.draw(Line2D.Double(mx, baseY - markerLen, mx, baseY))
            }
        } else {
            // three horizontal bars stacked vertically ≡
            for (i in -1..1) {
                val my = baseY - markerLen / 2.0 + i * gap
                g.draw(Line2D.Double(cx - markerLen / 2.0, my, cx + markerLen / 2.0, my))
            }
        }
    }

    // ─── Task icons ──────────────────────────────────────────────────────

    private fun drawGearIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        val cx = x + size / 2.0
        val cy = y + size / 2.0
        val outer = size / 2.0
        val inner = size / 2.0 * 0.62
        val hub = size / 2.0 * 0.35
        // 8 teeth gear: alternate radii
        val teeth = 8
        val path = Path2D.Double()
        for (i in 0 until teeth * 2) {
            val r = if (i % 2 == 0) outer else inner
            val a = Math.PI * 2.0 * i / (teeth * 2)
            val px = cx + r * cos(a)
            val py = cy + r * sin(a)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.closePath()
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        g.fill(path)
        // inner hub punched out in white
        g.color = NODE_FILL
        g.fill(Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2))
        g.color = ICON_COLOR
        g.draw(Ellipse2D.Double(cx - hub, cy - hub, hub * 2, hub * 2))
    }

    private fun drawUserIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.2f)
        // head
        val headD = size * 0.45
        val headX = x + (size - headD) / 2.0
        val headY = y
        g.draw(Ellipse2D.Double(headX, headY, headD, headD))
        // shoulders / body — stylised half-circle base
        val bodyW = size * 0.95
        val bodyH = size * 0.55
        val bodyX = x + (size - bodyW) / 2.0
        val bodyY = y + size - bodyH
        val body = Path2D.Double()
        body.moveTo(bodyX, bodyY + bodyH)
        body.curveTo(
            bodyX, bodyY + bodyH * 0.1,
            bodyX + bodyW, bodyY + bodyH * 0.1,
            bodyX + bodyW, bodyY + bodyH
        )
        g.draw(body)
    }

    private fun drawHandIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.2f)
        // simplified palm + 4 fingers + thumb
        val palmX = x + size * 0.18
        val palmY = y + size * 0.45
        val palmW = size * 0.55
        val palmH = size * 0.45
        g.draw(RoundRectangle2D.Double(palmX, palmY, palmW, palmH, 4.0, 4.0))
        // four fingers as short vertical caps above the palm
        val fingerW = palmW / 4.0 * 0.7
        for (i in 0 until 4) {
            val fx = palmX + i * (palmW / 4.0) + (palmW / 4.0 - fingerW) / 2.0
            val fh = if (i == 1 || i == 2) size * 0.32 else size * 0.26
            val fy = palmY - fh
            g.draw(RoundRectangle2D.Double(fx, fy, fingerW, fh, 2.0, 2.0))
        }
        // thumb
        val thumbW = size * 0.18
        val thumbH = size * 0.22
        g.draw(RoundRectangle2D.Double(
            palmX + palmW - 1.0, palmY + palmH * 0.15, thumbW, thumbH, 3.0, 3.0
        ))
    }

    private fun drawScriptIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        // page outline with folded corner
        val px = x + size * 0.18
        val py = y + size * 0.05
        val pw = size * 0.65
        val ph = size * 0.9
        val foldSize = size * 0.18
        val page = Path2D.Double()
        page.moveTo(px, py)
        page.lineTo(px + pw - foldSize, py)
        page.lineTo(px + pw, py + foldSize)
        page.lineTo(px + pw, py + ph)
        page.lineTo(px, py + ph)
        page.closePath()
        g.draw(page)
        g.draw(Line2D.Double(px + pw - foldSize, py, px + pw - foldSize, py + foldSize))
        g.draw(Line2D.Double(px + pw - foldSize, py + foldSize, px + pw, py + foldSize))
        // three slanted text lines
        for (i in 0 until 3) {
            val ly = py + ph * (0.40 + i * 0.20)
            g.draw(Line2D.Double(px + size * 0.08, ly, px + pw - size * 0.10, ly - size * 0.04))
        }
    }

    private fun drawBusinessRuleIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        val gx = x + size * 0.05
        val gy = y + size * 0.20
        val gw = size * 0.9
        val gh = size * 0.6
        g.draw(Rectangle2D.Double(gx, gy, gw, gh))
        // header band fill
        val headerH = gh / 3.0
        g.fill(Rectangle2D.Double(gx, gy, gw, headerH))
        g.color = ICON_COLOR
        // row separator
        g.draw(Line2D.Double(gx, gy + headerH, gx + gw, gy + headerH))
        g.draw(Line2D.Double(gx, gy + headerH * 2, gx + gw, gy + headerH * 2))
        // column separators
        g.draw(Line2D.Double(gx + gw / 3.0, gy, gx + gw / 3.0, gy + gh))
        g.draw(Line2D.Double(gx + gw * 2.0 / 3.0, gy, gx + gw * 2.0 / 3.0, gy + gh))
    }

    private fun drawSendIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        val ex = x + size * 0.10
        val ey = y + size * 0.25
        val ew = size * 0.80
        val eh = size * 0.55
        // filled envelope body
        g.color = ICON_COLOR
        g.fill(Rectangle2D.Double(ex, ey, ew, eh))
        // V flap drawn in white on top of fill
        g.color = NODE_FILL
        g.stroke = BasicStroke(1.2f)
        val flap = Path2D.Double()
        flap.moveTo(ex, ey)
        flap.lineTo(ex + ew / 2.0, ey + eh * 0.55)
        flap.lineTo(ex + ew, ey)
        g.draw(flap)
        // outline
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        g.draw(Rectangle2D.Double(ex, ey, ew, eh))
    }

    private fun drawReceiveIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        val ex = x + size * 0.10
        val ey = y + size * 0.25
        val ew = size * 0.80
        val eh = size * 0.55
        g.color = ICON_COLOR
        g.stroke = BasicStroke(1.0f)
        g.draw(Rectangle2D.Double(ex, ey, ew, eh))
        val flap = Path2D.Double()
        flap.moveTo(ex, ey)
        flap.lineTo(ex + ew / 2.0, ey + eh * 0.55)
        flap.lineTo(ex + ew, ey)
        g.draw(flap)
    }

    private fun drawMailIcon(g: Graphics2D, x: Double, y: Double, size: Double) {
        // Mail task reuses the send/envelope glyph.
        drawSendIcon(g, x, y, size)
    }
}

/**
 * 任务节点的左上角小图标种类。
 */
internal enum class NodeIcon {
    SERVICE, USER, MANUAL, CALL,
    SCRIPT, BUSINESS_RULE, RECEIVE, SEND, MAIL
}
