package me.terevo.layout

import kotlin.math.abs

private const val STRAIGHTEN_EPSILON: Double = 0.5

internal data class Group(
    val nodes: List<NodeId>,
    val order: String,
    val generation: Int,
    // A synthetic single-node placeholder standing in for one intermediate generation of a
    // Parentage edge that spans more than one generation (see WalkerLayoutEngine.placeComponent).
    // It never has a real size and never produces a visible node in the final Layout.
    val isDummy: Boolean = false,
)

internal fun groupWidth(group: Group, metrics: NodeMetrics, spacing: Double): Double =
    if (group.isDummy) 0.0 else group.nodes.sumOf { metrics.sizeOf(it).width } + spacing * (group.nodes.size - 1)

internal fun nodeCenterX(
    node: NodeId,
    group: Group,
    centers: Map<Group, Double>,
    widths: Map<Group, Double>,
    metrics: NodeMetrics,
    spacing: Double,
): Double {
    var left = centers.getValue(group) - widths.getValue(group) / 2.0
    for (id in group.nodes) {
        val width = metrics.sizeOf(id).width
        if (id == node) return left + width / 2.0
        left += width + spacing
    }
    error("$node is not a member of $group")
}

// Resolves a row of desired centers into non-overlapping ones without changing `order`: a
// left-to-right pass and a right-to-left pass each independently satisfy the spacing constraint
// (as a monotone sequence), so their average does too - the same principle Brandes-Köpf alignment
// relies on to avoid a whole extra cleanup pass.
internal fun resolveOverlaps(
    order: List<Group>,
    desired: Map<Group, Double>,
    widths: Map<Group, Double>,
    spacing: (Group, Group) -> Double,
): Map<Group, Double> {
    if (order.isEmpty()) return emptyMap()
    val left = mutableMapOf<Group, Double>()
    var previous: Group? = null
    for (group in order) {
        val minAllowed = previous?.let {
            left.getValue(it) + widths.getValue(it) / 2.0 + spacing(it, group) + widths.getValue(group) / 2.0
        }
        left[group] = if (minAllowed == null) desired.getValue(group) else maxOf(desired.getValue(group), minAllowed)
        previous = group
    }
    val right = mutableMapOf<Group, Double>()
    var next: Group? = null
    for (group in order.asReversed()) {
        val maxAllowed = next?.let {
            right.getValue(it) - widths.getValue(it) / 2.0 - spacing(group, it) - widths.getValue(group) / 2.0
        }
        right[group] = if (maxAllowed == null) desired.getValue(group) else minOf(desired.getValue(group), maxAllowed)
        next = group
    }
    return order.associateWith { (left.getValue(it) + right.getValue(it)) / 2.0 }
}

// Seeds the center with the most important item, then greedily assigns each remaining item
// (widest first) to whichever side currently carries less total width - keeping the centered
// item centered while the accumulated width stays balanced left/right, regardless of how
// uneven the branch widths are, instead of just alternating by rank.
//
// `priority` picks what goes in the middle (higher wins, ties broken by width then `order`).
// Callers that have no reason to prefer one item over another leave it at its default and get
// the widest item centered, as before.
internal fun <T> balancedOrder(
    items: List<T>,
    width: (T) -> Double,
    priority: (T) -> Int = { 0 },
    order: (T) -> String,
): List<T> {
    val sorted = items.sortedWith(
        compareByDescending<T>(priority).thenByDescending(width).thenBy(order),
    )
    if (sorted.isEmpty()) return sorted
    val arranged = ArrayDeque<T>()
    arranged.addLast(sorted.first())
    var leftWidth = 0.0
    var rightWidth = 0.0
    // Remaining items are placed widest-first so the two sides stay balanced; the centered item's
    // own priority must not leak into that ordering.
    for (item in sorted.drop(1).sortedWith(compareByDescending<T>(width).thenBy(order))) {
        if (leftWidth < rightWidth) {
            arranged.addFirst(item)
            leftWidth += width(item)
        } else {
            arranged.addLast(item)
            rightWidth += width(item)
        }
    }
    return arranged.toList()
}

