package com.rickytech.bpmn.viewer.layout

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import kotlin.math.atan2

internal enum class Side { LEFT, RIGHT, TOP, BOTTOM }

enum class AnchorPosition {
    LEFT_TOP, LEFT_MID, LEFT_BOTTOM,
    RIGHT_TOP, RIGHT_MID, RIGHT_BOTTOM,
    TOP_LEFT, TOP_MID, TOP_RIGHT,
    BOTTOM_LEFT, BOTTOM_MID, BOTTOM_RIGHT
}

/**
 * Selects the side based on the bearing of (dx, dy). Divides the full
 * circle into four 90-degree quadrants:
 *   - RIGHT  : -45° to +45°
 *   - BOTTOM : +45° to +135°
 *   - TOP    : -135° to -45°
 *   - LEFT   : +135° to -135° (wraps around ±180°)
 */
internal fun selectSide(dx: Double, dy: Double): Side {
    val angle = atan2(dy, dx) // -PI to PI
    return when {
        angle >= -Math.PI / 4 && angle < Math.PI / 4 -> Side.RIGHT
        angle >= Math.PI / 4 && angle < 3 * Math.PI / 4 -> Side.BOTTOM
        angle >= -3 * Math.PI / 4 && angle < -Math.PI / 4 -> Side.TOP
        else -> Side.LEFT
    }
}

/**
 * Given a chosen [side] and the perpendicular offset of the *other* node
 * relative to this node's centre, returns the preferred 1-of-3 anchor
 * sub-position on that side.
 *
 * For LEFT/RIGHT sides the [dy] offset (other node above / below) splits
 * into TOP / MID / BOTTOM thirds. For TOP/BOTTOM sides the [dx] offset
 * splits into LEFT / MID / RIGHT thirds.
 */
internal fun sideSubAnchor(
    side: Side,
    width: Double,
    height: Double,
    dx: Double,
    dy: Double
): AnchorPosition {
    val threshold = 0.25
    return when (side) {
        Side.LEFT -> {
            val r = if (height > 0) dy / height else 0.0
            when {
                r < -threshold -> AnchorPosition.LEFT_TOP
                r > threshold -> AnchorPosition.LEFT_BOTTOM
                else -> AnchorPosition.LEFT_MID
            }
        }
        Side.RIGHT -> {
            val r = if (height > 0) dy / height else 0.0
            when {
                r < -threshold -> AnchorPosition.RIGHT_TOP
                r > threshold -> AnchorPosition.RIGHT_BOTTOM
                else -> AnchorPosition.RIGHT_MID
            }
        }
        Side.TOP -> {
            val r = if (width > 0) dx / width else 0.0
            when {
                r < -threshold -> AnchorPosition.TOP_LEFT
                r > threshold -> AnchorPosition.TOP_RIGHT
                else -> AnchorPosition.TOP_MID
            }
        }
        Side.BOTTOM -> {
            val r = if (width > 0) dx / width else 0.0
            when {
                r < -threshold -> AnchorPosition.BOTTOM_LEFT
                r > threshold -> AnchorPosition.BOTTOM_RIGHT
                else -> AnchorPosition.BOTTOM_MID
            }
        }
    }
}

/**
 * If [primary] is already occupied, returns the closest unoccupied
 * neighbour on the same side. Falls back to [primary] if every anchor on
 * that side is taken (rare).
 */
internal fun pickFreeAnchor(primary: AnchorPosition, used: Set<AnchorPosition>): AnchorPosition {
    if (primary !in used) return primary
    for (n in neighborsOf(primary)) if (n !in used) return n
    return primary
}

internal fun neighborsOf(a: AnchorPosition): List<AnchorPosition> = when (a) {
    AnchorPosition.LEFT_TOP -> listOf(AnchorPosition.LEFT_MID, AnchorPosition.LEFT_BOTTOM)
    AnchorPosition.LEFT_MID -> listOf(AnchorPosition.LEFT_TOP, AnchorPosition.LEFT_BOTTOM)
    AnchorPosition.LEFT_BOTTOM -> listOf(AnchorPosition.LEFT_MID, AnchorPosition.LEFT_TOP)
    AnchorPosition.RIGHT_TOP -> listOf(AnchorPosition.RIGHT_MID, AnchorPosition.RIGHT_BOTTOM)
    AnchorPosition.RIGHT_MID -> listOf(AnchorPosition.RIGHT_TOP, AnchorPosition.RIGHT_BOTTOM)
    AnchorPosition.RIGHT_BOTTOM -> listOf(AnchorPosition.RIGHT_MID, AnchorPosition.RIGHT_TOP)
    AnchorPosition.TOP_LEFT -> listOf(AnchorPosition.TOP_MID, AnchorPosition.TOP_RIGHT)
    AnchorPosition.TOP_MID -> listOf(AnchorPosition.TOP_LEFT, AnchorPosition.TOP_RIGHT)
    AnchorPosition.TOP_RIGHT -> listOf(AnchorPosition.TOP_MID, AnchorPosition.TOP_LEFT)
    AnchorPosition.BOTTOM_LEFT -> listOf(AnchorPosition.BOTTOM_MID, AnchorPosition.BOTTOM_RIGHT)
    AnchorPosition.BOTTOM_MID -> listOf(AnchorPosition.BOTTOM_LEFT, AnchorPosition.BOTTOM_RIGHT)
    AnchorPosition.BOTTOM_RIGHT -> listOf(AnchorPosition.BOTTOM_MID, AnchorPosition.BOTTOM_LEFT)
}

