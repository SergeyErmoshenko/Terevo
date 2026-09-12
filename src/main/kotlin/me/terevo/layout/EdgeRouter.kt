package me.terevo.layout

internal object EdgeRouter {

    fun route(graph: TreeGraph, nodes: Map<NodeId, Rect>): List<EdgePath> = buildList {
        for (edge in graph.edges) {
            if (edge is LayoutEdge.Parentage) {
                routeParentage(edge, graph, nodes)?.let(::add)
            }
        }
    }

    private fun routeParentage(
        edge: LayoutEdge.Parentage,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
    ): EdgePath? {
        val child = nodes[edge.child] ?: return null
        val parent = nodes[edge.parent] ?: return null
        val parentBottom = graph.parents(edge.child).mapNotNull(nodes::get).maxOf { it.bottom }
        val busY = parentBottom + (child.top - parentBottom) / 2.0
        return EdgePath(
            edge = edge,
            segments = listOf(
                Point(parent.centerX, parent.bottom),
                Point(parent.centerX, busY),
                Point(child.centerX, busY),
                child.topCenter,
            ),
            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
        )
    }
}
