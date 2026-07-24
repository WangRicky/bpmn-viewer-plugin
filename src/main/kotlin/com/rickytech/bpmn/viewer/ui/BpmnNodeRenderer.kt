package com.rickytech.bpmn.viewer.ui

import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.ui.renderer.ContainerRenderer
import com.rickytech.bpmn.viewer.ui.renderer.EventIcon
import com.rickytech.bpmn.viewer.ui.renderer.EventRenderer
import com.rickytech.bpmn.viewer.ui.renderer.GatewayMarker
import com.rickytech.bpmn.viewer.ui.renderer.GatewayRenderer
import com.rickytech.bpmn.viewer.ui.renderer.NodeIcon
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.CALL_ACTIVITY_STROKE
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.SELECTED_HALO
import com.rickytech.bpmn.viewer.ui.renderer.RenderConstants.TASK_BORDER_STROKE
import com.rickytech.bpmn.viewer.ui.renderer.TaskRenderer
import java.awt.AlphaComposite
import java.awt.Graphics2D
import java.awt.geom.RoundRectangle2D

/**
 * 节点渲染门面：根据 [BpmnNodeType] 将绘制请求委派到对应的子渲染器。
 *
 * 仅保留：
 * 1. 选中态光晕（对所有节点类型通用）；
 * 2. 类型 → 子渲染器 + 图标 的分发表。
 *
 * 具体的几何/图标绘制实现位于 `ui.renderer` 包下的各 *Renderer 类中。
 */
class BpmnNodeRenderer {

    private val taskRenderer = TaskRenderer()
    private val eventRenderer = EventRenderer()
    private val gatewayRenderer = GatewayRenderer()
    private val containerRenderer = ContainerRenderer()

