package me.terevo.layout

internal object EdgeRouter {

    fun route(graph: TreeGraph, nodes: Map<NodeId, Rect>): List<EdgePath> = buildList {
        for (edge in graph.edges) {
            when (edge) {
                is LayoutEdge.Parentage -> routeParentage(edge, graph, nodes)?.let(::add)
                is LayoutEdge.Union -> routeUnion(edge, nodes)?.let(::add)
            }
        }
    }

    private fun routeParentage(
        edge: LayoutEdge.Parentage,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
    ): EdgePath? {
        val child = nodes[edge.child] ?: return null
        val parents = graph.parents(edge.child).mapNotNull(nodes::get)
        if (parents.isEmpty()) return null

        val parentBottom = parents.maxOf { it.bottom }
        val parentCenter = parents.map { it.centerX }.average()
        val busY = parentBottom + (child.top - parentBottom) / 2.0
        return EdgePath(
            edge = edge,
            segments = listOf(
                Point(parentCenter, parentBottom),
                Point(parentCenter, busY),
                Point(child.centerX, busY),
                child.topCenter,
            ),
            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
        )
    }

    private fun routeUnion(edge: LayoutEdge.Union, nodes: Map<NodeId, Rect>): EdgePath? {
        val first = nodes[edge.first] ?: return null
        val second = nodes[edge.second] ?: return null
        val (left, right) = if (first.centerX <= second.centerX) first to second else second to first
        return EdgePath(
            edge = edge,
            segments = listOf(
                Point(left.right, left.centerY),
                Point(right.left, right.centerY),
            ),
            style = if (edge.dissolved) EdgeStyle.DISSOLVED_MARRIAGE else EdgeStyle.MARRIAGE,
        )
    }
}
