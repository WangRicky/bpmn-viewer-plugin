package com.rickytech.bpmn.viewer.layout

import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import kotlin.math.abs

/**
 * Sugiyama-style hierarchical (left-to-right) layout.
 *
 * Steps:
 *  1. Build adjacency list from edges.
 *  2. Layer assignment using BFS from start events; back edges (creating cycles)
 *     are detected and not used to advance the layer counter.
 *  3. Within-layer ordering using barycenter heuristic (a few sweeps).
 *  4. Coordinate assignment: x = layer * LAYER_SPACING + PADDING,
 *                           y = positionInLayer * NODE_SPACING + PADDING.
 *  5. Edge routing: multi-anchor orthogonal routing that ignores any original
 *     waypoint information from the BPMN file.
 */
class SugiyamaLayout {
    companion object {
        private const val LAYER_SPACING = 250.0
        private const val NODE_SPACING = 100.0
        private const val PADDING = 50.0
        private const val BARYCENTER_SWEEPS = 12
        // Per-anchor offset step when several edges share the same anchor side.
        private const val ANCHOR_OFFSET_STEP = 8.0
    }

    private enum class Side { LEFT, RIGHT, TOP, BOTTOM }

    fun layout(model: BpmnModel): BpmnModel {
        if (model.nodes.isEmpty()) return model

        val nodesById = model.nodes.associateBy { it.id }

        // 1. adjacency
        val outAdj: Map<String, MutableList<String>> = model.nodes.associate { it.id to mutableListOf() }
        val inAdj: Map<String, MutableList<String>> = model.nodes.associate { it.id to mutableListOf() }
        for (e in model.edges) {
            if (nodesById.containsKey(e.sourceRef) && nodesById.containsKey(e.targetRef)) {
                outAdj[e.sourceRef]!!.add(e.targetRef)
                inAdj[e.targetRef]!!.add(e.sourceRef)
            }
        }

        // 2. layer assignment
        val layerOf = assignLayers(model, outAdj)

        // group by layer
        val layers: MutableMap<Int, MutableList<String>> = sortedMapOf()
        for ((id, layer) in layerOf) {
            layers.getOrPut(layer) { mutableListOf() }.add(id)
        }

        // 3. barycenter ordering
        orderWithinLayers(layers, inAdj, outAdj)

        // 4. coordinate assignment
        val laidOutNodes = mutableListOf<BpmnNode>()
        for ((layerIdx, ids) in layers) {
            ids.forEachIndexed { posInLayer, id ->
                val orig = nodesById.getValue(id)
                val (w, h) = sizeFor(orig.type)
                val n = orig.copy(width = w, height = h)
                n.x = PADDING + layerIdx * LAYER_SPACING
                n.y = PADDING + posInLayer * NODE_SPACING
                laidOutNodes += n
            }
        }
        val laidById = laidOutNodes.associateBy { it.id }

        // 5. edge routing — multi-anchor orthogonal router.
        // Track how many times each (node, side) anchor has been used so that
        // edges sharing an anchor get a small perpendicular offset and don't
        // visually collapse onto each other.
        val anchorUsage = HashMap<String, Int>()
        val laidOutEdges = model.edges.map { edge ->
            val s = laidById[edge.sourceRef]
            val t = laidById[edge.targetRef]
            val routed = if (s != null && t != null) {
                routeEdge(s, t, layerOf, anchorUsage, laidOutNodes)
            } else emptyList()
            edge.copy(routePoints = routed)
        }

        return BpmnModel(model.processId, model.processName, laidOutNodes, laidOutEdges, model.pools, model.lanes)
    }

    private fun sizeFor(type: BpmnNodeType): Pair<Double, Double> = when (type) {
        BpmnNodeType.START_EVENT, BpmnNodeType.END_EVENT -> 32.0 to 32.0
        BpmnNodeType.EXCLUSIVE_GATEWAY, BpmnNodeType.PARALLEL_GATEWAY -> 50.0 to 50.0
        else -> 170.0 to 70.0
    }

