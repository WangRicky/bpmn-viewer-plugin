package com.rickytech.bpmn.viewer.layout

import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import kotlin.math.abs

/**
 * Multi-anchor orthogonal edge router with angle-based anchor selection.
 *
 * Each node exposes 12 anchor points (3 per side). The exact target / source
 * anchor is selected by:
 *   1. Choosing the side based on the bearing between source / target centres
 *      (atan2 quadrants).
 *   2. Picking the sub-position (top/mid/bottom on left/right sides, or
 *      left/mid/right on top/bottom sides) according to the perpendicular
 *      offset between the two centres.
 *   3. Falling back to a still-free neighbouring anchor on the same side when
 *      the preferred one is already occupied, so multiple inbound / outbound
 *      edges spread out instead of overlapping.
 */
class EdgeRouter {

    companion object {
        /** Minimum standoff used when source/target overlap on an axis. */
        private const val MIN_BEND_GAP = 20.0
    }

    fun route(model: BpmnModel): BpmnModel {
        val nodesById = model.nodes.associateBy { it.id }
        val usedAnchors = HashMap<String, MutableSet<AnchorPosition>>()
        val routedEdges = model.edges.map { edge ->
            val s = nodesById[edge.sourceRef]
            val t = nodesById[edge.targetRef]
            val pts = if (s != null && t != null) routeEdge(s, t, usedAnchors, model.nodes) else emptyList()
            edge.copy(routePoints = pts)
        }
        // EdgeRouter must NEVER mutate node coordinates – it only computes
        // route points for edges. The original BPMN DI coordinates parsed by
        // BpmnParser are preserved verbatim.
        return BpmnModel(model.processId, model.processName, model.nodes, routedEdges, model.pools, model.lanes)
    }

    private fun routeEdge(
        source: BpmnNode,
        target: BpmnNode,
        usedAnchors: MutableMap<String, MutableSet<AnchorPosition>>,
        allNodes: List<BpmnNode>
    ): List<Pair<Double, Double>> {
        val srcAnchor = selectSourceAnchor(source, target, usedAnchors)
        val tgtAnchor = selectTargetAnchor(target, source, usedAnchors)

        val srcSide = anchorSide(srcAnchor)
        val tgtSide = anchorSide(tgtAnchor)
        val s = anchorPoint(source, srcAnchor)
        val t = anchorPoint(target, tgtAnchor)
        // Obstacles are every other node on the canvas. Container-style nodes
        // (pools, lanes, sub-processes, text annotations) are excluded so that
        // edges can legitimately enter their bounding boxes.
        val obstacles = allNodes.filter { n ->
            n.id != source.id && n.id != target.id && !isContainer(n)
        }
        return orthogonalRoute(s, t, srcSide, tgtSide, source, target, obstacles)
    }

