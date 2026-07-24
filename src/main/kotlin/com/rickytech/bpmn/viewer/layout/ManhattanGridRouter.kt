package com.rickytech.bpmn.viewer.layout

import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Grid-based orthogonal router using A* search.
 *
 * The canvas is discretised into a uniform grid; every non-container node
 * inflates its bounding box by [NODE_PADDING] and marks the corresponding
 * cells as blocked. For every edge an A* search runs from the source anchor
 * cell to the target anchor cell with a Manhattan heuristic and a turn
 * penalty so the result has as few bends as possible.
 *
 * The first move out of the source cell is constrained to be perpendicular
 * to the chosen anchor side, and the final move into the target cell is
 * forced by selecting only neighbours that arrive on the correct axis. This
 * guarantees a 90-degree exit / entry angle on both ends of the edge.
 */
class ManhattanGridRouter {

    companion object {
        /** Grid cell size in world coordinates. */
        const val GRID_STEP = 10.0

        /** Inflation around blocking nodes so edges keep a visual margin. */
        const val NODE_PADDING = 12.0

        /** Extra cost charged when the path direction changes. */
        const val TURN_PENALTY = 2

        /** Outer slack added around the canvas bounding box. */
        const val CANVAS_MARGIN = 50.0

        /** Maximum number of expanded A* nodes before giving up. */
        private const val MAX_EXPANSIONS = 50_000

        /** Direction codes used for turn detection. */
        private const val DIR_NONE = 0
        private const val DIR_UP = 1
        private const val DIR_DOWN = 2
        private const val DIR_LEFT = 3
        private const val DIR_RIGHT = 4
    }

    fun route(model: BpmnModel): BpmnModel {
        if (model.nodes.isEmpty()) {
            // Without any node we cannot build a grid; clear all routes.
            return BpmnModel(
                model.processId, model.processName, model.nodes,
                model.edges.map { it.copy(routePoints = emptyList()) },
                model.pools, model.lanes
            )
        }
        val nodesById = model.nodes.associateBy { it.id }
        val grid = buildGrid(model)

        // Sort edges by source/target centre distance so short edges are
        // routed first and reserve the most direct corridors.
        val ordered = model.edges
            .mapNotNull { e ->
                val s = nodesById[e.sourceRef] ?: return@mapNotNull null
                val t = nodesById[e.targetRef] ?: return@mapNotNull null
                Triple(e, s, t)
            }
            .sortedBy { (_, s, t) -> centreDistance(s, t) }

        val usedAnchors = HashMap<String, MutableSet<AnchorPosition>>()
        val routedById = HashMap<String, List<Pair<Double, Double>>>()
        for ((edge, s, t) in ordered) {
            routedById[edge.id] = routeEdge(s, t, usedAnchors, grid)
        }

        val routedEdges = model.edges.map { e ->
            val pts = routedById[e.id] ?: emptyList()
            e.copy(routePoints = pts)
        }
        return BpmnModel(
            model.processId, model.processName, model.nodes, routedEdges,
            model.pools, model.lanes
        )
    }

    // -----------------------------------------------------------------
    // Grid construction
    // -----------------------------------------------------------------

    private class Grid(
        val originX: Double,
        val originY: Double,
        val cols: Int,
        val rows: Int,
        val step: Double,
        val blocked: BooleanArray
    ) {
        fun isBlocked(col: Int, row: Int): Boolean =
            col < 0 || row < 0 || col >= cols || row >= rows ||
                blocked[row * cols + col]

        fun setBlocked(col: Int, row: Int, value: Boolean) {
            if (col in 0 until cols && row in 0 until rows) {
                blocked[row * cols + col] = value
            }
        }

        fun toWorld(col: Int, row: Int): Pair<Double, Double> =
            (originX + col * step) to (originY + row * step)

        fun toGrid(worldX: Double, worldY: Double): Pair<Int, Int> {
            val c = ((worldX - originX) / step).toInt().coerceIn(0, cols - 1)
            val r = ((worldY - originY) / step).toInt().coerceIn(0, rows - 1)
            return c to r
        }

        fun key(col: Int, row: Int): Int = row * cols + col
    }

