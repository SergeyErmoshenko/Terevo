package me.terevo.testing

import me.terevo.layout.LayoutEdge
import me.terevo.layout.LayoutNode
import me.terevo.layout.NodeId
import me.terevo.layout.TreeGraph

const val NODE_WIDTH: Double = 160.0
const val NODE_HEIGHT: Double = 64.0

fun node(id: String, sortKey: String = id): LayoutNode = LayoutNode(NodeId(id), sortKey)

fun nodeId(id: String): NodeId = NodeId(id)

fun parentage(parent: String, child: String, biological: Boolean = true): LayoutEdge.Parentage =
    LayoutEdge.Parentage(NodeId(parent), NodeId(child), biological)

fun union(first: String, second: String, dissolved: Boolean = false): LayoutEdge.Union =
    LayoutEdge.Union(NodeId(first), NodeId(second), dissolved)

fun graphOf(nodes: List<String>, edges: List<LayoutEdge> = emptyList()): TreeGraph =
    TreeGraph.of(nodes.map { node(it) }, edges)
