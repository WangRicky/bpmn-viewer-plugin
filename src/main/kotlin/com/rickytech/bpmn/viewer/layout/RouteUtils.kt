package com.rickytech.bpmn.viewer.layout

import com.rickytech.bpmn.viewer.model.BpmnNode
import kotlin.math.abs

/**
 * Shared orthogonal-routing collision-detection and obstacle-avoidance helpers.
 *
 * Used by [EdgeRouter] and [SugiyamaLayout] to keep generated polylines from
 * cutting through unrelated nodes. Logic is intentionally limited to orthogonal
 * (axis-aligned) segments.
 */

/** Safety gap between a detoured polyline and the bounding box of any obstacle. */
internal const val OBSTACLE_PADDING: Double = 15.0

/**
 * Tests whether the orthogonal segment p1-p2 (assumed horizontal or vertical)
 * crosses the interior of [node]. Endpoints touching the rectangle edge do not
 * count, so anchors flush with a node side stay valid.
 */
internal fun segmentIntersectsNode(
    p1: Pair<Double, Double>,
    p2: Pair<Double, Double>,
    node: BpmnNode
): Boolean {
    val pad = 2.0
    val minX = node.x + pad
    val minY = node.y + pad
    val maxX = node.x + node.width - pad
    val maxY = node.y + node.height - pad

    // Horizontal segment (constant y).
    if (abs(p1.second - p2.second) < 0.5) {
        val y = p1.second
        if (y <= minY || y >= maxY) return false
        val segMinX = minOf(p1.first, p2.first)
        val segMaxX = maxOf(p1.first, p2.first)
        return segMaxX > minX && segMinX < maxX
    }

    // Vertical segment (constant x).
    if (abs(p1.first - p2.first) < 0.5) {
        val x = p1.first
        if (x <= minX || x >= maxX) return false
        val segMinY = minOf(p1.second, p2.second)
        val segMaxY = maxOf(p1.second, p2.second)
        return segMaxY > minY && segMinY < maxY
    }

    return false
}

/**
 * Returns true when any segment of [route] crosses any obstacle in [obstacles].
 */
internal fun routeCollides(
    route: List<Pair<Double, Double>>,
    obstacles: List<BpmnNode>
): Boolean {
    if (obstacles.isEmpty() || route.size < 2) return false
    for (i in 0 until route.size - 1) {
        for (obs in obstacles) {
            if (segmentIntersectsNode(route[i], route[i + 1], obs)) {
                return true
            }
        }
    }
    return false
}

/**
 * Builds a 4-point orthogonal detour from [s] to [t] either by routing through
 * a fixed Y line ([isVerticalDetour] = true) or a fixed X column.
 *
 * Layout:
 *   - vertical detour:   s → (sX, detour) → (tX, detour) → t
 *   - horizontal detour: s → (detour, sY) → (detour, tY) → t
 */
internal fun buildDetourRoute(
    s: Pair<Double, Double>,
    t: Pair<Double, Double>,
    detourCoord: Double,
    isVerticalDetour: Boolean
): List<Pair<Double, Double>> {
    return if (isVerticalDetour) {
        val exitX = s.first
        val entryX = t.first
        listOf(s, exitX to detourCoord, entryX to detourCoord, t)
    } else {
        val exitY = s.second
        val entryY = t.second
        listOf(s, detourCoord to exitY, detourCoord to entryY, t)
    }
}

/**
 * Iteratively bends [standardRoute] around any node in [obstacles] it crosses.
 *
 * Instead of trying to bypass the entire obstacle cluster with one big detour
 * (which often hits unrelated nodes in dense layouts), this walks the route
 * segment by segment and inserts a minimal U-shaped notch around the *single*
 * obstacle currently in the way. The process repeats until the route is
 * collision-free or [maxIterations] is reached.
 *
 * The first/last points (anchor coordinates) are preserved verbatim. The final
 * polyline is fed through [simplifyRoute] to drop redundant collinear vertices.
 *
 * [obstacles] must already exclude the source/target nodes of the edge being
 * routed, otherwise the source/target rectangles themselves will trip the
 * intersection test.
 */
internal fun avoidObstacles(
    standardRoute: List<Pair<Double, Double>>,
    obstacles: List<BpmnNode>
): List<Pair<Double, Double>> {
    if (obstacles.isEmpty() || standardRoute.size < 2) return standardRoute
    if (!routeCollides(standardRoute, obstacles)) return standardRoute

    var route: List<Pair<Double, Double>> = standardRoute
    var bestRoute: List<Pair<Double, Double>> = standardRoute
    var bestCollisions = countCollisions(standardRoute, obstacles)
    val maxIterations = 30
    var iteration = 0

    while (iteration < maxIterations) {
        val collision = findFirstCollision(route, obstacles) ?: break
        val newRoute = detourAroundSegment(route, collision, obstacles) ?: break
        if (newRoute == route) break

        route = newRoute
        val currentCollisions = countCollisions(route, obstacles)
        if (currentCollisions < bestCollisions) {
            bestCollisions = currentCollisions
            bestRoute = route
        }
        if (currentCollisions == 0) {
            bestRoute = route
            break
        }
        iteration++
    }

    return simplifyRoute(bestRoute)
}

/** Identifies which segment of [route] is the first to cross which obstacle. */
private data class SegmentCollision(
    val segmentIndex: Int,
    val obstacle: BpmnNode
)