    private fun buildGrid(model: BpmnModel): Grid {
        // Collect every node that contributes to the canvas bounds. Pools
        // and lanes are included for sizing but never blocked.
        val all = ArrayList<BpmnNode>(model.nodes.size + model.pools.size + model.lanes.size)
        all.addAll(model.nodes)
        all.addAll(model.pools)
        all.addAll(model.lanes)

        var minX = Double.POSITIVE_INFINITY
        var minY = Double.POSITIVE_INFINITY
        var maxX = Double.NEGATIVE_INFINITY
        var maxY = Double.NEGATIVE_INFINITY
        for (n in all) {
            if (n.x < minX) minX = n.x
            if (n.y < minY) minY = n.y
            if (n.x + n.width > maxX) maxX = n.x + n.width
            if (n.y + n.height > maxY) maxY = n.y + n.height
        }
        if (!minX.isFinite()) {
            minX = 0.0; minY = 0.0; maxX = 200.0; maxY = 200.0
        }
        val originX = minX - CANVAS_MARGIN
        val originY = minY - CANVAS_MARGIN
        val width = (maxX + CANVAS_MARGIN) - originX
        val height = (maxY + CANVAS_MARGIN) - originY
        val cols = ceil(width / GRID_STEP).toInt().coerceAtLeast(1) + 1
        val rows = ceil(height / GRID_STEP).toInt().coerceAtLeast(1) + 1
        val grid = Grid(originX, originY, cols, rows, GRID_STEP, BooleanArray(cols * rows))

        // Mark every non-container node, inflated by NODE_PADDING.
        for (n in model.nodes) {
            if (isContainer(n)) continue
            val (c0, r0) = grid.toGrid(n.x - NODE_PADDING, n.y - NODE_PADDING)
            val (c1, r1) = grid.toGrid(n.x + n.width + NODE_PADDING, n.y + n.height + NODE_PADDING)
            for (r in r0..r1) {
                for (c in c0..c1) grid.setBlocked(c, r, true)
            }
        }
        return grid
    }

    private fun centreDistance(s: BpmnNode, t: BpmnNode): Double {
        val dx = (s.x + s.width / 2.0) - (t.x + t.width / 2.0)
        val dy = (s.y + s.height / 2.0) - (t.y + t.height / 2.0)
        return abs(dx) + abs(dy)
    }

    // -----------------------------------------------------------------
    // Per-edge routing
    // -----------------------------------------------------------------

    private fun routeEdge(
        source: BpmnNode,
        target: BpmnNode,
        usedAnchors: MutableMap<String, MutableSet<AnchorPosition>>,
        grid: Grid
    ): List<Pair<Double, Double>> {
        val srcAnchor = selectSourceAnchor(source, target, usedAnchors)
        val tgtAnchor = selectTargetAnchor(target, source, usedAnchors)
        val (sx, sy) = anchorPoint(source, srcAnchor)
        val (tx, ty) = anchorPoint(target, tgtAnchor)
        val srcSide = anchorSide(srcAnchor)
        val tgtSide = anchorSide(tgtAnchor)

        val (sCol, sRow) = grid.toGrid(sx, sy)
        val (gCol, gRow) = grid.toGrid(tx, ty)

        // Anchors land on the inflated boundary; temporarily clear the
        // start / goal cells so A* can leave / enter them.
        val sBlocked = grid.isBlocked(sCol, sRow)
        val gBlocked = grid.isBlocked(gCol, gRow)
        grid.setBlocked(sCol, sRow, false)
        grid.setBlocked(gCol, gRow, false)
        // Free a one-step launch cell ahead of the anchor too so the very
        // first move (which is forced perpendicular) is never blocked by
        // the inflated padding around our own node.
        val launch = launchCell(sCol, sRow, srcSide)
        val landing = launchCell(gCol, gRow, tgtSide)
        val launchWasBlocked = launch?.let { grid.isBlocked(it.first, it.second) } ?: false
        val landingWasBlocked = landing?.let { grid.isBlocked(it.first, it.second) } ?: false
        launch?.let { grid.setBlocked(it.first, it.second, false) }
        landing?.let { grid.setBlocked(it.first, it.second, false) }

        val path = aStarSearch(grid, sCol, sRow, gCol, gRow, srcSide, tgtSide)

        // Restore the grid for subsequent edges.
        grid.setBlocked(sCol, sRow, sBlocked)
        grid.setBlocked(gCol, gRow, gBlocked)
        launch?.let { grid.setBlocked(it.first, it.second, launchWasBlocked) }
        landing?.let { grid.setBlocked(it.first, it.second, landingWasBlocked) }

        return if (path != null && path.isNotEmpty()) {
            gridPathToPolyline(path, grid, sx to sy, tx to ty)
        } else {
            // Pathfinding failed: fall back to a straight segment so the
            // edge is still drawable.
            listOf(sx to sy, tx to ty)
        }
    }

