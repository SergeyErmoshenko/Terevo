package me.terevo.layout

internal object EdgeRouter {

    // How far into the parent-to-child gap the horizontal bus sits, as a fraction of that gap's
    // height, measured down from the parents. Kept below half so the bracket reads as hanging off
    // the parents rather than floating in the middle of open space.
    private const val BUS_OFFSET_RATIO: Double = 0.34

    // The bus never runs closer than this to either row of cards. The ratio alone put the bar
    // almost against the card edges whenever two generations sat close together, so the horizontal
    // run visually merged with the card borders; this keeps a readable band of clear space on both
    // sides. Clamped against the gap's own height below, so a genuinely tight gap still routes.
    private const val BUS_MIN_CLEARANCE: Double = 28.0

    // Coordinates come out of relaxation as floating point, so a line meant to be vertical can end
    // up spanning x=163.99999999 to x=164.00000001 - a hair off true vertical, which renders as a
    // faintly slanted or blurred stroke instead of a crisp one. Snapping every routed point to
    // whole pixels makes equal coordinates exactly equal, so vertical runs are actually vertical.
    private fun Point.snapped(): Point = Point(kotlin.math.round(x), kotlin.math.round(y))

    private fun busBetween(parentsBottom: Double, childTop: Double): Double {
        val gap = childTop - parentsBottom
        if (gap <= 0.0) return parentsBottom
        val clearance = minOf(BUS_MIN_CLEARANCE, gap / 2.0)
        return parentsBottom + (gap * BUS_OFFSET_RATIO).coerceIn(clearance, gap - clearance)
    }

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
            val busY = busBetween(parentsBottom, childRect.top)
            for ((parentId, parentRect) in listOf(union.first to first, union.second to second)) {
                val chain = waypoints[parentId to childId].orEmpty()
                val segments = if (chain.isEmpty()) {
                    // Both parents share the same bus height and the same final drop into the
                    // child, so the bracket reads as one shape tied to each parent's own card.
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
                brackets[parentId to childId] = segments.map { it.snapped() }
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
            val busY = busBetween(parentBottom, child.top)
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
            segments = segments.map { it.snapped() },
            style = if (edge.biological) EdgeStyle.BIOLOGICAL else EdgeStyle.NON_BIOLOGICAL,
        )
    }
}