    internal fun drawNode(g: Graphics2D, n: BpmnNode, isSelected: Boolean) {
        if (isSelected) {
            // soft halo behind the selected node — universal across all node types
            val prevComp = g.composite
            g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.18f)
            g.color = SELECTED_HALO
            g.fill(RoundRectangle2D.Double(n.x - 6, n.y - 6, n.width + 12, n.height + 12, 16.0, 16.0))
            g.composite = prevComp
        }
        when (n.type) {
            // ── Tasks ────────────────────────────────────────────────────
            BpmnNodeType.SERVICE_TASK       -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.SERVICE,       TASK_BORDER_STROKE)
            BpmnNodeType.USER_TASK          -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.USER,          TASK_BORDER_STROKE)
            BpmnNodeType.MANUAL_TASK        -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.MANUAL,        TASK_BORDER_STROKE)
            BpmnNodeType.CALL_ACTIVITY      -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.CALL,          CALL_ACTIVITY_STROKE)
            BpmnNodeType.SCRIPT_TASK        -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.SCRIPT,        TASK_BORDER_STROKE)
            BpmnNodeType.BUSINESS_RULE_TASK -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.BUSINESS_RULE, TASK_BORDER_STROKE)
            BpmnNodeType.RECEIVE_TASK       -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.RECEIVE,       TASK_BORDER_STROKE)
            BpmnNodeType.SEND_TASK          -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.SEND,          TASK_BORDER_STROKE)
            BpmnNodeType.MAIL_TASK          -> taskRenderer.drawTask(g, n, isSelected, NodeIcon.MAIL,          TASK_BORDER_STROKE)

            // ── Gateways ─────────────────────────────────────────────────
            BpmnNodeType.EXCLUSIVE_GATEWAY   -> gatewayRenderer.drawGateway(g, n, GatewayMarker.X,        isSelected)
            BpmnNodeType.PARALLEL_GATEWAY    -> gatewayRenderer.drawGateway(g, n, GatewayMarker.PLUS,     isSelected)
            BpmnNodeType.INCLUSIVE_GATEWAY   -> gatewayRenderer.drawGateway(g, n, GatewayMarker.CIRCLE,   isSelected)
            BpmnNodeType.EVENT_BASED_GATEWAY -> gatewayRenderer.drawGateway(g, n, GatewayMarker.PENTAGON, isSelected)

            // ── Plain start / end events ────────────────────────────────
            BpmnNodeType.START_EVENT -> eventRenderer.drawStartEvent(g, n, isSelected)
            BpmnNodeType.END_EVENT   -> eventRenderer.drawEndEvent(g, n, isSelected)

            // ── Start events with marker ────────────────────────────────
            BpmnNodeType.TIMER_START_EVENT   -> eventRenderer.drawStartEventWithIcon(g, n, isSelected, EventIcon.TIMER)
            BpmnNodeType.MESSAGE_START_EVENT -> eventRenderer.drawStartEventWithIcon(g, n, isSelected, EventIcon.MESSAGE)
            BpmnNodeType.SIGNAL_START_EVENT  -> eventRenderer.drawStartEventWithIcon(g, n, isSelected, EventIcon.SIGNAL)
            BpmnNodeType.ERROR_START_EVENT   -> eventRenderer.drawStartEventWithIcon(g, n, isSelected, EventIcon.ERROR)

            // ── End events with marker ──────────────────────────────────
            BpmnNodeType.ERROR_END_EVENT     -> eventRenderer.drawEndEventWithIcon(g, n, isSelected, EventIcon.ERROR)
            BpmnNodeType.TERMINATE_END_EVENT -> eventRenderer.drawEndEventWithIcon(g, n, isSelected, EventIcon.TERMINATE)
            BpmnNodeType.CANCEL_END_EVENT    -> eventRenderer.drawEndEventWithIcon(g, n, isSelected, EventIcon.CANCEL)

            // ── Boundary events ─────────────────────────────────────────
            BpmnNodeType.BOUNDARY_TIMER      -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.TIMER)
            BpmnNodeType.BOUNDARY_ERROR      -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.ERROR)
            BpmnNodeType.BOUNDARY_MESSAGE    -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.MESSAGE)
            BpmnNodeType.BOUNDARY_SIGNAL     -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.SIGNAL)
            BpmnNodeType.BOUNDARY_CANCEL     -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.CANCEL)
            BpmnNodeType.BOUNDARY_COMPENSATE -> eventRenderer.drawBoundaryEvent(g, n, isSelected, EventIcon.COMPENSATE)

            // ── Intermediate events ─────────────────────────────────────
            BpmnNodeType.INTERMEDIATE_TIMER_CATCH      -> eventRenderer.drawIntermediateEvent(g, n, isSelected, EventIcon.TIMER,      throwing = false)
            BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH    -> eventRenderer.drawIntermediateEvent(g, n, isSelected, EventIcon.MESSAGE,    throwing = false)
            BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH     -> eventRenderer.drawIntermediateEvent(g, n, isSelected, EventIcon.SIGNAL,     throwing = false)
            BpmnNodeType.INTERMEDIATE_SIGNAL_THROW     -> eventRenderer.drawIntermediateEvent(g, n, isSelected, EventIcon.SIGNAL,     throwing = true)
            BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW -> eventRenderer.drawIntermediateEvent(g, n, isSelected, EventIcon.COMPENSATE, throwing = true)

            // ── Containers / annotations ────────────────────────────────
            BpmnNodeType.SUB_PROCESS       -> containerRenderer.drawSubProcess(g, n, isSelected)
            BpmnNodeType.EVENT_SUB_PROCESS -> containerRenderer.drawSubProcess(g, n, isSelected)
            BpmnNodeType.POOL              -> containerRenderer.drawPool(g, n, isSelected)
            BpmnNodeType.LANE              -> containerRenderer.drawLane(g, n, isSelected)
            BpmnNodeType.TEXT_ANNOTATION   -> containerRenderer.drawTextAnnotation(g, n, isSelected)
        }
    }
}