internal fun anchorSide(a: AnchorPosition): Side = when (a) {
    AnchorPosition.LEFT_TOP, AnchorPosition.LEFT_MID, AnchorPosition.LEFT_BOTTOM -> Side.LEFT
    AnchorPosition.RIGHT_TOP, AnchorPosition.RIGHT_MID, AnchorPosition.RIGHT_BOTTOM -> Side.RIGHT
    AnchorPosition.TOP_LEFT, AnchorPosition.TOP_MID, AnchorPosition.TOP_RIGHT -> Side.TOP
    AnchorPosition.BOTTOM_LEFT, AnchorPosition.BOTTOM_MID, AnchorPosition.BOTTOM_RIGHT -> Side.BOTTOM
}

internal fun anchorPoint(n: BpmnNode, a: AnchorPosition): Pair<Double, Double> = when (a) {
    AnchorPosition.LEFT_TOP -> n.x to (n.y + n.height * 0.25)
    AnchorPosition.LEFT_MID -> n.x to (n.y + n.height * 0.50)
    AnchorPosition.LEFT_BOTTOM -> n.x to (n.y + n.height * 0.75)
    AnchorPosition.RIGHT_TOP -> (n.x + n.width) to (n.y + n.height * 0.25)
    AnchorPosition.RIGHT_MID -> (n.x + n.width) to (n.y + n.height * 0.50)
    AnchorPosition.RIGHT_BOTTOM -> (n.x + n.width) to (n.y + n.height * 0.75)
    AnchorPosition.TOP_LEFT -> (n.x + n.width * 0.25) to n.y
    AnchorPosition.TOP_MID -> (n.x + n.width * 0.50) to n.y
    AnchorPosition.TOP_RIGHT -> (n.x + n.width * 0.75) to n.y
    AnchorPosition.BOTTOM_LEFT -> (n.x + n.width * 0.25) to (n.y + n.height)
    AnchorPosition.BOTTOM_MID -> (n.x + n.width * 0.50) to (n.y + n.height)
    AnchorPosition.BOTTOM_RIGHT -> (n.x + n.width * 0.75) to (n.y + n.height)
}

/**
 * Picks the source anchor: the side faces the target, the sub-position
 * leans toward the half of the side closest to the target.
 */
internal fun selectSourceAnchor(
    source: BpmnNode,
    target: BpmnNode,
    usedAnchors: MutableMap<String, MutableSet<AnchorPosition>>
): AnchorPosition {
    val sCx = source.x + source.width / 2.0
    val sCy = source.y + source.height / 2.0
    val tCx = target.x + target.width / 2.0
    val tCy = target.y + target.height / 2.0
    // Direction from source toward target.
    val dx = tCx - sCx
    val dy = tCy - sCy
    val side = selectSide(dx, dy)

    val primary = sideSubAnchor(side, source.width, source.height, dx, dy)
    val used = usedAnchors.getOrPut(source.id) { mutableSetOf() }
    val chosen = pickFreeAnchor(primary, used)
    used.add(chosen)
    return chosen
}

/**
 * Picks the target anchor: the side faces the source, the sub-position
 * leans toward the half of the side closest to the source.
 */
internal fun selectTargetAnchor(
    target: BpmnNode,
    source: BpmnNode,
    usedAnchors: MutableMap<String, MutableSet<AnchorPosition>>
): AnchorPosition {
    val sCx = source.x + source.width / 2.0
    val sCy = source.y + source.height / 2.0
    val tCx = target.x + target.width / 2.0
    val tCy = target.y + target.height / 2.0
    // Direction from target toward source (where the inbound edge comes from).
    val dx = sCx - tCx
    val dy = sCy - tCy
    val side = selectSide(dx, dy)

    val primary = sideSubAnchor(side, target.width, target.height, dx, dy)
    val used = usedAnchors.getOrPut(target.id) { mutableSetOf() }
    val chosen = pickFreeAnchor(primary, used)
    used.add(chosen)
    return chosen
}

internal fun isContainer(n: BpmnNode): Boolean = when (n.type) {
    BpmnNodeType.POOL,
    BpmnNodeType.LANE,
    BpmnNodeType.SUB_PROCESS,
    BpmnNodeType.EVENT_SUB_PROCESS,
    BpmnNodeType.TEXT_ANNOTATION -> true
    else -> false
}
