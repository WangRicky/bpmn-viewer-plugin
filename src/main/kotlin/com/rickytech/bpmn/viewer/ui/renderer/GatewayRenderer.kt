package com.rickytech.bpmn.viewer.ui.renderer

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.GATEWAY_STROKE
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
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import kotlin.math.cos
import kotlin.math.sin

/**
 * 网关（Gateway）类节点的菱形渲染器，支持 4 种内部标志：X / + / 圆 / 五边形。
 */
internal class GatewayRenderer {

    fun drawGateway(g: Graphics2D, n: BpmnNode, marker: GatewayMarker, isSelected: Boolean) {
        val cx = n.x + n.width / 2.0
        val cy = n.y + n.height / 2.0
        val path = Path2D.Double()
        path.moveTo(cx, n.y)
        path.lineTo(n.x + n.width, cy)
        path.lineTo(cx, n.y + n.height)
        path.lineTo(n.x, cy)
        path.closePath()

        // soft shadow
        val prevComp = g.composite
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f)
        g.color = Color.BLACK
        val shadow = Path2D.Double(path)
        shadow.transform(AffineTransform.getTranslateInstance(1.5, 2.5))
        g.fill(shadow)
        g.composite = prevComp

        g.color = NODE_FILL
        g.fill(path)
        if (isSelected) {
            g.color = SELECTED_BORDER
            g.stroke = BasicStroke(2.4f)
        } else {
            g.color = NODE_BORDER
            g.stroke = BasicStroke(GATEWAY_STROKE)
        }
        g.draw(path)

        // marker glyph
        g.color = ICON_COLOR
        g.stroke = BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        val span = n.width * 0.25
        when (marker) {
            GatewayMarker.X -> {
                val markerPath = Path2D.Double()
                markerPath.moveTo(cx - span, cy - span)
                markerPath.lineTo(cx + span, cy + span)
                markerPath.moveTo(cx + span, cy - span)
                markerPath.lineTo(cx - span, cy + span)
                g.draw(markerPath)
            }
            GatewayMarker.PLUS -> {
                val plusPath = Path2D.Double()
                plusPath.moveTo(cx - span, cy)
                plusPath.lineTo(cx + span, cy)
                plusPath.moveTo(cx, cy - span)
                plusPath.lineTo(cx, cy + span)
                g.draw(plusPath)
            }
            GatewayMarker.CIRCLE -> {
                val cr = span * 0.95
                g.stroke = BasicStroke(2.4f)
                g.draw(Ellipse2D.Double(cx - cr, cy - cr, cr * 2, cr * 2))
            }
            GatewayMarker.PENTAGON -> {
                val pr = span * 0.85
                val pent = Path2D.Double()
                for (i in 0 until 5) {
                    val a = -Math.PI / 2 + i * 2 * Math.PI / 5
                    val px = cx + pr * cos(a)
                    val py = cy + pr * sin(a)
                    if (i == 0) pent.moveTo(px, py) else pent.lineTo(px, py)
                }
                pent.closePath()
                g.stroke = BasicStroke(1.5f)
                g.draw(pent)
            }
        }

        // label below the gateway
        val label = if (n.name.isNotBlank()) n.name else ""
        if (label.isNotBlank()) {
            g.color = NODE_TEXT
            g.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(label)
            g.drawString(
                label,
                (cx - tw / 2.0).toFloat(),
                (n.y + n.height + fm.ascent + 4).toFloat()
            )
        }
    }
}

/**
 * 网关菱形内部的标志图：排他(X)、并行(+)、包容(○)、事件(五边形)。
 */
internal enum class GatewayMarker { X, PLUS, CIRCLE, PENTAGON }