    private fun launchCell(col: Int, row: Int, side: Side): Pair<Int, Int>? = when (side) {
        Side.RIGHT -> (col + 1) to row
        Side.LEFT -> (col - 1) to row
        Side.TOP -> col to (row - 1)
        Side.BOTTOM -> col to (row + 1)
    }

    // -----------------------------------------------------------------
    // A* search
    // -----------------------------------------------------------------

    private data class AStarNode(
        val col: Int,
        val row: Int,
        val g: Int,
        val f: Int,
        val parentCol: Int,
        val parentRow: Int,
        val direction: Int
    )

    private fun aStarSearch(
        grid: Grid,
        startCol: Int,
        startRow: Int,
        goalCol: Int,
        goalRow: Int,
        srcSide: Side,
        tgtSide: Side
    ): List<Pair<Int, Int>>? {
        if (startCol == goalCol && startRow == goalRow) {
            return listOf(startCol to startRow)
        }
        val initialDir = sideDirection(srcSide)
        val open = PriorityQueue<AStarNode>(compareBy { it.f })
        // Best known g-cost per cell+direction, so we don't over-prune
        // cells that need to be revisited from a different heading.
        val bestG = HashMap<Long, Int>()
        // Parent tracking keyed by full state (cell + direction) so that
        // multiple visits to the same cell from different headings keep
        // their own predecessors. Value = (parentCol, parentRow, parentStateKey).
        val parents = HashMap<Long, Triple<Int, Int, Long>>()
        val closed = HashSet<Long>()

        val startKey = stateKey(grid, startCol, startRow, initialDir)
        val startH = heuristic(startCol, startRow, goalCol, goalRow)
        open.add(AStarNode(startCol, startRow, 0, startH, -1, -1, initialDir))
        bestG[startKey] = 0

        var expansions = 0
        // The last move must enter the goal *from the outside* of the
        // target, i.e. travel in the direction opposite to the side
        // (e.g. a RIGHT-side anchor is reached by a leftward move).
        val requiredFinalDir = opposite(sideDirection(tgtSide))

        while (open.isNotEmpty()) {
            if (++expansions > MAX_EXPANSIONS) return null
            val cur = open.poll()
            val curKey = stateKey(grid, cur.col, cur.row, cur.direction)
            if (!closed.add(curKey)) continue

            // Goal check: also enforce the final approach direction so the
            // last segment is perpendicular to the target side.
            if (cur.col == goalCol && cur.row == goalRow) {
                if (cur.direction == DIR_NONE || cur.direction == requiredFinalDir) {
                    return reconstructPath(parents, curKey, cur.col, cur.row)
                }
                continue
            }

            for (move in 1..4) {
                // Initial step is locked to the perpendicular direction.
                if (cur.parentCol == -1 && initialDir != DIR_NONE && move != initialDir) continue
                // Forbid immediate back-tracking.
                if (cur.direction != DIR_NONE && move == opposite(cur.direction)) continue

                val (nc, nr) = step(cur.col, cur.row, move)
                if (grid.isBlocked(nc, nr)) continue
                // The goal cell is only acceptable if entered along the
                // required final heading.
                val isGoal = nc == goalCol && nr == goalRow
                if (isGoal && move != requiredFinalDir) continue

                val turn = cur.direction != DIR_NONE && move != cur.direction
                val ng = cur.g + 1 + if (turn) TURN_PENALTY else 0
                val nKey = stateKey(grid, nc, nr, move)
                val prev = bestG[nKey]
                if (prev != null && prev <= ng) continue
                bestG[nKey] = ng

                val nf = ng + heuristic(nc, nr, goalCol, goalRow)
                parents[nKey] = Triple(cur.col, cur.row, curKey)
                open.add(AStarNode(nc, nr, ng, nf, cur.col, cur.row, move))
            }
        }
        return null
    }

