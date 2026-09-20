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

    private const val CHANNEL_SPACING: Double = 14.0
    private const val CHANNEL_CONFLICT_EPSILON: Double = 0.5

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

    private data class Bus(val parents: Set<NodeId>, val childTop: Double)

    private class BusGeometry(val span: ClosedFloatingPointRange<Double>, val base: Double, val limit: Double) {
        fun spanWidth(): Double = span.endInclusive - span.start

        fun horizontallyOverlaps(other: ClosedFloatingPointRange<Double>): Boolean =
            minOf(span.endInclusive, other.endInclusive) - maxOf(span.start, other.start) >
                CHANNEL_CONFLICT_EPSILON

        fun mergedWith(otherSpan: ClosedFloatingPointRange<Double>, otherBase: Double, otherLimit: Double) =
            BusGeometry(
                minOf(span.start, otherSpan.start)..maxOf(span.endInclusive, otherSpan.endInclusive),
                minOf(base, otherBase),
                minOf(limit, otherLimit),
            )
    }

    fun route(
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>> = emptyMap(),
    ): List<EdgePath> {
        val busY = assignBusChannels(graph, nodes, waypoints)
        return buildList {
            // A couple's shared children are routed as one bracket hanging off the couple (see
            // routeUnion), replacing their individual parentage lines below. Couples without shared
            // children instead get a direct connector between them.
            val brackets = mutableMapOf<Pair<NodeId, NodeId>, List<Point>>()
            for (union in graph.unions) {
                routeUnion(union, graph, nodes, waypoints, brackets, busY)?.let(::add)
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
                        routeParentage(edge, graph, nodes, chain, busY)?.let(::add)
                    }
                }
            }
        }
    }

    private fun busOf(graph: TreeGraph, child: NodeId, childRect: Rect): Bus =
        Bus(graph.parents(child).toSet(), childRect.top)

    private fun isRoutedThroughWaypoints(
        edge: LayoutEdge.Parentage,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
    ): Boolean = waypoints[edge.parent to edge.child]?.isNotEmpty() == true

    private fun busGeometryByBus(
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
    ): Map<Bus, BusGeometry> {
        val geometry = mutableMapOf<Bus, BusGeometry>()
        for (edge in graph.parentages) {
            if (isRoutedThroughWaypoints(edge, waypoints)) continue
            val childRect = nodes[edge.child] ?: continue
            val parentRects = graph.parents(edge.child).mapNotNull(nodes::get)
            if (parentRects.isEmpty()) continue
            val parentRect = nodes[edge.parent] ?: continue

            val bus = busOf(graph, edge.child, childRect)
            val base = busBetween(parentRects.maxOf { it.bottom }, childRect.top)
            val span = minOf(parentRect.centerX, childRect.centerX)..
                maxOf(parentRect.centerX, childRect.centerX)
            geometry[bus] = geometry[bus]?.mergedWith(span, base, childRect.top)
                ?: BusGeometry(span, base, childRect.top)
        }
        return geometry
    }

    private fun widestBarFirst(geometry: Map<Bus, BusGeometry>): List<Bus> =
        geometry.keys.sortedWith(
            compareByDescending<Bus> { geometry.getValue(it).spanWidth() }
                .thenBy { geometry.getValue(it).span.start }
                .thenBy { bus -> bus.parents.map { it.value }.sorted().joinToString(",") }
                .thenBy { it.childTop },
        )

    private fun assignBusChannels(
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
    ): Map<Bus, Double> {
        val geometry = busGeometryByBus(graph, nodes, waypoints)
        val occupiedChannels = mutableListOf<Pair<ClosedFloatingPointRange<Double>, Double>>()
        val channelOfBus = mutableMapOf<Bus, Double>()

        for (bus in widestBarFirst(geometry)) {
            val bar = geometry.getValue(bus)
            var channel = 0
            while (true) {
                val candidate = bar.base + channel * CHANNEL_SPACING
                if (candidate >= bar.limit) {
                    channelOfBus[bus] = bar.base
                    break
                }
                val isFree = occupiedChannels.none { (otherSpan, otherY) ->
                    otherY == candidate && bar.horizontallyOverlaps(otherSpan)
                }
                if (isFree) {
                    channelOfBus[bus] = candidate
                    occupiedChannels += bar.span to candidate
                    break
                }
                channel++
            }
        }
        return channelOfBus
    }

    private fun routeUnion(
        union: LayoutEdge.Union,
        graph: TreeGraph,
        nodes: Map<NodeId, Rect>,
        waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
        brackets: MutableMap<Pair<NodeId, NodeId>, List<Point>>,
        busY: Map<Bus, Double>,
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
            val resolved = busY[busOf(graph, childId, childRect)]
                ?: busBetween(parentsBottom, childRect.top)
            for ((parentId, parentRect) in listOf(union.first to first, union.second to second)) {
                val chain = waypoints[parentId to childId].orEmpty()
                val segments = if (chain.isEmpty()) {
                    // Both parents share the same bus height and the same final drop into the
                    // child, so the bracket reads as one shape tied to each parent's own card.
                    listOf(
                        Point(parentRect.centerX, parentRect.bottom),
                        Point(parentRect.centerX, resolved),
                        Point(childRect.centerX, resolved),
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
        busY: Map<Bus, Double>,
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
            val resolved = busY[busOf(graph, edge.child, child)] ?: busBetween(parentBottom, child.top)
            listOf(
                Point(parent.centerX, parent.bottom),
                Point(parent.centerX, resolved),
                Point(child.centerX, resolved),
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
