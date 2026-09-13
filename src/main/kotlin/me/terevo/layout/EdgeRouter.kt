package me.terevo.layout

internal object EdgeRouter {

    fun route(graph: TreeGraph, nodes: Map<NodeId, Rect>): List<EdgePath> = buildList {
        // A couple's shared children are routed as one bracket hanging off the couple (see
        // routeUnion), replacing their individual parentage lines below. Couples without shared
        // children instead get a direct connector between them.
        val brackets = mutableMapOf<Pair<NodeId, NodeId>, List<Point>>()
        for (union in graph.unions) {
            routeUnion(union, graph, nodes, brackets)?.let(::add)
        }
        for (edge in graph.edges) {
            if (edge is LayoutEdge.Parentage) {
                val bracket = brackets[edge.parent to edge.child]
                if (bracket != null) {
                    add(
                        EdgePath(
                            edge = edge,
                            segments = bracket,
                            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
                        ),
                    )
                } else {
                    routeParentage(edge, graph, nodes)?.let(::add)
                }
            }
        }
    }

    private fun routeUnion(
        union: LayoutEdge.Union,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        brackets: MutableMap<Pair<NodeId, NodeId>, List<Point>>,
    ): EdgePath? {
        val first = nodes[union.first] ?: return null
        val second = nodes[union.second] ?: return null
        val sharedChildren = graph.children(union.first)
            .filter { it in graph.children(union.second) }
            .mapNotNull { id -> nodes[id]?.let { id to it } }

        if (sharedChildren.isEmpty()) {
            val (left, right) = if (first.centerX <= second.centerX) first to second else second to first
            return EdgePath(
                edge = union,
                segments = listOf(Point(left.right, left.centerY), Point(right.left, right.centerY)),
                style = if (union.dissolved) EdgeStyle.DISSOLVED_MARRIAGE else EdgeStyle.MARRIAGE,
            )
        }

        val midX = (first.centerX + second.centerX) / 2.0
        val parentsBottom = maxOf(first.bottom, second.bottom)
        val childTop = sharedChildren.minOf { it.second.top }
        val busY = parentsBottom + (childTop - parentsBottom) / 2.0
        for ((childId, childRect) in sharedChildren) {
            val segments = listOf(
                Point(midX, parentsBottom),
                Point(midX, busY),
                Point(childRect.centerX, busY),
                childRect.topCenter,
            )
            brackets[union.first to childId] = segments
            brackets[union.second to childId] = segments
        }
        return null
    }

    private fun routeParentage(
        edge: LayoutEdge.Parentage,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
    ): EdgePath? {
        val child = nodes[edge.child] ?: return null
        val parent = nodes[edge.parent] ?: return null
        val parentIds = graph.parents(edge.child)
        val parentBottom = parentIds.mapNotNull(nodes::get).maxOf { it.bottom }
        val busY = parentBottom + (child.top - parentBottom) / 2.0
        val segments = if (parentIds.size > 1) {
            // Land each co-parent's line on its own point along the child's top edge instead of a
            // shared center, so the two independent lines never read as one bar directly joining
            // the parents themselves.
            val ordered = parentIds.sortedBy { nodes[it]?.centerX ?: 0.0 }
            val index = ordered.indexOf(edge.parent)
            val approachX = child.left + child.width * (index + 1) / (ordered.size + 1)
            listOf(
                Point(parent.centerX, parent.bottom),
                Point(parent.centerX, busY),
                Point(approachX, busY),
                Point(approachX, child.top),
            )
        } else {
            listOf(
                Point(parent.centerX, parent.bottom),
                Point(parent.centerX, busY),
                Point(child.centerX, busY),
                child.topCenter,
            )
        }
        return EdgePath(
            edge = edge,
            segments = segments,
            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
        )
    }
}
