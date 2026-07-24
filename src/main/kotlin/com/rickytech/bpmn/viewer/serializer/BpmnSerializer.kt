package com.rickytech.bpmn.viewer.serializer

import com.rickytech.bpmn.viewer.edit.EditableEdge
import com.rickytech.bpmn.viewer.edit.EditableModel
import com.rickytech.bpmn.viewer.edit.EditableNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import com.rickytech.bpmn.viewer.model.ListenerDef
import com.rickytech.bpmn.viewer.model.VariableMapping
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * 将 [EditableModel] 序列化为 Activiti 兼容的 BPMN 2.0 XML。
 *
 * 与 [com.rickytech.bpmn.viewer.parser.BpmnParser] 互为逆操作：
 * 序列化结果可被 BpmnParser 重新解析（round-trip）。
 *
 * 实现说明：直接使用带前缀的 tagName 创建元素以避免命名空间前缀冲突；
 * 所有 xmlns 在根元素上一次性声明。
 */
class BpmnSerializer {

    companion object {
        private const val BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL"
        private const val XSI_NS = "http://www.w3.org/2001/XMLSchema-instance"
        private const val ACTIVITI_NS = "http://activiti.org/bpmn"
        private const val BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI"
        private const val OMGDC_NS = "http://www.omg.org/spec/DD/20100524/DC"
        private const val OMGDI_NS = "http://www.omg.org/spec/DD/20100524/DI"
        private const val TARGET_NS = "http://www.activiti.org/processdef"
    }

    fun serialize(model: EditableModel): String {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.newDocument()

        val definitions = doc.createElement("definitions")
        definitions.setAttribute("xmlns", BPMN_NS)
        definitions.setAttribute("xmlns:xsi", XSI_NS)
        definitions.setAttribute("xmlns:activiti", ACTIVITI_NS)
        definitions.setAttribute("xmlns:bpmndi", BPMNDI_NS)
        definitions.setAttribute("xmlns:omgdc", OMGDC_NS)
        definitions.setAttribute("xmlns:omgdi", OMGDI_NS)
        definitions.setAttribute("targetNamespace", TARGET_NS)
        doc.appendChild(definitions)

        val process = doc.createElement("process")
        process.setAttribute("id", model.processId)
        if (model.processName.isNotBlank()) {
            process.setAttribute("name", model.processName)
        }
        process.setAttribute("isExecutable", "true")
        definitions.appendChild(process)

        // 收集网关默认流：在源节点元素上输出 default="edgeId"
        val defaultFlowBySource: Map<String, String> = model.edges
            .filter { it.isDefaultFlow }
            .associate { it.sourceRef to it.id }

        // 节点
        for (node in model.nodes) {
            val el = createNodeElement(doc, node, defaultFlowBySource[node.id])
            if (el != null) process.appendChild(el)
        }

        // 连线
        for (edge in model.edges) {
            val el = createEdgeElement(doc, edge) ?: continue
            process.appendChild(el)
        }

        // DI 部分
        appendDiagram(doc, definitions, model)

        return writeToString(doc)
    }

    // ------------------------------------------------------------------
    // 节点元素构建
    // ------------------------------------------------------------------