class WalkerLayoutEngine : LayoutEngine {

    override fun layout(request: LayoutRequest): Layout {
        if (request.graph.nodes.isEmpty()) return Layout.EMPTY

        val assignment = GenerationAssigner.assign(request.graph)
        val visible = visibleNodes(request, assignment.generations)
        if (visible.isEmpty()) return Layout.EMPTY

        val placedComponents = assignment.components.mapNotNull { component ->
            val componentNodes = component.filter { it in visible }
            if (componentNodes.isEmpty()) return@mapNotNull null
            placeComponent(request, assignment.generations, componentNodes)
        }
        val orderedComponents =
            balancedOrder(placedComponents, { Rect.enclosing(it.nodes.values).width }, order = { it.order })

        val nodes = mutableMapOf<NodeId, Rect>()
        val waypoints = mutableMapOf<Pair<NodeId, NodeId>, List<Point>>()
        var componentLeft = 0.0
        for (placement in orderedComponents) {
            val componentBounds = Rect.enclosing(placement.nodes.values)
            val dx = componentLeft - componentBounds.left
            placement.nodes.forEach { (id, rect) -> nodes[id] = rect.translated(dx, 0.0) }
            placement.waypoints.forEach { (edge, points) -> waypoints[edge] = points.map { Point(it.x + dx, it.y) } }
            componentLeft += componentBounds.width + request.options.subtreeSpacing
        }

        return Layout(
            nodes = nodes,
            edges = EdgeRouter.route(request.graph, nodes, waypoints),
            generations = assignment.generations.filterKeys { it in visible },
            bounds = Rect.enclosing(nodes.values),
        )
    }

