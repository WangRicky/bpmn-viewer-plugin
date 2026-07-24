package com.rickytech.bpmn.viewer.parser

import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnModel
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * BPMN XML parser based on DOM. Parses Activiti-flavoured BPMN 2.0.
 * The activiti namespace URI is "http://activiti.org/bpmn".
 *
 * Node-level parsing is delegated to [NodeParser]; extension element parsing
 * (multi-instance, listeners, variable mappings) is handled by [ExtensionParser].
 */
class BpmnParser {

    fun parse(xml: String): BpmnModel = parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

    fun parse(input: InputStream): BpmnModel {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isValidating = false
            try { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) } catch (_: Throwable) {}
            try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Throwable) {}
            try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Throwable) {}
        }
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(input)
        doc.documentElement.normalize()

        // Collect all <process> elements directly under <definitions>. Some BPMN
        // files (typically collaboration scenarios) declare more than one process,
        // one per participant; merge their nodes and edges so the viewer can
        // render the entire collaboration.
        val processEls = mutableListOf<Element>()
        run {
            val defChildren = doc.documentElement.childNodes
            for (i in 0 until defChildren.length) {
                val c = defChildren.item(i)
                if (c.nodeType != Node.ELEMENT_NODE) continue
                val ce = c as Element
                val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
                if (local == "process") processEls += ce
            }
        }

        val processId = processEls.firstOrNull()?.getAttribute("id") ?: ""
        val processName = processEls.firstOrNull()?.getAttribute("name") ?: ""

        val nodes = mutableListOf<BpmnNode>()
        val edges = mutableListOf<BpmnEdge>()

        // Parse collaboration (pools & message flows)
        val collaborationEl = ExtensionParser.findFirstChildByLocalName(doc.documentElement, "collaboration")
        val pools = mutableListOf<BpmnNode>()
        val lanes = mutableListOf<BpmnNode>()
        if (collaborationEl != null) {
            val collChildren = collaborationEl.childNodes
            for (i in 0 until collChildren.length) {
                val c = collChildren.item(i)
                if (c.nodeType != Node.ELEMENT_NODE) continue
                val ce = c as Element
                val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
                if (local == "participant") {
                    pools += BpmnNode(
                        id = ce.getAttribute("id") ?: "",
                        name = ce.getAttribute("name") ?: "",
                        type = BpmnNodeType.POOL,
                    )
                }
                if (local == "messageFlow") {
                    edges += BpmnEdge(
                        id = ce.getAttribute("id") ?: "",
                        name = ce.getAttribute("name")?.takeIf { it.isNotBlank() },
                        sourceRef = ce.getAttribute("sourceRef") ?: "",
                        targetRef = ce.getAttribute("targetRef") ?: "",
                        isMessageFlow = true,
                    )
                }
            }
        }

        // Parse laneSet for every process
        for (processEl in processEls) {
            val laneSet = ExtensionParser.findFirstChildByLocalName(processEl, "laneSet") ?: continue
            val laneChildren = laneSet.childNodes
            for (i in 0 until laneChildren.length) {
                val c = laneChildren.item(i)
                if (c.nodeType != Node.ELEMENT_NODE) continue
                val ce = c as Element
                val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
                if (local == "lane") {
                    lanes += BpmnNode(
                        id = ce.getAttribute("id") ?: "",
                        name = ce.getAttribute("name") ?: "",
                        type = BpmnNodeType.LANE,
                    )
                }
            }
        }

        // Parse children of every process and merge into the global lists
        val defaultFlowIds = mutableSetOf<String>()
        for (processEl in processEls) {
            val (parsedNodes, parsedEdges) = parseChildElements(processEl)
            nodes += parsedNodes
            edges += parsedEdges

            // Detect default flows from gateways inside this process
            val gwChildren = processEl.childNodes
            for (i in 0 until gwChildren.length) {
                val child = gwChildren.item(i)
                if (child.nodeType != Node.ELEMENT_NODE) continue
                val ce = child as Element
                val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
                if (local in setOf("exclusiveGateway", "inclusiveGateway")) {
                    ce.getAttribute("default")?.takeIf { it.isNotBlank() }?.let { defaultFlowIds.add(it) }
                }
            }
        }

        if (processEls.isEmpty() && nodes.isEmpty() && edges.isEmpty()) {
            error(
                "该文件不是有效的 BPMN 流程定义（未找到 <process> 元素）。" +
                "This file does not contain a valid BPMN process definition (no <process> element found)."
            )
        }

        val finalEdges = edges.map { e ->
            if (e.id in defaultFlowIds) e.copy(isDefaultFlow = true) else e
        }

        // Pull node geometry from the optional <bpmndi:BPMNDiagram> section so
        // the viewer can honour the original BPMN coordinates instead of
        // running a layout algorithm. Nodes that are missing from the diagram
        // simply keep their default coordinates and can be repaired later.
        val diagramEl = ExtensionParser.findFirstChildByLocalName(doc.documentElement, "BPMNDiagram")
        if (diagramEl != null) {
            applyDiagramCoordinates(diagramEl, nodes, pools, lanes)
        }

        return BpmnModel(processId, processName, nodes, finalEdges, pools, lanes)
    }

    /**
     * Walks the <bpmndi:BPMNPlane> children and copies the (x, y, width, height)
     * declared on each <bpmndi:BPMNShape>'s <omgdc:Bounds> onto the matching
     * BpmnNode (looked up by the `bpmnElement` reference). Pools and lanes are
     * included so collaboration containers also receive their DI coordinates.
     */
    private fun applyDiagramCoordinates(
        diagram: Element,
        nodes: List<BpmnNode>,
        pools: List<BpmnNode> = emptyList(),
        lanes: List<BpmnNode> = emptyList()
    ) {
        val nodesById = mutableMapOf<String, BpmnNode>()
        for (node in nodes) {
            nodesById[node.id] = node
            // Also include child nodes from sub-processes
            for (child in node.childNodes) {
                nodesById[child.id] = child
            }
        }
        for (p in pools) nodesById[p.id] = p
        for (l in lanes) nodesById[l.id] = l
        val plane = ExtensionParser.findFirstChildByLocalName(diagram, "BPMNPlane") ?: return
        val children = plane.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val el = c as Element
            val local = el.localName ?: el.nodeName.substringAfterLast(':')
            if (local != "BPMNShape") continue
            val ref = el.getAttribute("bpmnElement").takeIf { it.isNotBlank() } ?: continue
            val node = nodesById[ref] ?: continue
            val bounds = ExtensionParser.findFirstChildByLocalName(el, "Bounds") ?: continue
            val x = bounds.getAttribute("x").toDoubleOrNull() ?: continue
            val y = bounds.getAttribute("y").toDoubleOrNull() ?: continue
            val w = bounds.getAttribute("width").toDoubleOrNull() ?: continue
            val h = bounds.getAttribute("height").toDoubleOrNull() ?: continue
            node.x = x
            node.y = y
            node.width = w
            node.height = h
        }
    }

    /**
     * Parses the child elements of a process/subProcess container, extracting
     * all recognised BPMN nodes and edges into separate lists. Delegates
     * individual element parsing to [NodeParser].
     */
    internal fun parseChildElements(parent: Element): Pair<MutableList<BpmnNode>, MutableList<BpmnEdge>> {
        val nodes = mutableListOf<BpmnNode>()
        val edges = mutableListOf<BpmnEdge>()

        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element

            when (el.localName ?: el.nodeName.substringAfterLast(':')) {
                "startEvent"        -> nodes += NodeParser.parseStartEvent(el)
                "endEvent"          -> nodes += NodeParser.parseEndEvent(el)
                "serviceTask"       -> nodes += NodeParser.parseServiceTask(el)
                "userTask"          -> nodes += NodeParser.parseUserTask(el)
                "callActivity"      -> nodes += NodeParser.parseCallActivity(el)
                "manualTask"        -> nodes += NodeParser.parseManualTask(el)
                "mailTask"          -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.MAIL_TASK)
                "exclusiveGateway"  -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.EXCLUSIVE_GATEWAY)
                "parallelGateway"   -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.PARALLEL_GATEWAY)
                "inclusiveGateway"  -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.INCLUSIVE_GATEWAY)
                "eventBasedGateway" -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.EVENT_BASED_GATEWAY)
                "scriptTask"        -> nodes += NodeParser.parseScriptTask(el)
                "businessRuleTask"  -> nodes += NodeParser.parseBusinessRuleTask(el)
                "receiveTask"       -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.RECEIVE_TASK)
                "sendTask"          -> nodes += NodeParser.parseSimpleNode(el, BpmnNodeType.SEND_TASK)
                "subProcess"        -> nodes += NodeParser.parseSubProcess(el, ::parseChildElements)
                "boundaryEvent"     -> nodes += NodeParser.parseBoundaryEvent(el)
                "intermediateThrowEvent" -> nodes += NodeParser.parseIntermediateThrowEvent(el)
                "intermediateCatchEvent" -> nodes += NodeParser.parseIntermediateCatchEvent(el)
                "textAnnotation"    -> nodes += NodeParser.parseTextAnnotation(el)
                "association"       -> edges += NodeParser.parseAssociation(el)
                "sequenceFlow"      -> edges += NodeParser.parseSequenceFlow(el)
            }
        }

        return nodes to edges
    }
}
