package com.rickytech.bpmn.viewer.model

data class BpmnEdge(
    val id: String,
    val name: String?,
    val sourceRef: String,
    val targetRef: String,
    val conditionExpression: String? = null,
    var routePoints: List<Pair<Double, Double>> = emptyList(),
    val isDefaultFlow: Boolean = false,
    val isMessageFlow: Boolean = false,
    val isAssociation: Boolean = false,
)
