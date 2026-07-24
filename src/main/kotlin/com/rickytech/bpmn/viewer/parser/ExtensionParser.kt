package com.rickytech.bpmn.viewer.parser

import com.rickytech.bpmn.viewer.model.ListenerDef
import com.rickytech.bpmn.viewer.model.VariableMapping
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Parses BPMN extension elements: multi-instance configuration, variable
 * mappings, execution listeners, and task listeners. Also exposes shared DOM
 * utility helpers used across the parser package.
 */
internal object ExtensionParser {

    /** Activiti namespace URI used across all parsers. */
    const val ACTIVITI_NS = "http://activiti.org/bpmn"

    // ─── DOM Utilities ────────────────────────────────────────────────────

    /**
     * Finds the first direct child element matching the given local name,
     * ignoring namespace prefix differences.
     */
    fun findFirstChildByLocalName(parent: Element, localName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType == Node.ELEMENT_NODE) {
                val ln = (c as Element).localName ?: c.nodeName.substringAfterLast(':')
                if (ln == localName) return c
            }
        }
        return null
    }

    /**
     * Reads an Activiti-namespaced attribute, falling back to the qualified-name
     * form for documents that did not declare the namespace properly.
     */
    fun activitiAttr(el: Element, name: String): String? =
        el.getAttributeNS(ACTIVITI_NS, name).takeIf { it.isNotBlank() }
            ?: el.getAttribute("activiti:$name").takeIf { it.isNotBlank() }

    // ─── Multi-Instance ───────────────────────────────────────────────────

    fun parseMultiInstance(el: Element): MultiInstanceInfo {
        val mi = findFirstChildByLocalName(el, "multiInstanceLoopCharacteristics")
            ?: return MultiInstanceInfo()
        val seqAttr = mi.getAttribute("isSequential")
        val isSeq = if (seqAttr.isNullOrBlank()) null else seqAttr.equals("true", ignoreCase = true)
        val cardinality = findFirstChildByLocalName(mi, "loopCardinality")?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val dataInputRef = findFirstChildByLocalName(mi, "loopDataInputRef")?.textContent?.trim()?.takeIf { it.isNotBlank() }
        val itemEl = findFirstChildByLocalName(mi, "inputDataItem")
        val itemName = itemEl?.getAttribute("name")?.takeIf { it.isNotBlank() }
        val completionCondition = findFirstChildByLocalName(mi, "completionCondition")?.textContent?.trim() ?: ""
        val loopCondition = findFirstChildByLocalName(mi, "loopCondition")?.textContent?.trim() ?: ""
        return MultiInstanceInfo(
            isSequential = isSeq,
            loopCardinality = cardinality,
            loopDataInputRef = dataInputRef,
            inputDataItem = itemName,
            completionCondition = completionCondition,
            loopCondition = loopCondition
        )
    }

    // ─── Variable Mappings ────────────────────────────────────────────────

    /**
     * Parses <extensionElements> for all <activiti:in> / <activiti:out> entries,
     * returning their (direction, source, target) triples in document order.
     */
    fun parseVariableMappings(el: Element): List<VariableMapping> {
        val ext = findFirstChildByLocalName(el, "extensionElements") ?: return emptyList()
        val out = mutableListOf<VariableMapping>()
        val children = ext.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val ce = c as Element
            val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
            if (local != "in" && local != "out") continue
            val source = ce.getAttribute("source").ifBlank { ce.getAttribute("sourceExpression") }
            val target = ce.getAttribute("target").ifBlank { ce.getAttribute("targetExpression") }
            if (source.isBlank() && target.isBlank()) continue
            out += VariableMapping(direction = local, source = source, target = target)
        }
        return out
    }

    // ─── Execution Listeners ──────────────────────────────────────────────

    fun parseListeners(el: Element): List<ListenerDef> {
        val ext = findFirstChildByLocalName(el, "extensionElements") ?: return emptyList()
        val result = mutableListOf<ListenerDef>()
        val children = ext.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val ce = c as Element
            val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
            if (local != "executionListener") continue
            val event = ce.getAttribute("event") ?: ""
            val cls = ce.getAttributeNS(ACTIVITI_NS, "class").takeIf { it.isNotBlank() }
                ?: ce.getAttribute("class").takeIf { it.isNotBlank() }
            val expr = ce.getAttribute("expression").takeIf { it.isNotBlank() }
            val delegate = ce.getAttribute("delegateExpression").takeIf { it.isNotBlank() }
            val (impl, implType) = when {
                cls != null -> cls to "class"
                expr != null -> expr to "expression"
                delegate != null -> delegate to "delegateExpression"
                else -> continue
            }
            result += ListenerDef(event = event, implementation = impl, implementationType = implType)
        }
        return result
    }

    // ─── Task Listeners ───────────────────────────────────────────────────

    fun parseTaskListeners(el: Element): List<ListenerDef> {
        val ext = findFirstChildByLocalName(el, "extensionElements") ?: return emptyList()
        val result = mutableListOf<ListenerDef>()
        val children = ext.childNodes
        for (i in 0 until children.length) {
            val c = children.item(i)
            if (c.nodeType != Node.ELEMENT_NODE) continue
            val ce = c as Element
            val local = ce.localName ?: ce.nodeName.substringAfterLast(':')
            if (local != "taskListener") continue
            val event = ce.getAttribute("event") ?: ""
            val cls = ce.getAttributeNS(ACTIVITI_NS, "class").takeIf { it.isNotBlank() }
                ?: ce.getAttribute("class").takeIf { it.isNotBlank() }
            val expr = ce.getAttribute("expression").takeIf { it.isNotBlank() }
            val delegate = ce.getAttribute("delegateExpression").takeIf { it.isNotBlank() }
            val (impl, implType) = when {
                cls != null -> cls to "class"
                expr != null -> expr to "expression"
                delegate != null -> delegate to "delegateExpression"
                else -> continue
            }
            result += ListenerDef(event = event, implementation = impl, implementationType = implType)
        }
        return result
    }
}

/**
 * Holds parsed multi-instance loop characteristics for a BPMN activity.
 */
internal data class MultiInstanceInfo(
    val isSequential: Boolean? = null,
    val loopCardinality: String? = null,
    val loopDataInputRef: String? = null,
    val inputDataItem: String? = null,
    val completionCondition: String = "",
    val loopCondition: String = ""
)
