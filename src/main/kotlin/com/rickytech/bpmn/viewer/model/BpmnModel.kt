package com.rickytech.bpmn.viewer.model

data class BpmnModel(
    val processId: String,
    val processName: String,
    val nodes: List<BpmnNode>,
    val edges: List<BpmnEdge>,
    val pools: List<BpmnNode> = emptyList(),
    val lanes: List<BpmnNode> = emptyList(),
)
