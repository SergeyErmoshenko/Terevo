package me.terevo.layout

internal object EdgeRouter {

    // How far into the parent-to-child gap the horizontal bus sits, as a fraction of that gap's
    // height, measured down from the parents. Kept small so the bracket reads as tight to the
    // parents rather than floating in the middle of open space.
    private const val BUS_OFFSET_RATIO: Double = 0.25

    // Waypoints let a Parentage edge that spans more than one generation (see GenerationAssigner's
    // partner-alignment relaxation) bend through the intermediate rows it actually crosses, instead
    // of jumping straight from parent to child through whatever unrelated nodes sit in between.
    // Keyed by (parent, child) - not by the edge object - so routeUnion can look a chain up by
    // either parent regardless of the edge's `biological` flag.
    fun route(
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>> = emptyMap(),
    ): List<EdgePath> = buildList {
        // A couple's shared children are routed as one bracket hanging off the couple (see
        // routeUnion), replacing their individual parentage lines below. Couples without shared
        // children instead get a direct connector between them.
        val brackets = mutableMapOf<Pair<NodeId, NodeId>, List<Point>>()
        for (union in graph.unions) {
            routeUnion(union, graph, nodes, waypoints, brackets)?.let(::add)
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
                    val chain = waypoints[edge.parent to edge.child].orEmpty()
                    routeParentage(edge, graph, nodes, chain)?.let(::add)
                }
            }
        }
    }

    private fun routeUnion(
        union: LayoutEdge.Union,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
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

        val parentsBottom = maxOf(first.bottom, second.bottom)
        for ((childId, childRect) in sharedChildren) {
            // The bus sits close under the parents rather than at the midpoint of the gap, so it
            // reads as a tight bracket hanging off the couple instead of a line that sags toward
            // the middle of open space - independent of how far away the next generation happens
            // to be.
            val busY = parentsBottom + (childRect.top - parentsBottom) * BUS_OFFSET_RATIO
            // Each parent gets its own stem down to the shared bus, exactly like a plain
            // (non-union) multi-parent Parentage edge - so the bar between the parents sits at the
            // bus, not flush against the bottom of either parent's own card, and both stems
            // converge on the same point into the child regardless of which parent is taller.
            for ((parentId, parentRect) in listOf(union.first to first, union.second to second)) {
                val chain = waypoints[parentId to childId].orEmpty()
                val segments = if (chain.isEmpty()) {
                    listOf(
                        Point(parentRect.centerX, parentRect.bottom),
                        Point(parentRect.centerX, busY),
                        Point(childRect.centerX, busY),
                        childRect.topCenter,
                    )
                } else {
                    buildList {
                        add(Point(parentRect.centerX, parentRect.bottom))
                        var current = Point(parentRect.centerX, parentRect.bottom)
                        for (waypoint in chain) {
                            add(Point(current.x, waypoint.y))
                            add(waypoint)
                            current = waypoint
                        }
                        add(Point(current.x, childRect.top))
                        add(childRect.topCenter)
                    }
                }
                brackets[parentId to childId] = segments
            }
        }
        return null
    }

    private fun routeParentage(
        edge: LayoutEdge.Parentage,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: List<Point>,
    ): EdgePath? {
        val child = nodes[edge.child] ?: return null
        val parent = nodes[edge.parent] ?: return null
        val parentIds = graph.parents(edge.child)
        val parentBottom = parentIds.mapNotNull(nodes::get).maxOf { it.bottom }
        // Every parent's stem converges on the child's own center via a shared bus, regardless of
        // how many parents there are or whether they're recorded as a Union — each parent's line
        // shares the same final segment into the child, rendering as one clean bracket. When the
        // edge spans more than one generation, it bends through each intermediate waypoint instead
        // of jumping straight to the child's row.
        val segments = if (waypoints.isEmpty()) {
            val busY = parentBottom + (child.top - parentBottom) * BUS_OFFSET_RATIO
            listOf(
                Point(parent.centerX, parent.bottom),
                Point(parent.centerX, busY),
                Point(child.centerX, busY),
                child.topCenter,
            )
        } else {
            buildList {
                add(Point(parent.centerX, parent.bottom))
                var current = Point(parent.centerX, parent.bottom)
                for (waypoint in waypoints) {
                    add(Point(current.x, waypoint.y))
                    add(waypoint)
                    current = waypoint
                }
                add(Point(current.x, child.top))
                add(child.topCenter)
            }
        }
        return EdgePath(
            edge = edge,
            segments = segments,
            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
        )
    }
}