    // Layered-graph (Sugiyama-style) placement: generations are already assigned, so within each
    // generation the left-to-right order is decided from *every* parent/child group-adjacency at
    // once (LayerOrdering), then every group's x-coordinate is pulled toward the average position
    // of *all* its real neighbors, not just a single arbitrarily-chosen "primary" parent
    // (CoordinateAssignment). This is what keeps second marriages, cousins and in-laws close to
    // where they actually belong instead of drawing a long edge across the whole canvas.
    private fun placeComponent(
        request: LayoutRequest,
        generations: Map<NodeId, Int>,
        component: List<NodeId>,
    ): PlacedComponent {
        val graph = request.graph
        val options = request.options
        val realGroups = spouseGroups(graph, generations, component)
        val groupByNode = realGroups.flatMap { group -> group.nodes.map { it to group } }.toMap().toMutableMap()

        // A Parentage edge spanning more than one generation (see GenerationAssigner's
        // partner-alignment relaxation) gets a chain of synthetic single-node groups, one per
        // intermediate generation, so ordering and coordinate assignment see it as a real chain of
        // adjacent-generation hops - occupying space and positioned like any other node - instead
        // of an edge invisible to both until it's drawn straight across whatever sits in between.
        val dummyChains = mutableMapOf<Pair<NodeId, NodeId>, List<Group>>()
        for (edge in graph.parentages) {
            if (edge.parent !in groupByNode || edge.child !in groupByNode) continue
            val parentGeneration = generations.getValue(edge.parent)
            val childGeneration = generations.getValue(edge.child)
            if (childGeneration - parentGeneration <= 1) continue
            val chain = (parentGeneration + 1 until childGeneration).map { generation ->
                val dummyId = NodeId("~dummy~${edge.parent}~${edge.child}~$generation~")
                Group(
                    nodes = listOf(dummyId),
                    order = "${graph.orderOf(edge.parent)} ${graph.orderOf(edge.child)}",
                    generation = generation,
                    isDummy = true
                )
                    .also { groupByNode[dummyId] = it }
            }
            dummyChains[edge.parent to edge.child] = chain
        }

        val groups = realGroups + dummyChains.values.flatten()
        val widths = groups.associateWith { groupWidth(it, request.metrics, options.spouseSpacing) }

        val parentGroupsOf = groups.associateWith { mutableListOf<Group>() }
        val childGroupsOf = groups.associateWith { mutableListOf<Group>() }
        val nodeParentsOf = mutableMapOf<NodeId, MutableList<NodeId>>()
        val nodeChildrenOf = mutableMapOf<NodeId, MutableList<NodeId>>()
        fun link(parent: NodeId, child: NodeId) {
            nodeChildrenOf.getOrPut(parent) { mutableListOf() }.add(child)
            nodeParentsOf.getOrPut(child) { mutableListOf() }.add(parent)
            val parentGroup = groupByNode.getValue(parent)
            val childGroup = groupByNode.getValue(child)
            parentGroupsOf.getValue(childGroup).let { if (parentGroup !in it) it.add(parentGroup) }
            childGroupsOf.getValue(parentGroup).let { if (childGroup !in it) it.add(childGroup) }
        }
        for (edge in graph.parentages) {
            if (edge.parent !in groupByNode || edge.child !in groupByNode) continue
            val chain = dummyChains[edge.parent to edge.child]
            if (chain == null) {
                link(edge.parent, edge.child)
            } else {
                var previous = edge.parent
                for (dummy in chain) {
                    val dummyId = dummy.nodes.single()
                    link(previous, dummyId)
                    previous = dummyId
                }
                link(previous, edge.child)
            }
        }

        val (order, cluster) = LayerOrdering.order(
            groups, parentGroupsOf, childGroupsOf, widths, options.siblingSpacing,
            nodeParentsOf, nodeChildrenOf, groupByNode,
        )
        val centers = CoordinateAssignment.assign(
            order = order,
            widths = widths,
            groupOf = groupByNode,
            nodeParentsOf = nodeParentsOf,
            nodeChildrenOf = nodeChildrenOf,
            metrics = request.metrics,
            siblingSpacing = options.siblingSpacing,
            spouseSpacing = options.spouseSpacing,
            cluster = cluster,
            clusterSpacing = options.clusterSpacing,
        )

        // Every generation that holds any group - real or dummy - needs a row top, even one with
        // no real component member of its own (a purely-transit row for a long edge's chain), so
        // it falls back to the default node height instead of failing to look up a row it's not
        // aware of.
        val generationHeights = groups.map { it.generation }.distinct().associateWith { generation ->
            component.filter { generations.getValue(it) == generation }
                .maxOfOrNull { request.metrics.sizeOf(it).height } ?: request.metrics.defaultSize.height
        }
        val generationTops = mutableMapOf<Int, Double>()
        var top = 0.0
        for (generation in generationHeights.keys.sorted()) {
            generationTops[generation] = top
            top += generationHeights.getValue(generation) + options.generationSpacing
        }

        // Which spouse sits on which side of the couple box is decided here rather than in
        // spouseGroups, because it depends on where each member's own family actually ended up -
        // information that only exists once coordinates are assigned. Ordering members by name (the
        // order spouseGroups produces) put a spouse on the side facing AWAY from their own parents,
        // so their parentage edge had to reach back across their partner's box, crossing whatever
        // ran between. Confirmed against a real family tree: a wife sat left of her husband while
        // her parents and four siblings were off to the right, so her edge crossed both her husband
        // and his own descending line.
        //
        // Members with no parents of their own keep their relative order and stay where they are;
        // only a member whose parents sit on a definite side gets pulled to that side.
        val orientedMembers = realGroups.associateWith { group ->
            if (group.nodes.size <= 1) {
                group.nodes
            } else {
                val groupCenter = centers.getValue(group)
                // Negative pulls a member left, positive right, null leaves it in place.
                val pull = group.nodes.associateWith { node ->
                    val parentCenters = nodeParentsOf[node].orEmpty()
                        .mapNotNull { groupByNode[it] }
                        .filter { it != group }
                        .mapNotNull(centers::get)
                    if (parentCenters.isEmpty()) null else parentCenters.average() - groupCenter
                }
                orderedKeepingMarriedPartnersAdjacent(group, graph, pull)
            }
        }

        val placedNodes = buildMap {
            for (group in realGroups) {
                val groupWidth = widths.getValue(group)
                var left = centers.getValue(group) - groupWidth / 2.0
                for (id in orientedMembers.getValue(group)) {
                    val size = request.metrics.sizeOf(id)
                    put(id, Rect(left, generationTops.getValue(generations.getValue(id)), size.width, size.height))
                    left += size.width + options.spouseSpacing
                }
            }
        }

        val nodes = straightenLineageTops(
            placedNodes, realGroups, orientedMembers, centers, groupByNode,
            nodeParentsOf, nodeChildrenOf, options, cluster,
        )

        val waypoints = dummyChains.mapValues { (_, chain) ->
            chain.map { dummy ->
                Point(
                    centers.getValue(dummy),
                    generationTops.getValue(dummy.generation) + generationHeights.getValue(dummy.generation) / 2.0,
                )
            }
        }

        return PlacedComponent(nodes, waypoints, component.minOf(graph::orderOf))
    }