    private fun assignLayers(
        model: BpmnModel,
        outAdj: Map<String, List<String>>
    ): Map<String, Int> {
        val layer = HashMap<String, Int>(model.nodes.size)

        val starts = model.nodes.filter { it.type == BpmnNodeType.START_EVENT }.map { it.id }
        val roots: List<String> = if (starts.isNotEmpty()) starts else listOf(model.nodes.first().id)

        val visiting = HashSet<String>()
        val finished = HashSet<String>()

        fun dfs(node: String, depth: Int) {
            val current = layer[node]
            if (current == null || depth > current) layer[node] = depth
            if (!visiting.add(node)) return
            for (nxt in outAdj[node].orEmpty()) {
                if (nxt in visiting) continue // back edge -> ignore
                dfs(nxt, depth + 1)
            }
            visiting.remove(node)
            finished.add(node)
        }

        for (r in roots) dfs(r, 0)
        for (n in model.nodes) {
            if (n.id !in layer) {
                dfs(n.id, (layer.values.maxOrNull() ?: -1) + 1)
            }
        }
        return layer
    }

    private fun orderWithinLayers(
        layers: Map<Int, MutableList<String>>,
        inAdj: Map<String, List<String>>,
        outAdj: Map<String, List<String>>
    ) {
        val sortedLayerIdx = layers.keys.sorted()
        if (sortedLayerIdx.isEmpty()) return

        repeat(BARYCENTER_SWEEPS) { sweep ->
            val downward = sweep % 2 == 0
            val order = if (downward) sortedLayerIdx else sortedLayerIdx.reversed()
            for (li in order) {
                if (li == order.first()) continue
                val prevLayerIdx = if (downward) li - 1 else li + 1
                val prev = layers[prevLayerIdx] ?: continue
                val positions = HashMap<String, Int>()
                prev.forEachIndexed { i, id -> positions[id] = i }
                val current = layers[li] ?: continue
                val barycenters = current.map { id ->
                    val neighbors = if (downward) inAdj[id].orEmpty() else outAdj[id].orEmpty()
                    val ranks = neighbors.mapNotNull { positions[it] }
                    val bc = if (ranks.isEmpty()) Double.MAX_VALUE / 2.0 else ranks.average()
                    id to bc
                }
                val sorted = barycenters.sortedBy { it.second }.map { it.first }
                current.clear()
                current.addAll(sorted)
            }
        }
    }

    /**
     * Multi-anchor orthogonal router. Picks anchor sides based on the relative
     * positions of source vs. target, then emits a 4-point orthogonal polyline
     * (or a 5-point detour for back edges). The result is finally passed
     * through [avoidObstacles] so unrelated nodes lying on the path force a
     * detour rather than being crossed.
     */
    private fun routeEdge(
        source: BpmnNode,
        target: BpmnNode,
        layerOf: Map<String, Int>,
        anchorUsage: MutableMap<String, Int>,
        allNodes: List<BpmnNode>
    ): List<Pair<Double, Double>> {
        val srcLayer = layerOf[source.id] ?: 0
        val tgtLayer = layerOf[target.id] ?: 0

        val srcCy = source.y + source.height / 2.0
        val tgtCy = target.y + target.height / 2.0

        // Container-style nodes are excluded so legitimate edges into
        // pools / lanes / sub-processes don't trigger spurious detours.
        val obstacles = allNodes.filter { n ->
            n.id != source.id && n.id != target.id && !isContainer(n)
        }

        val standard = when {
            // Forward flow: target sits on a later layer (to the right).
            tgtLayer > srcLayer -> {
                val s = anchor(source, Side.RIGHT, anchorUsage)
                val t = anchor(target, Side.LEFT, anchorUsage)
                routeRightToLeft(s, t, source, target)
            }

            // Back edge: target sits on an earlier layer (to the left).
            // Drop down out of the source, run leftward beneath both nodes,
            // then climb up into the target. This produces the characteristic
            // looping path that bpmn.io draws for cycles.
            tgtLayer < srcLayer -> {
                val s = anchor(source, Side.BOTTOM, anchorUsage)
                val t = anchor(target, Side.BOTTOM, anchorUsage)
                routeBackEdge(s, t, source, target)
            }

            // Same-layer edge (vertically adjacent siblings).
            else -> {
                if (tgtCy >= srcCy) {
                    val s = anchor(source, Side.BOTTOM, anchorUsage)
                    val t = anchor(target, Side.TOP, anchorUsage)
                    routeTopBottom(s, t, source, target, downward = true)
                } else {
                    val s = anchor(source, Side.TOP, anchorUsage)
                    val t = anchor(target, Side.BOTTOM, anchorUsage)
                    routeTopBottom(s, t, source, target, downward = false)
                }
            }
        }
        return avoidObstacles(standard, obstacles)
    }

