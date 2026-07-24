package com.rickytech.bpmn.viewer.ui.renderer

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_BORDER
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.NODE_TEXT
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.SELECTED_BORDER
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.RoundRectangle2D

/**
 * 容器/布局类节点（子流程、泳池、泳道、文本注释）的渲染器。
 */
internal class ContainerRenderer {

    /** 子流程或事件子流程：圆角矩形 + 左上角标题 + 底部"+"展开标记。 */
    fun drawSubProcess(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        val rect = RoundRectangle2D.Double(n.x, n.y, n.width, n.height, 12.0, 12.0)
        g.color = Color(0xF8, 0xF8, 0xF8)
        g.fill(rect)
        g.color = if (isSelected) SELECTED_BORDER else NODE_BORDER
        val isEventSub = n.type == BpmnNodeType.EVENT_SUB_PROCESS
        g.stroke = BasicStroke(
            if (isEventSub) 1.0f else 2.0f,
            BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f,
            if (isEventSub) floatArrayOf(5f, 3f) else null, 0f
        )
        g.draw(rect)
        // top-left title
        val title = n.name.ifBlank { n.id }
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        val fm = g.fontMetrics
        g.drawString(title, (n.x + 10).toFloat(), (n.y + fm.ascent + 6).toFloat())
        // bottom-center expand marker (+)
        val cx = n.x + n.width / 2.0
        val by = n.y + n.height - 12.0
        g.stroke = BasicStroke(1.5f)
        g.color = NODE_BORDER
        g.draw(Line2D.Double(cx - 5.0, by, cx + 5.0, by))
        g.draw(Line2D.Double(cx, by - 5.0, cx, by + 5.0))
    }

    /** 泳池：白底矩形 + 左侧 30px 名称条 + 旋转 90° 的标题。 */
    fun drawPool(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        g.color = Color.WHITE
        g.fillRect(n.x.toInt(), n.y.toInt(), n.width.toInt(), n.height.toInt())
        g.color = if (isSelected) SELECTED_BORDER else NODE_BORDER
        g.stroke = BasicStroke(1.5f)
        g.drawRect(n.x.toInt(), n.y.toInt(), n.width.toInt(), n.height.toInt())
        // left name strip (30px wide)
        g.draw(Line2D.Double(n.x + 30.0, n.y, n.x + 30.0, n.y + n.height))
        // rotated name
        val title = n.name.ifBlank { n.id }
        val prev = g.transform
        g.rotate(-Math.PI / 2.0, n.x + 15.0, n.y + n.height / 2.0)
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.BOLD, 12)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(title)
        g.drawString(
            title,
            (n.x + 15.0 - tw / 2.0).toFloat(),
            (n.y + n.height / 2.0 + fm.ascent / 2.0).toFloat()
        )
        g.transform = prev
    }

    /** 泳道：白底矩形 + 左侧旋转标题。 */
    fun drawLane(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        g.color = Color.WHITE
        g.fillRect(n.x.toInt(), n.y.toInt(), n.width.toInt(), n.height.toInt())
        g.color = if (isSelected) SELECTED_BORDER else NODE_BORDER
        g.stroke = BasicStroke(1.0f)
        g.drawRect(n.x.toInt(), n.y.toInt(), n.width.toInt(), n.height.toInt())
        // rotated label on the left edge
        val title = n.name.ifBlank { n.id }
        val prev = g.transform
        g.rotate(-Math.PI / 2.0, n.x + 12.0, n.y + n.height / 2.0)
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        val fm = g.fontMetrics
        val tw = fm.stringWidth(title)
        g.drawString(
            title,
            (n.x + 12.0 - tw / 2.0).toFloat(),
            (n.y + n.height / 2.0 + fm.ascent / 2.0).toFloat()
        )
        g.transform = prev
    }

    /** 文本注释：左侧开口方括号 [ + 文本。 */
    fun drawTextAnnotation(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        // open bracket on the left edge
        g.color = if (isSelected) SELECTED_BORDER else Color(0x99, 0x99, 0x99)
        g.stroke = BasicStroke(1.0f)
        val xi = n.x.toInt()
        val yi = n.y.toInt()
        val yh = (n.y + n.height).toInt()
        g.drawLine(xi + 10, yi, xi, yi)
        g.drawLine(xi, yi, xi, yh)
        g.drawLine(xi, yh, xi + 10, yh)
        // text
        g.color = NODE_TEXT
        g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        val fm = g.fontMetrics
        val text = n.name
        g.drawString(
            text,
            (n.x + 14).toFloat(),
            (n.y + n.height / 2.0 + fm.ascent / 2.0 - 2).toFloat()
        )
    }
}