    private fun createNodeElement(doc: Document, node: EditableNode, defaultFlowId: String?): Element? {
        val tagName = nodeTagName(node.type) ?: return null
        val el = doc.createElement(tagName)
        el.setAttribute("id", node.id)
        if (node.name.isNotBlank()) {
            el.setAttribute("name", node.name)
        }

        when (node.type) {
            BpmnNodeType.SERVICE_TASK,
            BpmnNodeType.MAIL_TASK -> {
                applyAsync(el, node)
                applyServiceLikeAttrs(el, node)
                if (node.type == BpmnNodeType.MAIL_TASK) {
                    // mailTask 在 Activiti 中实际为 serviceTask + 特殊 type
                    el.setAttribute("activiti:type", "mail")
                }
                appendExtensionElements(doc, el, node)
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.USER_TASK -> {
                applyAsync(el, node)
                node.assignee?.takeIf { it.isNotBlank() }?.let { el.setAttribute("activiti:assignee", it) }
                node.candidateUsers?.takeIf { it.isNotBlank() }?.let { el.setAttribute("activiti:candidateUsers", it) }
                node.candidateGroups?.takeIf { it.isNotBlank() }?.let { el.setAttribute("activiti:candidateGroups", it) }
                node.formKey?.takeIf { it.isNotBlank() }?.let { el.setAttribute("activiti:formKey", it) }
                appendExtensionElements(doc, el, node)
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.SCRIPT_TASK -> {
                applyAsync(el, node)
                node.scriptFormat?.takeIf { it.isNotBlank() }?.let { el.setAttribute("scriptFormat", it) }
                appendExtensionElements(doc, el, node)
                node.scriptContent?.takeIf { it.isNotBlank() }?.let {
                    val scriptEl = doc.createElement("script")
                    scriptEl.appendChild(doc.createCDATASection(it))
                    el.appendChild(scriptEl)
                }
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.BUSINESS_RULE_TASK -> {
                applyAsync(el, node)
                applyServiceLikeAttrs(el, node)
                appendExtensionElements(doc, el, node)
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.MANUAL_TASK,
            BpmnNodeType.RECEIVE_TASK,
            BpmnNodeType.SEND_TASK -> {
                applyAsync(el, node)
                appendExtensionElements(doc, el, node)
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.CALL_ACTIVITY -> {
                applyAsync(el, node)
                node.calledElement?.takeIf { it.isNotBlank() }?.let { el.setAttribute("calledElement", it) }
                appendExtensionElements(doc, el, node)
                appendMultiInstance(doc, el, node)
            }
            BpmnNodeType.START_EVENT,
            BpmnNodeType.TIMER_START_EVENT,
            BpmnNodeType.MESSAGE_START_EVENT,
            BpmnNodeType.SIGNAL_START_EVENT,
            BpmnNodeType.ERROR_START_EVENT -> {
                applyAsync(el, node)
                appendExtensionElements(doc, el, node)
                appendEventDefinition(doc, el, node)
            }
            BpmnNodeType.END_EVENT,
            BpmnNodeType.ERROR_END_EVENT,
            BpmnNodeType.TERMINATE_END_EVENT,
            BpmnNodeType.CANCEL_END_EVENT -> {
                appendExtensionElements(doc, el, node)
                appendEventDefinition(doc, el, node)
            }
            BpmnNodeType.BOUNDARY_TIMER,
            BpmnNodeType.BOUNDARY_ERROR,
            BpmnNodeType.BOUNDARY_MESSAGE,
            BpmnNodeType.BOUNDARY_SIGNAL,
            BpmnNodeType.BOUNDARY_CANCEL,
            BpmnNodeType.BOUNDARY_COMPENSATE -> {
                node.attachedToRef?.takeIf { it.isNotBlank() }?.let { el.setAttribute("attachedToRef", it) }
                el.setAttribute("cancelActivity", node.cancelActivity.toString())
                appendEventDefinition(doc, el, node)
            }
            BpmnNodeType.INTERMEDIATE_TIMER_CATCH,
            BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH,
            BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH,
            BpmnNodeType.INTERMEDIATE_SIGNAL_THROW,
            BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW -> {
                appendEventDefinition(doc, el, node)
            }
            BpmnNodeType.SUB_PROCESS,
            BpmnNodeType.EVENT_SUB_PROCESS -> {
                if (node.type == BpmnNodeType.EVENT_SUB_PROCESS) {
                    el.setAttribute("triggeredByEvent", "true")
                }
                appendExtensionElements(doc, el, node)
            }
            BpmnNodeType.TEXT_ANNOTATION -> {
                if (node.name.isNotBlank()) {
                    // textAnnotation 不输出 name 属性，正文进入 <text>
                    el.removeAttribute("name")
                    val textEl = doc.createElement("text")
                    textEl.textContent = node.name
                    el.appendChild(textEl)
                }
            }
            BpmnNodeType.EXCLUSIVE_GATEWAY,
            BpmnNodeType.INCLUSIVE_GATEWAY -> {
                defaultFlowId?.let { el.setAttribute("default", it) }
                appendExtensionElements(doc, el, node)
            }
            BpmnNodeType.PARALLEL_GATEWAY,
            BpmnNodeType.EVENT_BASED_GATEWAY -> {
                appendExtensionElements(doc, el, node)
            }
            BpmnNodeType.POOL,
            BpmnNodeType.LANE -> return null
        }
        return el
    }

    private fun nodeTagName(type: BpmnNodeType): String? = when (type) {
        BpmnNodeType.START_EVENT,
        BpmnNodeType.TIMER_START_EVENT,
        BpmnNodeType.MESSAGE_START_EVENT,
        BpmnNodeType.SIGNAL_START_EVENT,
        BpmnNodeType.ERROR_START_EVENT -> "startEvent"
        BpmnNodeType.END_EVENT,
        BpmnNodeType.ERROR_END_EVENT,
        BpmnNodeType.TERMINATE_END_EVENT,
        BpmnNodeType.CANCEL_END_EVENT -> "endEvent"
        BpmnNodeType.SERVICE_TASK -> "serviceTask"
        BpmnNodeType.USER_TASK -> "userTask"
        BpmnNodeType.SCRIPT_TASK -> "scriptTask"
        BpmnNodeType.MANUAL_TASK -> "manualTask"
        BpmnNodeType.BUSINESS_RULE_TASK -> "businessRuleTask"
        BpmnNodeType.RECEIVE_TASK -> "receiveTask"
        BpmnNodeType.SEND_TASK -> "sendTask"
        BpmnNodeType.MAIL_TASK -> "serviceTask"
        BpmnNodeType.CALL_ACTIVITY -> "callActivity"
        BpmnNodeType.EXCLUSIVE_GATEWAY -> "exclusiveGateway"
        BpmnNodeType.PARALLEL_GATEWAY -> "parallelGateway"
        BpmnNodeType.INCLUSIVE_GATEWAY -> "inclusiveGateway"
        BpmnNodeType.EVENT_BASED_GATEWAY -> "eventBasedGateway"
        BpmnNodeType.SUB_PROCESS,
        BpmnNodeType.EVENT_SUB_PROCESS -> "subProcess"
        BpmnNodeType.BOUNDARY_TIMER,
        BpmnNodeType.BOUNDARY_ERROR,
        BpmnNodeType.BOUNDARY_MESSAGE,
        BpmnNodeType.BOUNDARY_SIGNAL,
        BpmnNodeType.BOUNDARY_CANCEL,
        BpmnNodeType.BOUNDARY_COMPENSATE -> "boundaryEvent"
        BpmnNodeType.INTERMEDIATE_TIMER_CATCH,
        BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH,
        BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH -> "intermediateCatchEvent"
        BpmnNodeType.INTERMEDIATE_SIGNAL_THROW,
        BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW -> "intermediateThrowEvent"
        BpmnNodeType.TEXT_ANNOTATION -> "textAnnotation"
        BpmnNodeType.POOL,
        BpmnNodeType.LANE -> null
    }

    private fun applyAsync(el: Element, node: EditableNode) {
        if (node.isAsync) {
            el.setAttribute("activiti:async", "true")
        }
    }

    private fun applyServiceLikeAttrs(el: Element, node: EditableNode) {
        node.javaClass?.takeIf { it.isNotBlank() }?.let { el.setAttribute("activiti:class", it) }
        node.delegateExpression?.takeIf { it.isNotBlank() }?.let {
            el.setAttribute("activiti:delegateExpression", it)
        }
    }

    // ------------------------------------------------------------------
    // 事件定义
    // ------------------------------------------------------------------

    private fun appendEventDefinition(doc: Document, parent: Element, node: EditableNode) {
        when (node.type) {
            BpmnNodeType.TIMER_START_EVENT,
            BpmnNodeType.INTERMEDIATE_TIMER_CATCH,
            BpmnNodeType.BOUNDARY_TIMER -> appendTimerDefinition(doc, parent, node)

            BpmnNodeType.MESSAGE_START_EVENT,
            BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH,
            BpmnNodeType.BOUNDARY_MESSAGE -> {
                val def = doc.createElement("messageEventDefinition")
                node.messageRef?.takeIf { it.isNotBlank() }?.let { def.setAttribute("messageRef", it) }
                parent.appendChild(def)
            }

            BpmnNodeType.SIGNAL_START_EVENT,
            BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH,
            BpmnNodeType.INTERMEDIATE_SIGNAL_THROW,
            BpmnNodeType.BOUNDARY_SIGNAL -> {
                val def = doc.createElement("signalEventDefinition")
                node.signalRef?.takeIf { it.isNotBlank() }?.let { def.setAttribute("signalRef", it) }
                parent.appendChild(def)
            }

            BpmnNodeType.ERROR_START_EVENT,
            BpmnNodeType.ERROR_END_EVENT,
            BpmnNodeType.BOUNDARY_ERROR -> {
                val def = doc.createElement("errorEventDefinition")
                node.errorRef?.takeIf { it.isNotBlank() }?.let { def.setAttribute("errorRef", it) }
                node.errorCode?.takeIf { it.isNotBlank() }?.let { def.setAttribute("errorCode", it) }
                parent.appendChild(def)
            }

            BpmnNodeType.TERMINATE_END_EVENT -> {
                parent.appendChild(doc.createElement("terminateEventDefinition"))
            }

            BpmnNodeType.CANCEL_END_EVENT,
            BpmnNodeType.BOUNDARY_CANCEL -> {
                parent.appendChild(doc.createElement("cancelEventDefinition"))
            }

            BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW,
            BpmnNodeType.BOUNDARY_COMPENSATE -> {
                parent.appendChild(doc.createElement("compensateEventDefinition"))
            }

            else -> { /* 普通 START/END 事件无定义子元素 */ }
        }
    }

    private fun appendTimerDefinition(doc: Document, parent: Element, node: EditableNode) {
        val def = doc.createElement("timerEventDefinition")
        node.timerDuration?.takeIf { it.isNotBlank() }?.let {
            val c = doc.createElement("timeDuration"); c.textContent = it; def.appendChild(c)
        }
        node.timeCycle?.takeIf { it.isNotBlank() }?.let {
            val c = doc.createElement("timeCycle"); c.textContent = it; def.appendChild(c)
        }
        node.timeDate?.takeIf { it.isNotBlank() }?.let {
            val c = doc.createElement("timeDate"); c.textContent = it; def.appendChild(c)
        }
        parent.appendChild(def)
    }

    // ------------------------------------------------------------------
    // extensionElements: 变量映射、监听器
    // ------------------------------------------------------------------

    private fun appendExtensionElements(doc: Document, parent: Element, node: EditableNode) {
        val hasMappings = node.variableMappings.isNotEmpty()
        val hasExecListeners = node.executionListeners.isNotEmpty()
        val hasTaskListeners = node.taskListeners.isNotEmpty()
        if (!hasMappings && !hasExecListeners && !hasTaskListeners) return

        val ext = doc.createElement("extensionElements")
        for (mapping in node.variableMappings) appendVariableMapping(doc, ext, mapping)
        for (listener in node.executionListeners) {
            appendListener(doc, ext, "activiti:executionListener", listener)
        }
        for (listener in node.taskListeners) {
            appendListener(doc, ext, "activiti:taskListener", listener)
        }
        parent.appendChild(ext)
    }

    private fun appendVariableMapping(doc: Document, parent: Element, mapping: VariableMapping) {
        val tag = if (mapping.direction == "out") "activiti:out" else "activiti:in"
        val el = doc.createElement(tag)
        if (mapping.source.isNotBlank()) el.setAttribute("source", mapping.source)
        if (mapping.target.isNotBlank()) el.setAttribute("target", mapping.target)
        parent.appendChild(el)
    }

    private fun appendListener(doc: Document, parent: Element, tagName: String, listener: ListenerDef) {
        val el = doc.createElement(tagName)
        if (listener.event.isNotBlank()) el.setAttribute("event", listener.event)
        when (listener.implementationType) {
            "class" -> el.setAttribute("class", listener.implementation)
            "expression" -> el.setAttribute("expression", listener.implementation)
            "delegateExpression" -> el.setAttribute("delegateExpression", listener.implementation)
            else -> el.setAttribute("class", listener.implementation)
        }
        parent.appendChild(el)
    }

    // ------------------------------------------------------------------
    // 多实例
    // ------------------------------------------------------------------

    private fun appendMultiInstance(doc: Document, parent: Element, node: EditableNode) {
        val seq = node.isSequential ?: return
        val mi = doc.createElement("multiInstanceLoopCharacteristics")
        mi.setAttribute("isSequential", seq.toString())
        node.inputDataItem?.takeIf { it.isNotBlank() }?.let {
            mi.setAttribute("activiti:elementVariable", it)
        }
        node.loopCardinality?.takeIf { it.isNotBlank() }?.let {
            val c = doc.createElement("loopCardinality"); c.textContent = it; mi.appendChild(c)
        }
        node.loopDataInputRef?.takeIf { it.isNotBlank() }?.let {
            val c = doc.createElement("loopDataInputRef"); c.textContent = it; mi.appendChild(c)
        }
        if (node.completionCondition.isNotBlank()) {
            val c = doc.createElement("completionCondition"); c.textContent = node.completionCondition; mi.appendChild(c)
        }
        if (node.loopCondition.isNotBlank()) {
            val c = doc.createElement("loopCondition"); c.textContent = node.loopCondition; mi.appendChild(c)
        }
        parent.appendChild(mi)
    }

    // ------------------------------------------------------------------
    // 连线
    // ------------------------------------------------------------------

    private fun createEdgeElement(doc: Document, edge: EditableEdge): Element? {
        if (edge.isMessageFlow || edge.isAssociation) {
            // messageFlow 属于 collaboration；association 当前编辑模型为扁平流程，跳过
            return null
        }
        val el = doc.createElement("sequenceFlow")
        el.setAttribute("id", edge.id)
        edge.name?.takeIf { it.isNotBlank() }?.let { el.setAttribute("name", it) }
        el.setAttribute("sourceRef", edge.sourceRef)
        el.setAttribute("targetRef", edge.targetRef)
        edge.conditionExpression?.takeIf { it.isNotBlank() }?.let { expr ->
            val cond = doc.createElement("conditionExpression")
            cond.setAttribute("xsi:type", "tFormalExpression")
            cond.appendChild(doc.createCDATASection(expr))
            el.appendChild(cond)
        }
        return el
    }

    // ------------------------------------------------------------------
    // BPMN DI
    // ------------------------------------------------------------------

    private fun appendDiagram(doc: Document, definitions: Element, model: EditableModel) {
        val diagram = doc.createElement("bpmndi:BPMNDiagram")
        diagram.setAttribute("id", "BPMNDiagram_1")
        val plane = doc.createElement("bpmndi:BPMNPlane")
        plane.setAttribute("id", "BPMNPlane_1")
        plane.setAttribute("bpmnElement", model.processId)
        diagram.appendChild(plane)

        for (node in model.nodes) {
            if (node.type == BpmnNodeType.POOL || node.type == BpmnNodeType.LANE) continue
            val shape = doc.createElement("bpmndi:BPMNShape")
            shape.setAttribute("id", "BPMNShape_${node.id}")
            shape.setAttribute("bpmnElement", node.id)
            val bounds = doc.createElement("omgdc:Bounds")
            bounds.setAttribute("x", node.x.toInt().toString())
            bounds.setAttribute("y", node.y.toInt().toString())
            bounds.setAttribute("width", node.width.toInt().toString())
            bounds.setAttribute("height", node.height.toInt().toString())
            shape.appendChild(bounds)
            plane.appendChild(shape)
        }

        for (edge in model.edges) {
            if (edge.routePoints.isEmpty()) continue
            val edgeEl = doc.createElement("bpmndi:BPMNEdge")
            edgeEl.setAttribute("id", "BPMNEdge_${edge.id}")
            edgeEl.setAttribute("bpmnElement", edge.id)
            for ((px, py) in edge.routePoints) {
                val wp = doc.createElement("omgdi:waypoint")
                wp.setAttribute("x", px.toInt().toString())
                wp.setAttribute("y", py.toInt().toString())
                edgeEl.appendChild(wp)
            }
            plane.appendChild(edgeEl)
        }

        definitions.appendChild(diagram)
    }

    // ------------------------------------------------------------------
    // 输出
    // ------------------------------------------------------------------

    private fun writeToString(doc: Document): String {
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.METHOD, "xml")
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "yes")
            setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")
        }
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        return writer.toString()
    }
}