    private fun isContainer(n: BpmnNode): Boolean = when (n.type) {
        BpmnNodeType.POOL,
        BpmnNodeType.LANE,
        BpmnNodeType.SUB_PROCESS,
        BpmnNodeType.EVENT_SUB_PROCESS,
        BpmnNodeType.TEXT_ANNOTATION -> true
        else -> false
    }

    /**
     * Returns the next free position on the given side of [n]. The first edge
     * lands on the geometric center; subsequent edges are pushed alternately to
     * either side by [ANCHOR_OFFSET_STEP], capped to keep the anchor inside the
     * node's edge.
     */
    private fun anchor(
        n: BpmnNode,
        side: Side,
        usage: MutableMap<String, Int>
    ): Pair<Double, Double> {
        val key = "${n.id}:$side"
        val idx = usage.getOrDefault(key, 0)
        usage[key] = idx + 1

        val rank = (idx + 1) / 2
        val sign = if (idx == 0) 0 else if (idx % 2 == 1) 1 else -1
        val rawOffset = sign * rank * ANCHOR_OFFSET_STEP

        return when (side) {
            Side.LEFT -> {
                val maxOff = (n.height / 2.0) - 6.0
                val off = rawOffset.coerceIn(-maxOff, maxOff)
                n.x to (n.y + n.height / 2.0 + off)
            }
            Side.RIGHT -> {
                val maxOff = (n.height / 2.0) - 6.0
                val off = rawOffset.coerceIn(-maxOff, maxOff)
                (n.x + n.width) to (n.y + n.height / 2.0 + off)
            }
            Side.TOP -> {
                val maxOff = (n.width / 2.0) - 6.0
                val off = rawOffset.coerceIn(-maxOff, maxOff)
                (n.x + n.width / 2.0 + off) to n.y
            }
            Side.BOTTOM -> {
                val maxOff = (n.width / 2.0) - 6.0
                val off = rawOffset.coerceIn(-maxOff, maxOff)
                (n.x + n.width / 2.0 + off) to (n.y + n.height)
            }
        }
    }

    /**
     * Right-anchor → Left-anchor orthogonal route.
     *
     * Special-case the trivial "y already aligned" branch into a single straight
     * segment to keep the picture tidy.
     */
    private fun routeRightToLeft(
        s: Pair<Double, Double>,
        t: Pair<Double, Double>,
        source: BpmnNode,
        target: BpmnNode
    ): List<Pair<Double, Double>> {
        if (abs(s.second - t.second) < 0.5) {
            return listOf(s, t)
        }
        // Bend at the horizontal midpoint of the gap between the two nodes;
        // clamp so the bend column does not penetrate either node.
        val gapL = source.x + source.width
        val gapR = target.x
        val mid = ((gapL + gapR) / 2.0).coerceIn(gapL + 8.0, gapR - 8.0)
        return listOf(
            s,
            mid to s.second,
            mid to t.second,
            t
        )
    }

    /**
     * Back edge: source → target where target is upstream. Both anchors sit on
     * the bottom side of their nodes. The line drops down, sweeps under both
     * nodes, then climbs up into the target.
     */
    private fun routeBackEdge(
        s: Pair<Double, Double>,
        t: Pair<Double, Double>,
        source: BpmnNode,
        target: BpmnNode
    ): List<Pair<Double, Double>> {
        val belowSrc = source.y + source.height
        val belowTgt = target.y + target.height
        val below = maxOf(belowSrc, belowTgt) + NODE_SPACING * 0.6
        return listOf(
            s,
            s.first to below,
            t.first to below,
            t
        )
    }

    /**
     * Same-layer route: vertically adjacent neighbours. Bend on a column just
     * outside the source/target right edge.
     */
    private fun routeTopBottom(
        s: Pair<Double, Double>,
        t: Pair<Double, Double>,
        source: BpmnNode,
        target: BpmnNode,
        @Suppress("UNUSED_PARAMETER") downward: Boolean
    ): List<Pair<Double, Double>> {
        if (abs(s.first - t.first) < 0.5) {
            return listOf(s, t)
        }
        val midY = (s.second + t.second) / 2.0
        val nodesBottom = maxOf(source.y + source.height, target.y + target.height)
        val nodesTop = minOf(source.y, target.y)
        val safeMidY = midY.coerceIn(nodesTop + 6.0, nodesBottom + NODE_SPACING * 0.6)
        return listOf(
            s,
            s.first to safeMidY,
            t.first to safeMidY,
            t
        )
    }
}