    private fun reconstructPath(
        parents: Map<Long, Triple<Int, Int, Long>>,
        endStateKey: Long,
        endCol: Int,
        endRow: Int
    ): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        out.add(endCol to endRow)
        var key = endStateKey
        while (true) {
            val p = parents[key] ?: break
            out.add(p.first to p.second)
            key = p.third
        }
        out.reverse()
        return out
    }

    private fun heuristic(col: Int, row: Int, goalCol: Int, goalRow: Int): Int =
        abs(goalCol - col) + abs(goalRow - row)

    private fun stateKey(grid: Grid, col: Int, row: Int, dir: Int): Long =
        (row.toLong() * grid.cols + col) * 5L + dir

    private fun step(col: Int, row: Int, dir: Int): Pair<Int, Int> = when (dir) {
        DIR_UP -> col to (row - 1)
        DIR_DOWN -> col to (row + 1)
        DIR_LEFT -> (col - 1) to row
        DIR_RIGHT -> (col + 1) to row
        else -> col to row
    }

    private fun opposite(dir: Int): Int = when (dir) {
        DIR_UP -> DIR_DOWN
        DIR_DOWN -> DIR_UP
        DIR_LEFT -> DIR_RIGHT
        DIR_RIGHT -> DIR_LEFT
        else -> DIR_NONE
    }

    private fun sideDirection(side: Side): Int = when (side) {
        Side.RIGHT -> DIR_RIGHT
        Side.LEFT -> DIR_LEFT
        Side.TOP -> DIR_UP
        Side.BOTTOM -> DIR_DOWN
    }

    // -----------------------------------------------------------------
    // Polyline post-processing
    // -----------------------------------------------------------------

    /**
     * Compresses the per-cell A* path into a polyline by keeping only the
     * cells where the direction changes. The first and last points are
     * snapped to the precise anchor world coordinates so the polyline meets
     * the node geometry exactly.
     */
    private fun gridPathToPolyline(
        path: List<Pair<Int, Int>>,
        grid: Grid,
        sourceAnchor: Pair<Double, Double>,
        targetAnchor: Pair<Double, Double>
    ): List<Pair<Double, Double>> {
        if (path.size <= 1) return listOf(sourceAnchor, targetAnchor)
        val bends = ArrayList<Pair<Int, Int>>()
        bends.add(path.first())
        for (i in 1 until path.size - 1) {
            val (pc, pr) = path[i - 1]
            val (cc, cr) = path[i]
            val (nc, nr) = path[i + 1]
            val d1c = cc - pc; val d1r = cr - pr
            val d2c = nc - cc; val d2r = nr - cr
            if (d1c != d2c || d1r != d2r) bends.add(cc to cr)
        }
        bends.add(path.last())

        val world = bends.map { (c, r) -> grid.toWorld(c, r) }.toMutableList()
        // Snap endpoints to the exact anchor coordinates.
        world[0] = sourceAnchor
        world[world.lastIndex] = targetAnchor

        // After snapping, the segment connecting the original first cell
        // to the snapped anchor may no longer be axis-aligned. Insert a
        // short orthogonal jog when this happens.
        return enforceOrthogonality(world)
    }

    /**
     * Walks the polyline and, whenever two consecutive points are not
     * axis-aligned, inserts an extra knee point so the segment stays
     * orthogonal. This typically only fires at the snapped endpoints.
     */
    private fun enforceOrthogonality(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pts.size < 2) return pts
        val out = ArrayList<Pair<Double, Double>>(pts.size + 2)
        out.add(pts[0])
        for (i in 1 until pts.size) {
            val prev = out.last()
            val cur = pts[i]
            val dx = abs(cur.first - prev.first)
            val dy = abs(cur.second - prev.second)
            if (dx > 0.5 && dy > 0.5) {
                // Choose the bend direction that preserves the heading
                // of the previous segment when possible.
                val knee = if (out.size >= 2) {
                    val pp = out[out.lastIndex - 1]
                    val horizPrev = abs(prev.second - pp.second) < 0.5
                    if (horizPrev) cur.first to prev.second
                    else prev.first to cur.second
                } else {
                    prev.first to cur.second
                }
                out.add(knee)
            }
            out.add(cur)
        }
        return collapseCollinear(out)
    }

    /** Removes consecutive duplicates and merges 3 collinear points. */
    private fun collapseCollinear(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (pts.size <= 2) return pts
        val out = ArrayList<Pair<Double, Double>>(pts.size)
        for (p in pts) {
            if (out.isNotEmpty()) {
                val last = out.last()
                if (abs(p.first - last.first) < 0.5 && abs(p.second - last.second) < 0.5) continue
            }
            if (out.size >= 2) {
                val a = out[out.size - 2]
                val b = out[out.size - 1]
                val collinearH = abs(a.second - b.second) < 0.5 && abs(b.second - p.second) < 0.5
                val collinearV = abs(a.first - b.first) < 0.5 && abs(b.first - p.first) < 0.5
                if (collinearH || collinearV) {
                    out.removeAt(out.size - 1)
                }
            }
            out.add(p)
        }
        return out
    }

}
