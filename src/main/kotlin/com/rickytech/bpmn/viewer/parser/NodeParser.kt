package com.rickytech.bpmn.viewer.parser

import com.rickytech.bpmn.viewer.model.BpmnEdge
import com.rickytech.bpmn.viewer.model.BpmnNode
import com.rickytech.bpmn.viewer.model.BpmnNodeType
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses individual BPMN node and edge elements. Each public method accepts
 * a DOM [Element] and returns the corresponding [BpmnNode] or [BpmnEdge].
 *
 * Delegates extension parsing (multi-instance, listeners, variable mappings)
 * to [ExtensionParser].
 */
internal object NodeParser {

    // ─── Simple / Generic ─────────────────────────────────────────────────

    fun parseSimpleNode(el: Element, type: BpmnNodeType): BpmnNode = BpmnNode(
        id = el.getAttribute("id"),
        name = el.getAttribute("name") ?: "",
        type = type
    )

    // ─── Start Event ──────────────────────────────────────────────────────

    fun parseStartEvent(el: Element): BpmnNode {
        val timerDef = ExtensionParser.findFirstChildByLocalName(el, "timerEventDefinition")
        val msgDef = ExtensionParser.findFirstChildByLocalName(el, "messageEventDefinition")
        val sigDef = ExtensionParser.findFirstChildByLocalName(el, "signalEventDefinition")
        val errDef = ExtensionParser.findFirstChildByLocalName(el, "errorEventDefinition")

        val type = when {
            timerDef != null -> BpmnNodeType.TIMER_START_EVENT
            msgDef != null -> BpmnNodeType.MESSAGE_START_EVENT
            sigDef != null -> BpmnNodeType.SIGNAL_START_EVENT
            errDef != null -> BpmnNodeType.ERROR_START_EVENT
            else -> BpmnNodeType.START_EVENT
        }

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = type,
            timerDuration = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDuration")?.textContent?.trim() },
            timeCycle = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeCycle")?.textContent?.trim() },
            timeDate = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDate")?.textContent?.trim() },
            messageRef = msgDef?.getAttribute("messageRef")?.takeIf { it.isNotBlank() },
            signalRef = sigDef?.getAttribute("signalRef")?.takeIf { it.isNotBlank() },
            errorRef = errDef?.getAttribute("errorRef")?.takeIf { it.isNotBlank() },
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── End Event ────────────────────────────────────────────────────────

    fun parseEndEvent(el: Element): BpmnNode {
        val errDef = ExtensionParser.findFirstChildByLocalName(el, "errorEventDefinition")
        val termDef = ExtensionParser.findFirstChildByLocalName(el, "terminateEventDefinition")
        val cancelDef = ExtensionParser.findFirstChildByLocalName(el, "cancelEventDefinition")

        val type = when {
            errDef != null -> BpmnNodeType.ERROR_END_EVENT
            termDef != null -> BpmnNodeType.TERMINATE_END_EVENT
            cancelDef != null -> BpmnNodeType.CANCEL_END_EVENT
            else -> BpmnNodeType.END_EVENT
        }

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = type,
            errorRef = errDef?.getAttribute("errorRef")?.takeIf { it.isNotBlank() },
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── Service Task ─────────────────────────────────────────────────────

    fun parseServiceTask(el: Element): BpmnNode {
        val javaClass = ExtensionParser.activitiAttr(el, "class")
        val delegate = ExtensionParser.activitiAttr(el, "delegateExpression")
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.SERVICE_TASK,
            javaClass = javaClass,
            delegateExpression = delegate,
            variableMappings = ExtensionParser.parseVariableMappings(el),
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            loopDataInputRef = mi.loopDataInputRef,
            inputDataItem = mi.inputDataItem,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── User Task ────────────────────────────────────────────────────────

    fun parseUserTask(el: Element): BpmnNode {
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.USER_TASK,
            assignee = ExtensionParser.activitiAttr(el, "assignee"),
            candidateUsers = ExtensionParser.activitiAttr(el, "candidateUsers"),
            candidateGroups = ExtensionParser.activitiAttr(el, "candidateGroups"),
            variableMappings = ExtensionParser.parseVariableMappings(el),
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            loopDataInputRef = mi.loopDataInputRef,
            inputDataItem = mi.inputDataItem,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            formKey = ExtensionParser.activitiAttr(el, "formKey"),
            executionListeners = ExtensionParser.parseListeners(el),
            taskListeners = ExtensionParser.parseTaskListeners(el),
        )
    }

    // ─── Call Activity ────────────────────────────────────────────────────

    fun parseCallActivity(el: Element): BpmnNode {
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.CALL_ACTIVITY,
            calledElement = el.getAttribute("calledElement").takeIf { it.isNotBlank() },
            variableMappings = ExtensionParser.parseVariableMappings(el),
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            loopDataInputRef = mi.loopDataInputRef,
            inputDataItem = mi.inputDataItem,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── Manual Task ──────────────────────────────────────────────────────

    fun parseManualTask(el: Element): BpmnNode {
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.MANUAL_TASK,
            variableMappings = ExtensionParser.parseVariableMappings(el),
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            loopDataInputRef = mi.loopDataInputRef,
            inputDataItem = mi.inputDataItem,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── Script Task ──────────────────────────────────────────────────────

    fun parseScriptTask(el: Element): BpmnNode {
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.SCRIPT_TASK,
            scriptFormat = el.getAttribute("scriptFormat")?.takeIf { it.isNotBlank() },
            scriptContent = ExtensionParser.findFirstChildByLocalName(el, "script")?.textContent?.trim(),
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── Business Rule Task ───────────────────────────────────────────────

    fun parseBusinessRuleTask(el: Element): BpmnNode {
        val mi = ExtensionParser.parseMultiInstance(el)
        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = BpmnNodeType.BUSINESS_RULE_TASK,
            javaClass = ExtensionParser.activitiAttr(el, "class"),
            delegateExpression = ExtensionParser.activitiAttr(el, "delegateExpression"),
            isAsync = ExtensionParser.activitiAttr(el, "async")?.equals("true", ignoreCase = true) == true,
            isSequential = mi.isSequential,
            loopCardinality = mi.loopCardinality,
            completionCondition = mi.completionCondition,
            loopCondition = mi.loopCondition,
            executionListeners = ExtensionParser.parseListeners(el),
        )
    }

    // ─── Boundary Event ───────────────────────────────────────────────────

    fun parseBoundaryEvent(el: Element): BpmnNode {
        val attachedTo = el.getAttribute("attachedToRef") ?: ""
        val cancelAct = el.getAttribute("cancelActivity")?.equals("true", ignoreCase = true) != false

        val timerDef = ExtensionParser.findFirstChildByLocalName(el, "timerEventDefinition")
        val errDef = ExtensionParser.findFirstChildByLocalName(el, "errorEventDefinition")
        val msgDef = ExtensionParser.findFirstChildByLocalName(el, "messageEventDefinition")
        val sigDef = ExtensionParser.findFirstChildByLocalName(el, "signalEventDefinition")
        val cancelDef = ExtensionParser.findFirstChildByLocalName(el, "cancelEventDefinition")
        val compDef = ExtensionParser.findFirstChildByLocalName(el, "compensateEventDefinition")

        val type = when {
            timerDef != null -> BpmnNodeType.BOUNDARY_TIMER
            errDef != null -> BpmnNodeType.BOUNDARY_ERROR
            msgDef != null -> BpmnNodeType.BOUNDARY_MESSAGE
            sigDef != null -> BpmnNodeType.BOUNDARY_SIGNAL
            cancelDef != null -> BpmnNodeType.BOUNDARY_CANCEL
            compDef != null -> BpmnNodeType.BOUNDARY_COMPENSATE
            else -> BpmnNodeType.BOUNDARY_TIMER // fallback
        }

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = type,
            attachedToRef = attachedTo,
            cancelActivity = cancelAct,
            timerDuration = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDuration")?.textContent?.trim() },
            timeCycle = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeCycle")?.textContent?.trim() },
            timeDate = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDate")?.textContent?.trim() },
            errorRef = errDef?.getAttribute("errorRef")?.takeIf { it.isNotBlank() },
            messageRef = msgDef?.getAttribute("messageRef")?.takeIf { it.isNotBlank() },
            signalRef = sigDef?.getAttribute("signalRef")?.takeIf { it.isNotBlank() },
        )
    }

    // ─── Sub-Process ──────────────────────────────────────────────────────

    fun parseSubProcess(el: Element, parseChildren: (Element) -> Pair<MutableList<BpmnNode>, MutableList<BpmnEdge>>): BpmnNode {
        val isEvent = el.getAttribute("triggeredByEvent")?.equals("true", ignoreCase = true) == true
        val (childNodes, childEdges) = parseChildren(el)

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = if (isEvent) BpmnNodeType.EVENT_SUB_PROCESS else BpmnNodeType.SUB_PROCESS,
            childNodes = childNodes,
            childEdges = childEdges,
        )
    }

    // ─── Intermediate Catch Event ─────────────────────────────────────────

    fun parseIntermediateCatchEvent(el: Element): BpmnNode {
        val timerDef = ExtensionParser.findFirstChildByLocalName(el, "timerEventDefinition")
        val msgDef = ExtensionParser.findFirstChildByLocalName(el, "messageEventDefinition")
        val sigDef = ExtensionParser.findFirstChildByLocalName(el, "signalEventDefinition")

        val type = when {
            timerDef != null -> BpmnNodeType.INTERMEDIATE_TIMER_CATCH
            msgDef != null -> BpmnNodeType.INTERMEDIATE_MESSAGE_CATCH
            sigDef != null -> BpmnNodeType.INTERMEDIATE_SIGNAL_CATCH
            else -> BpmnNodeType.INTERMEDIATE_TIMER_CATCH // fallback
        }

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = type,
            timerDuration = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDuration")?.textContent?.trim() },
            timeCycle = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeCycle")?.textContent?.trim() },
            timeDate = timerDef?.let { ExtensionParser.findFirstChildByLocalName(it, "timeDate")?.textContent?.trim() },
            messageRef = msgDef?.getAttribute("messageRef")?.takeIf { it.isNotBlank() },
            signalRef = sigDef?.getAttribute("signalRef")?.takeIf { it.isNotBlank() },
        )
    }

    // ─── Intermediate Throw Event ─────────────────────────────────────────

    fun parseIntermediateThrowEvent(el: Element): BpmnNode {
        val sigDef = ExtensionParser.findFirstChildByLocalName(el, "signalEventDefinition")
        val compDef = ExtensionParser.findFirstChildByLocalName(el, "compensateEventDefinition")

        val type = when {
            sigDef != null -> BpmnNodeType.INTERMEDIATE_SIGNAL_THROW
            compDef != null -> BpmnNodeType.INTERMEDIATE_COMPENSATE_THROW
            else -> BpmnNodeType.INTERMEDIATE_SIGNAL_THROW // fallback
        }

        return BpmnNode(
            id = el.getAttribute("id"),
            name = el.getAttribute("name") ?: "",
            type = type,
            signalRef = sigDef?.getAttribute("signalRef")?.takeIf { it.isNotBlank() },
        )
    }

    // ─── Text Annotation ──────────────────────────────────────────────────

    fun parseTextAnnotation(el: Element): BpmnNode {
        val text = ExtensionParser.findFirstChildByLocalName(el, "text")?.textContent?.trim() ?: ""
        return BpmnNode(
            id = el.getAttribute("id"),
            name = text,
            type = BpmnNodeType.TEXT_ANNOTATION,
        )
    }

    // ─── Sequence Flow ────────────────────────────────────────────────────

    fun parseSequenceFlow(el: Element): BpmnEdge {
        var condition: String? = null
        val children = el.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType == Node.ELEMENT_NODE) {
                val localName = (c as Element).localName ?: c.nodeName.substringAfterLast(':')
                if (localName == "conditionExpression") {
                    condition = c.textContent?.trim()
                }
            }
        }
        return BpmnEdge(
            id = el.getAttribute("id"),
            name = el.getAttribute("name").takeIf { it.isNotBlank() },
            sourceRef = el.getAttribute("sourceRef"),
            targetRef = el.getAttribute("targetRef"),
            conditionExpression = condition
        )
    }

    // ─── Association ──────────────────────────────────────────────────────

    fun parseAssociation(el: Element): BpmnEdge {
        return BpmnEdge(
            id = el.getAttribute("id") ?: "",
            name = null,
            sourceRef = el.getAttribute("sourceRef") ?: "",
            targetRef = el.getAttribute("targetRef") ?: "",
            isAssociation = true,
        )
    }
}