private fun findFirstCollision(
    route: List<Pair<Double, Double>>,
    obstacles: List<BpmnNode>
): SegmentCollision? {
    for (i in 0 until route.size - 1) {
        for (obs in obstacles) {
            if (segmentIntersectsNode(route[i], route[i + 1], obs)) {
                return SegmentCollision(i, obs)
            }
        }
    }
    return null
}

/**
 * Replaces the colliding segment in [route] with a U-shaped detour around the
 * obstacle. Tries both perpendicular sides (above/below for a horizontal
 * segment, left/right for a vertical one) and picks the first variant that
 * does not collide with any other obstacle. If both candidates still collide
 * the one with fewer collisions wins, breaking ties by point count.
 *
 * Returns null when the segment is neither horizontal nor vertical (we only
 * support orthogonal routing).
 */
private fun detourAroundSegment(
    route: List<Pair<Double, Double>>,
    collision: SegmentCollision,
    allObstacles: List<BpmnNode>
): List<Pair<Double, Double>>? {
    val i = collision.segmentIndex
    val p1 = route[i]
    val p2 = route[i + 1]
    val obs = collision.obstacle

    val obsLeft = obs.x - OBSTACLE_PADDING
    val obsRight = obs.x + obs.width + OBSTACLE_PADDING
    val obsTop = obs.y - OBSTACLE_PADDING
    val obsBottom = obs.y + obs.height + OBSTACLE_PADDING

    val isHorizontal = abs(p1.second - p2.second) < 0.5
    val isVertical = abs(p1.first - p2.first) < 0.5

    val candidates = mutableListOf<List<Pair<Double, Double>>>()
    val prefix = route.subList(0, i)
    val suffix = route.subList(i + 2, route.size)

    if (isHorizontal) {
        val y = p1.second
        val segMinX = minOf(p1.first, p2.first)
        val segMaxX = maxOf(p1.first, p2.first)
        val splitLeft = obsLeft.coerceIn(segMinX, segMaxX)
        val splitRight = obsRight.coerceIn(segMinX, segMaxX)

        // Detour above the obstacle.
        candidates += prefix + listOf(
            p1,
            splitLeft to y,
            splitLeft to obsTop,
            splitRight to obsTop,
            splitRight to y,
            p2
        ) + suffix

        // Detour below the obstacle.
        candidates += prefix + listOf(
            p1,
            splitLeft to y,
            splitLeft to obsBottom,
            splitRight to obsBottom,
            splitRight to y,
            p2
        ) + suffix
    } else if (isVertical) {
        val x = p1.first
        val segMinY = minOf(p1.second, p2.second)
        val segMaxY = maxOf(p1.second, p2.second)
        val splitTop = obsTop.coerceIn(segMinY, segMaxY)
        val splitBottom = obsBottom.coerceIn(segMinY, segMaxY)

        // Detour to the left.
        candidates += prefix + listOf(
            p1,
            x to splitTop,
            obsLeft to splitTop,
            obsLeft to splitBottom,
            x to splitBottom,
            p2
        ) + suffix

        // Detour to the right.
        candidates += prefix + listOf(
            p1,
            x to splitTop,
            obsRight to splitTop,
            obsRight to splitBottom,
            x to splitBottom,
            p2
        ) + suffix
    } else {
        return null
    }

    val nonColliding = candidates.filter { !routeCollides(it, allObstacles) }
    if (nonColliding.isNotEmpty()) {
        return nonColliding.minByOrNull { it.size }
    }

    return candidates.minByOrNull { countCollisions(it, allObstacles) * 100 + it.size }
}

/** Counts the number of (segment, obstacle) pairs that collide along [route]. */
private fun countCollisions(
    route: List<Pair<Double, Double>>,
    obstacles: List<BpmnNode>
): Int {
    if (route.size < 2) return 0
    var count = 0
    for (i in 0 until route.size - 1) {
        for (obs in obstacles) {
            if (segmentIntersectsNode(route[i], route[i + 1], obs)) count++
        }
    }
    return count
}

/**
 * Drops collinear interior vertices, e.g. three consecutive points sharing the
 * same X or Y collapse to two. The first and last points are always kept so
 * anchor positions stay intact.
 */
private fun simplifyRoute(route: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
    if (route.size <= 2) return route
    val result = mutableListOf(route.first())
    for (i in 1 until route.size - 1) {
        val prev = result.last()
        val curr = route[i]
        val next = route[i + 1]
        // Drop curr when prev/curr/next are collinear horizontally or vertically.
        val sameX = abs(prev.first - curr.first) < 0.5 && abs(curr.first - next.first) < 0.5
        val sameY = abs(prev.second - curr.second) < 0.5 && abs(curr.second - next.second) < 0.5
        // Also drop curr when it duplicates prev (zero-length segment).
        val duplicate = abs(prev.first - curr.first) < 0.5 && abs(prev.second - curr.second) < 0.5
        if (!sameX && !sameY && !duplicate) {
            result.add(curr)
        }
    }
    val last = route.last()
    val tail = result.last()
    if (abs(tail.first - last.first) < 0.5 && abs(tail.second - last.second) < 0.5) {
        // Replace duplicate tail to guarantee the actual anchor coordinates win.
        result[result.size - 1] = last
    } else {
        result.add(last)
    }
    return result
}
