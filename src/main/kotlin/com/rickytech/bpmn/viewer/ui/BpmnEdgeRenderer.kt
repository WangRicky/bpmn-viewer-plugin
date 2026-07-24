package com.rickytech.bpmn.viewer.ui

import com.rickytech.bpmn.viewer.model.BpmnEdge
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 负责所有BPMN连线/边的2D渲染。
 * 从 BpmnGraphPanel 中提取以降低单类复杂度。
 */
class BpmnEdgeRenderer(private val panelBackground: Color = Color.WHITE) {

    companion object {
        // ── Edges ────────────────────────────────────────────────────────────
        private val EDGE_COLOR = Color(0x37, 0x41, 0x51)         // slate-700
        private val EDGE_SELECTED_COLOR = Color(0x1A, 0x73, 0xE8) // selection blue
        private val EDGE_NAME_TEXT = Color(0x6B, 0x72, 0x80)     // slate-500

        // Stroke widths.
        private const val EDGE_STROKE = 1.2f
    }

    internal fun drawEdge(g: Graphics2D, edge: BpmnEdge, isSelected: Boolean) {
        val pts = edge.routePoints
        if (pts.size < 2) return

        val color = if (isSelected) EDGE_SELECTED_COLOR else EDGE_COLOR
        val width = if (isSelected) EDGE_STROKE + 0.6f else EDGE_STROKE
        val originalStroke = g.stroke

        g.color = color

        // Determine stroke style based on edge type
        val stroke = when {
            edge.isMessageFlow -> BasicStroke(
                width.coerceAtLeast(1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0f, floatArrayOf(10f, 5f), 0f
            )
            edge.isAssociation -> BasicStroke(
                width.coerceAtLeast(1.0f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                0f, floatArrayOf(3f, 3f), 0f
            )
            else -> BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        }
        g.stroke = stroke

        val path = Path2D.Double()
        path.moveTo(pts[0].first, pts[0].second)
        for (i in 1 until pts.size) {
            path.lineTo(pts[i].first, pts[i].second)
        }
        g.draw(path)

        // Arrow head / endpoint markers based on edge type
        val last = pts[pts.size - 1]
        val prev = pts[pts.size - 2]
        when {
            edge.isMessageFlow -> {
                // Start: open circle marker
                drawOpenCircleMarker(g, pts[0], pts[1], color)
                // End: open (non-filled) arrow head
                drawOpenArrowHead(g, prev.first, prev.second, last.first, last.second, color)
            }
            edge.isAssociation -> {
                // No arrow head for association lines
            }
            else -> {
                // Default solid triangular arrow head (for SequenceFlow and DefaultFlow)
                drawArrowHead(g, prev.first, prev.second, last.first, last.second, color)
            }
        }

        // DefaultFlow: slash mark near source
        if (edge.isDefaultFlow) {
            drawDefaultFlowSlash(g, pts[0], pts[1], color, width)
        }

        // Restore original stroke
        g.stroke = originalStroke

        // Optional flow name only — no condition expression on the wire.
        // Tiny gray italic label, no background pill, placed near the polyline midpoint.
        val name = edge.name?.takeIf { it.isNotBlank() }
        if (name != null) {
            val (lx, ly) = midpointOnPolyline(pts)
            g.font = Font(Font.SANS_SERIF, Font.ITALIC, 10)
            val fm = g.fontMetrics
            val tw = fm.stringWidth(name)
            g.color = EDGE_NAME_TEXT
            g.drawString(name, (lx - tw / 2.0).toFloat(), (ly - 4.0).toFloat())
        }
    }

    private fun midpointOnPolyline(pts: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (pts.size == 2) {
            return ((pts[0].first + pts[1].first) / 2.0) to ((pts[0].second + pts[1].second) / 2.0)
        }
        var total = 0.0
        val segLen = DoubleArray(pts.size - 1)
        for (i in 0 until pts.size - 1) {
            val l = Point2D.distance(pts[i].first, pts[i].second, pts[i + 1].first, pts[i + 1].second)
            segLen[i] = l
            total += l
        }
        val target = total / 2.0
        var acc = 0.0
        for (i in 0 until pts.size - 1) {
            if (acc + segLen[i] >= target) {
                val remaining = target - acc
                val t = if (segLen[i] == 0.0) 0.0 else remaining / segLen[i]
                val x = pts[i].first + (pts[i + 1].first - pts[i].first) * t
                val y = pts[i].second + (pts[i + 1].second - pts[i].second) * t
                return x to y
            }
            acc += segLen[i]
        }
        return pts.last()
    }

    private fun drawArrowHead(g: Graphics2D, fx: Double, fy: Double, tx: Double, ty: Double, color: Color = EDGE_COLOR) {
        val angle = atan2(ty - fy, tx - fx)
        val len = 10.0
        val spread = Math.toRadians(20.0)
        val x1 = tx - len * cos(angle - spread)
        val y1 = ty - len * sin(angle - spread)
        val x2 = tx - len * cos(angle + spread)
        val y2 = ty - len * sin(angle + spread)
        val arrow = Path2D.Double()
        arrow.moveTo(tx, ty)
        arrow.lineTo(x1, y1)
        arrow.lineTo(x2, y2)
        arrow.closePath()
        // crisp solid triangle
        val prev = g.stroke
        g.stroke = BasicStroke(1f)
        g.color = color
        g.fill(arrow)
        g.draw(arrow)
        g.stroke = prev
    }

    /** Open (non-filled) arrow head for MessageFlow endpoints. */
    private fun drawOpenArrowHead(g: Graphics2D, fx: Double, fy: Double, tx: Double, ty: Double, color: Color) {
        val angle = atan2(ty - fy, tx - fx)
        val len = 10.0
        val spread = Math.toRadians(20.0)
        val x1 = tx - len * cos(angle - spread)
        val y1 = ty - len * sin(angle - spread)
        val x2 = tx - len * cos(angle + spread)
        val y2 = ty - len * sin(angle + spread)
        val arrow = Path2D.Double()
        arrow.moveTo(tx, ty)
        arrow.lineTo(x1, y1)
        arrow.lineTo(x2, y2)
        arrow.closePath()
        val prev = g.stroke
        g.stroke = BasicStroke(1.2f)
        g.color = panelBackground
        g.fill(arrow)
        g.color = color
        g.draw(arrow)
        g.stroke = prev
    }

    /** Open circle marker drawn at the source end of a MessageFlow. */
    private fun drawOpenCircleMarker(g: Graphics2D, start: Pair<Double, Double>, next: Pair<Double, Double>, color: Color) {
        val radius = 4.0
        val angle = atan2(next.second - start.second, next.first - start.first)
        // Center the circle slightly along the edge direction from the start point
        val cx = start.first + radius * cos(angle)
        val cy = start.second + radius * sin(angle)
        val circle = Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2)
        val prev = g.stroke
        g.stroke = BasicStroke(1.2f)
        g.color = panelBackground
        g.fill(circle)
        g.color = color
        g.draw(circle)
        g.stroke = prev
    }

    /** Slash mark (tick) near the source of a DefaultFlow per BPMN 2.0 spec. */
    private fun drawDefaultFlowSlash(g: Graphics2D, start: Pair<Double, Double>, next: Pair<Double, Double>, color: Color, strokeWidth: Float) {
        val edgeAngle = atan2(next.second - start.second, next.first - start.first)
        // Position the slash ~16px from start along the edge
        val offset = 16.0
        val mx = start.first + offset * cos(edgeAngle)
        val my = start.second + offset * sin(edgeAngle)
        // Slash is perpendicular to the edge direction, rotated ~70 degrees for visual clarity
        val slashAngle = edgeAngle + Math.toRadians(70.0)
        val halfLen = 6.0
        val x1 = mx - halfLen * cos(slashAngle)
        val y1 = my - halfLen * sin(slashAngle)
        val x2 = mx + halfLen * cos(slashAngle)
        val y2 = my + halfLen * sin(slashAngle)
        val prev = g.stroke
        g.stroke = BasicStroke(strokeWidth.coerceAtLeast(1.5f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        g.color = color
        g.draw(Line2D.Double(x1, y1, x2, y2))
        g.stroke = prev
    }
}
