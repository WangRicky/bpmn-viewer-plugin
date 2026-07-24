package com.rickytech.bpmn.viewer.ui.renderer

import java.awt.Color
import java.awt.FontMetrics

/**
 * 渲染层共享的颜色、字体与笔触常量。
 *
 * 所有子渲染器使用同一组颜色与笔触宽度，避免视觉风格漂移。
 */
internal object RenderConstants {
    // ── Surface ──────────────────────────────────────────────────────────
    val NODE_FILL: Color = Color.WHITE
    val NODE_BORDER: Color = Color(0x6B, 0x72, 0x80)        // slate-500
    val NODE_TEXT: Color = Color(0x1F, 0x29, 0x37)          // slate-900
    val NODE_SUBTEXT: Color = Color(0x6B, 0x72, 0x80)
    val ICON_COLOR: Color = Color(0x37, 0x41, 0x51)         // slate-700

    // ── Events ───────────────────────────────────────────────────────────
    val START_BORDER: Color = Color(0x52, 0xBD, 0x52)       // muted green
    val START_FILL: Color = Color(0xF1, 0xFA, 0xF1)         // pale green tint
    val END_BORDER: Color = Color(0xC5, 0x0C, 0x0C)         // deep red
    val END_FILL: Color = Color(0xFD, 0xF1, 0xF1)           // pale red tint

    // ── Selection accent ─────────────────────────────────────────────────
    val SELECTED_BORDER: Color = Color(0x1A, 0x73, 0xE8)
    val SELECTED_HALO: Color = Color(0x1A, 0x73, 0xE8)

    // ── Stroke widths ────────────────────────────────────────────────────
    const val TASK_BORDER_STROKE: Float = 1.4f
    const val CALL_ACTIVITY_STROKE: Float = 3.0f
    const val GATEWAY_STROKE: Float = 1.6f
    const val START_STROKE: Float = 1.5f
    const val END_STROKE: Float = 3.0f
}

/**
 * 将文本截断并附加省略号，使其在 [maxWidth] 像素内可见。
 */
internal fun clipText(text: String, fm: FontMetrics, maxWidth: Int): String {
    if (fm.stringWidth(text) <= maxWidth) return text
    var s = text
    while (s.isNotEmpty() && fm.stringWidth("$s…") > maxWidth) s = s.dropLast(1)
    return "$s…"
}