    /**
     * Builds an orthogonal polyline between [s] and [t] given the chosen
     * anchor sides. Handles all 16 possible side combinations by selecting
     * the appropriate bend strategy.
     *
     * After the standard polyline is produced, [avoidObstacles] runs a
     * collision check against every other node and re-routes around any
     * unrelated node sitting on the path.
     */
    private fun orthogonalRoute(
        s: Pair<Double, Double>,
        t: Pair<Double, Double>,
        srcSide: Side,
        tgtSide: Side,
        source: BpmnNode,
        target: BpmnNode,
        obstacles: List<BpmnNode>
    ): List<Pair<Double, Double>> {
        val standard: List<Pair<Double, Double>> = when {
            // Opposing horizontal: source RIGHT → target LEFT
            srcSide == Side.RIGHT && tgtSide == Side.LEFT -> {
                if (abs(s.second - t.second) < 0.5) listOf(s, t)
                else {
                    val gapL = source.x + source.width
                    val gapR = target.x
                    val mid = if (gapR > gapL + 8.0)
                        ((gapL + gapR) / 2.0).coerceIn(gapL + 4.0, gapR - 4.0)
                    else
                        gapL + MIN_BEND_GAP
                    listOf(s, mid to s.second, mid to t.second, t)
                }
            }
            // Opposing horizontal: source LEFT → target RIGHT
            srcSide == Side.LEFT && tgtSide == Side.RIGHT -> {
                if (abs(s.second - t.second) < 0.5) listOf(s, t)
                else {
                    val gapL = target.x + target.width
                    val gapR = source.x
                    val mid = if (gapR > gapL + 8.0)
                        ((gapL + gapR) / 2.0).coerceIn(gapL + 4.0, gapR - 4.0)
                    else
                        gapR - MIN_BEND_GAP
                    listOf(s, mid to s.second, mid to t.second, t)
                }
            }
            // Opposing vertical: source BOTTOM → target TOP
            srcSide == Side.BOTTOM && tgtSide == Side.TOP -> {
                if (abs(s.first - t.first) < 0.5) listOf(s, t)
                else {
                    val gapT = source.y + source.height
                    val gapB = target.y
                    val mid = if (gapB > gapT + 8.0)
                        ((gapT + gapB) / 2.0).coerceIn(gapT + 4.0, gapB - 4.0)
                    else
                        gapT + MIN_BEND_GAP
                    listOf(s, s.first to mid, t.first to mid, t)
                }
            }
            // Opposing vertical: source TOP → target BOTTOM
            srcSide == Side.TOP && tgtSide == Side.BOTTOM -> {
                if (abs(s.first - t.first) < 0.5) listOf(s, t)
                else {
                    val gapT = target.y + target.height
                    val gapB = source.y
                    val mid = if (gapB > gapT + 8.0)
                        ((gapT + gapB) / 2.0).coerceIn(gapT + 4.0, gapB - 4.0)
                    else
                        gapB - MIN_BEND_GAP
                    listOf(s, s.first to mid, t.first to mid, t)
                }
            }
            // Same side: both RIGHT (U-shape going right)
            srcSide == Side.RIGHT && tgtSide == Side.RIGHT -> {
                val maxX = maxOf(source.x + source.width, target.x + target.width) + MIN_BEND_GAP
                listOf(s, maxX to s.second, maxX to t.second, t)
            }
            // Same side: both LEFT (U-shape going left)
            srcSide == Side.LEFT && tgtSide == Side.LEFT -> {
                val minX = minOf(source.x, target.x) - MIN_BEND_GAP
                listOf(s, minX to s.second, minX to t.second, t)
            }
            // Same side: both BOTTOM (U-shape going down)
            srcSide == Side.BOTTOM && tgtSide == Side.BOTTOM -> {
                val maxY = maxOf(source.y + source.height, target.y + target.height) + MIN_BEND_GAP
                listOf(s, s.first to maxY, t.first to maxY, t)
            }
            // Same side: both TOP (U-shape going up)
            srcSide == Side.TOP && tgtSide == Side.TOP -> {
                val minY = minOf(source.y, target.y) - MIN_BEND_GAP
                listOf(s, s.first to minY, t.first to minY, t)
            }
            // L-shape: source RIGHT → target TOP / BOTTOM
            // Promoted to 4-point Z-route: ensure the first segment leaves the
            // RIGHT side horizontally for at least MIN_BEND_GAP before bending,
            // so the line is visually perpendicular to the source edge.
            srcSide == Side.RIGHT && (tgtSide == Side.TOP || tgtSide == Side.BOTTOM) -> {
                val bendX = maxOf(s.first + MIN_BEND_GAP, t.first)
                listOf(s, bendX to s.second, bendX to t.second, t)
            }
            // L-shape: source LEFT → target TOP / BOTTOM
            srcSide == Side.LEFT && (tgtSide == Side.TOP || tgtSide == Side.BOTTOM) -> {
                val bendX = minOf(s.first - MIN_BEND_GAP, t.first)
                listOf(s, bendX to s.second, bendX to t.second, t)
            }
            // L-shape: source BOTTOM → target LEFT / RIGHT
            srcSide == Side.BOTTOM && (tgtSide == Side.LEFT || tgtSide == Side.RIGHT) -> {
                val bendY = maxOf(s.second + MIN_BEND_GAP, t.second)
                listOf(s, s.first to bendY, t.first to bendY, t)
            }
            // L-shape: source TOP → target LEFT / RIGHT
            srcSide == Side.TOP && (tgtSide == Side.LEFT || tgtSide == Side.RIGHT) -> {
                val bendY = minOf(s.second - MIN_BEND_GAP, t.second)
                listOf(s, s.first to bendY, t.first to bendY, t)
            }
            // Fallback: straight line
            else -> listOf(s, t)
        }
        return avoidObstacles(standard, obstacles)
    }
}