    private fun straightenLineageTops(
        placed: Map<NodeId, Rect>,
        realGroups: List<Group>,
        orientedMembers: Map<Group, List<NodeId>>,
        centers: Map<Group, Double>,
        groupByNode: Map<NodeId, Group>,
        nodeParentsOf: Map<NodeId, List<NodeId>>,
        nodeChildrenOf: Map<NodeId, List<NodeId>>,
        options: LayoutOptions,
        cluster: Map<Group, Group>,
    ): Map<NodeId, Rect> {
        val moved = placed.toMutableMap()

        fun requiredGap(left: Group, right: Group): Double =
            if ((cluster[left] ?: left) == (cluster[right] ?: right)) {
                options.siblingSpacing
            } else {
                options.siblingSpacing + options.clusterSpacing
            }

        fun currentLeftEdge(group: Group): Double =
            orientedMembers.getValue(group).minOf { moved.getValue(it).left }

        fun currentRightEdge(group: Group): Double =
            orientedMembers.getValue(group).maxOf { moved.getValue(it).right }

        fun onlyChildOf(group: Group): NodeId? {
            val children = group.nodes.flatMap { nodeChildrenOf[it].orEmpty() }.distinct()
            val child = children.singleOrNull() ?: return null
            if (nodeParentsOf[child].orEmpty().size > 1) return null
            if (groupByNode[child] == group) return null
            return child.takeIf { it in moved }
        }

        fun isLineageTop(group: Group): Boolean =
            group.nodes.none { nodeParentsOf[it].orEmpty().isNotEmpty() }

        fun freeSpaceToward(row: List<Group>, index: Int, group: Group, towardRight: Boolean): Double {
            val neighbour = row.getOrNull(if (towardRight) index + 1 else index - 1)
                ?: return Double.POSITIVE_INFINITY
            return if (towardRight) {
                currentLeftEdge(neighbour) - currentRightEdge(group) - requiredGap(group, neighbour)
            } else {
                currentLeftEdge(group) - currentRightEdge(neighbour) - requiredGap(neighbour, group)
            }
        }

        fun slide(group: Group, by: Double) {
            for (id in orientedMembers.getValue(group)) {
                val rect = moved[id] ?: continue
                moved[id] = Rect(rect.left + by, rect.top, rect.width, rect.height)
            }
        }

        val rowsByTop = realGroups
            .filter { group -> group.nodes.any { it in moved } }
            .groupBy { group -> moved.getValue(group.nodes.first { it in moved }).top }

        for ((_, groupsInRow) in rowsByTop) {
            val row = groupsInRow.sortedBy { centers.getValue(it) }
            for ((index, group) in row.withIndex()) {
                if (!isLineageTop(group)) continue
                val childId = onlyChildOf(group) ?: continue
                val parentId = group.nodes.firstOrNull { childId in nodeChildrenOf[it].orEmpty() } ?: continue
                val offsetToChild = moved.getValue(childId).centerX - moved.getValue(parentId).centerX
                if (abs(offsetToChild) < STRAIGHTEN_EPSILON) continue

                val room = freeSpaceToward(row, index, group, towardRight = offsetToChild > 0)
                if (room <= STRAIGHTEN_EPSILON) continue
                slide(group, offsetToChild.coerceIn(-room, room))
            }
        }
        return moved
    }

    private fun marriageUnitsOf(group: Group, graph: TreeGraph): List<List<NodeId>> {
        val unitRepresentative = group.nodes.associateWithTo(mutableMapOf()) { it }
        fun representativeOf(node: NodeId): NodeId {
            var current = node
            while (unitRepresentative.getValue(current) != current) current = unitRepresentative.getValue(current)
            return current
        }
        for (node in group.nodes) {
            for (partner in graph.partners(node)) {
                if (partner !in unitRepresentative) continue
                val nodeUnit = representativeOf(node)
                val partnerUnit = representativeOf(partner)
                if (nodeUnit != partnerUnit) unitRepresentative[partnerUnit] = nodeUnit
            }
        }
        return group.nodes.groupBy(::representativeOf).values.toList()
    }

    private fun orderedKeepingMarriedPartnersAdjacent(
        group: Group,
        graph: TreeGraph,
        pull: Map<NodeId, Double?>,
    ): List<NodeId> {
        val existingPosition = group.nodes.withIndex().associate { (index, node) -> node to index }
        fun averagePull(members: List<NodeId>): Double =
            members.mapNotNull { pull[it] }.takeIf { it.isNotEmpty() }?.average() ?: 0.0

        return marriageUnitsOf(group, graph)
            .sortedWith(
                compareBy(
                    { averagePull(it) },
                    { members -> members.minOf { existingPosition.getValue(it) } },
                ),
            )
            .flatMap { members ->
                members.sortedWith(compareBy({ pull[it] ?: 0.0 }, { existingPosition.getValue(it) }))
            }
    }

    private fun spouseGroups(
        graph: TreeGraph,
        generations: Map<NodeId, Int>,
        component: List<NodeId>,
    ): List<Group> {
        val remaining = component.toMutableSet()
        val groups = mutableListOf<Group>()
        while (remaining.isNotEmpty()) {
            val first = remaining.minBy(graph::orderOf)
            val generation = generations.getValue(first)
            val nodes = mutableListOf<NodeId>()
            val queue = ArrayDeque(listOf(first))
            remaining.remove(first)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                nodes += current
                val linked = graph.partners(current) + coParentsOf(graph, current)
                for (partner in linked.distinct()) {
                    if (generations[partner] == generation && remaining.remove(partner)) queue.addLast(partner)
                }
            }
            nodes.sortBy(graph::orderOf)
            groups += Group(nodes, nodes.joinToString(" ") { graph.orderOf(it) }, generation)
        }
        return groups.sortedBy { it.order }
    }

    private fun coParentsOf(graph: TreeGraph, node: NodeId): List<NodeId> =
        graph.children(node).flatMap(graph::parents).filter { it != node }

    private fun visibleNodes(request: LayoutRequest, generations: Map<NodeId, Int>): Set<NodeId> {
        if (request.options.mode == LayoutMode.WHOLE_FAMILY) return request.graph.sortedNodeIds().toSet()
        val root = request.options.root ?: return request.graph.sortedNodeIds().toSet()
        if (!request.graph.contains(root)) return emptySet()
        val depth = request.options.depth
        val visible = mutableSetOf(root)
        val queue = ArrayDeque(listOf(root to 0))
        while (queue.isNotEmpty()) {
            val (current, distance) = queue.removeFirst()
            if (distance >= depth) continue
            val next = when (request.options.mode) {
                LayoutMode.ANCESTORS -> request.graph.parents(current)
                LayoutMode.DESCENDANTS -> request.graph.children(current)
                LayoutMode.BOTH -> request.graph.parents(current) + request.graph.children(current) + request.graph.partners(
                    current
                )

                LayoutMode.WHOLE_FAMILY -> request.graph.parents(current) + request.graph.children(current) + request.graph.partners(
                    current
                )
            }
            for (id in next) {
                if (visible.add(id)) queue.addLast(id to distance + 1)
            }
        }
        return visible.filterTo(mutableSetOf()) { it in generations }
    }

    private data class PlacedComponent(
        val nodes: Map<NodeId, Rect>,
        val waypoints: Map<Pair<NodeId, NodeId>, List<Point>>,
        val order: String,
    )
}
