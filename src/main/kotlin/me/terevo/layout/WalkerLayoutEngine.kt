package me.terevo.layout

class WalkerLayoutEngine : LayoutEngine {

    override fun layout(request: LayoutRequest): Layout {
        if (request.graph.nodes.isEmpty()) return Layout.EMPTY

        val assignment = GenerationAssigner.assign(request.graph)
        val visible = visibleNodes(request, assignment.generations)
        if (visible.isEmpty()) return Layout.EMPTY

        val placedComponents = assignment.components.mapNotNull { component ->
            val componentNodes = component.filter { it in visible }
            if (componentNodes.isEmpty()) return@mapNotNull null
            val placed = placeComponent(request, assignment.generations, componentNodes)
            PlacedComponent(placed, componentNodes.minOf(request.graph::orderOf))
        }
        val orderedComponents = balancedOrder(placedComponents, { Rect.enclosing(it.nodes.values).width }, { it.order })

        val nodes = mutableMapOf<NodeId, Rect>()
        var componentLeft = 0.0
        for (placement in orderedComponents) {
            val componentBounds = Rect.enclosing(placement.nodes.values)
            val dx = componentLeft - componentBounds.left
            placement.nodes.forEach { (id, rect) -> nodes[id] = rect.translated(dx, 0.0) }
            componentLeft += componentBounds.width + request.options.subtreeSpacing
        }

        return Layout(
            nodes = nodes,
            edges = EdgeRouter.route(request.graph, nodes),
            generations = assignment.generations.filterKeys { it in visible },
            bounds = Rect.enclosing(nodes.values),
        )
    }

    private fun placeComponent(
        request: LayoutRequest,
        generations: Map<NodeId, Int>,
        component: List<NodeId>,
    ): Map<NodeId, Rect> {
        val graph = request.graph
        val options = request.options
        val componentSet = component.toSet()
        val groups = spouseGroups(graph, generations, component)
        val groupByNode = groups.flatMap { group -> group.nodes.map { it to group } }.toMap()
        val rawChildren = groups.associateWith { group ->
            group.nodes
                .flatMap(graph::children)
                .filter { it in componentSet }
                .mapNotNull(groupByNode::get)
                .distinct()
        }
        // A group can be listed as a "child" by more than one parent group (co-parents that
        // spouseGroups() couldn't merge into one compound group, or separate biological/adoptive
        // parents). Only one parent may own it for placement, otherwise its bounds/center get
        // overwritten by whichever parent's traversal runs last, corrupting both subtrees.
        val primaryParent = mutableMapOf<Group, Group>()
        for (parent in groups) {
            for (child in rawChildren.getValue(parent)) {
                val current = primaryParent[child]
                if (current == null || parent.order < current.order) primaryParent[child] = parent
            }
        }
        val children = groups.associateWith { group ->
            rawChildren.getValue(group).filter { primaryParent[it] == group }.sortedBy { it.order }
        }
        val parentGroups = primaryParent.keys
        val roots = groups.filter { it !in parentGroups }.sortedBy { it.order }
        // A root that had children in the raw graph but lost every one of them to another
        // group's primary claim (e.g. a couple whose two members each bring their own parents)
        // isn't a genuine independent subtree — it has nothing of its own to anchor on and would
        // otherwise get dumped far away by the plain root sequencing below. It belongs directly
        // above wherever its real child ended up instead.
        val (trueRoots, orphanedRoots) = roots.partition {
            children.getValue(it).isNotEmpty() || rawChildren.getValue(it).isEmpty()
        }
        val centers = mutableMapOf<Group, Double>()
        val widths = groups.associateWith { groupWidth(it, request.metrics, options.spouseSpacing) }
        // A parent group can be linked to only a subset of a compound child group's members (e.g.
        // a couple where each spouse keeps their own separate parent) — record which specific
        // nodes each parent-child edge actually touches, so placement can anchor on them instead
        // of the child group's overall midpoint.
        val linkedChildNodes = children.entries.flatMap { (parent, kids) ->
            kids.map { child ->
                (parent to child) to parent.nodes.flatMap(graph::children).filter { it in child.nodes }
            }
        }.toMap()

        val rootBounds = trueRoots.associateWith {
            placeSubtree(it, children, widths, options, centers, linkedChildNodes, request.metrics)
        }
        val orderedRoots = balancedOrder(trueRoots, { rootBounds.getValue(it).width }, { it.order })
        var nextRootLeft = 0.0
        for (root in orderedRoots) {
            val subtree = rootBounds.getValue(root)
            shiftSubtree(root, children, centers, nextRootLeft - subtree.left)
            nextRootLeft += subtree.width + options.subtreeSpacing
        }
        // Anchor each orphaned root on the actual person(s) it descends to, not the whole target
        // group's center — e.g. a couple's two members each keep their own parent, so that parent
        // belongs directly above its own spouse's slot within the couple, not the couple's midpoint
        // (which the couple's primary parent, if any, already occupies).
        val orphanedAnchors = orphanedRoots.associateWith { root ->
            root.nodes.flatMap(graph::children).filter { it in componentSet }
                .map { childNode ->
                    nodeCenterX(
                        childNode,
                        groupByNode.getValue(childNode),
                        centers,
                        widths,
                        request.metrics,
                        options.spouseSpacing
                    )
                }
                .average()
        }
        // Orphaned roots that still land on the exact same anchor (e.g. two claims on the same
        // person) must not collapse onto one coordinate — lay them out side by side around it.
        for ((anchorX, group) in orphanedRoots.groupBy(orphanedAnchors::getValue)) {
            val ordered = balancedOrder(group, { widths.getValue(it) }, { it.order })
            var left = 0.0
            val offsets = mutableMapOf<Group, Double>()
            for (item in ordered) {
                offsets[item] = left + widths.getValue(item) / 2.0
                left += widths.getValue(item) + options.siblingSpacing
            }
            val rowWidth = left - options.siblingSpacing
            val rowLeft = anchorX - rowWidth / 2.0
            for (item in ordered) {
                centers[item] = rowLeft + offsets.getValue(item)
            }
        }
        for (group in groups.filterNot { it in centers }.sortedBy { it.order }) {
            centers[group] = nextRootLeft + widths.getValue(group) / 2.0
            nextRootLeft += widths.getValue(group) + options.subtreeSpacing
        }

        val generationHeights = component
            .groupBy { generations.getValue(it) }
            .mapValues { (_, ids) -> ids.maxOf { request.metrics.sizeOf(it).height } }
        val generationTops = mutableMapOf<Int, Double>()
        var top = 0.0
        for (generation in generationHeights.keys.sorted()) {
            generationTops[generation] = top
            top += generationHeights.getValue(generation) + options.generationSpacing
        }

        return buildMap {
            for (group in groups) {
                val groupWidth = widths.getValue(group)
                var left = centers.getValue(group) - groupWidth / 2.0
                for (id in group.nodes) {
                    val size = request.metrics.sizeOf(id)
                    put(id, Rect(left, generationTops.getValue(generations.getValue(id)), size.width, size.height))
                    left += size.width + options.spouseSpacing
                }
            }
        }
    }

    private fun placeSubtree(
        group: Group,
        children: Map<Group, List<Group>>,
        widths: Map<Group, Double>,
        options: LayoutOptions,
        centers: MutableMap<Group, Double>,
        linkedChildNodes: Map<Pair<Group, Group>, List<NodeId>>,
        metrics: NodeMetrics,
    ): Rect {
        val traversal = mutableListOf<Group>()
        val stack = ArrayDeque<Pair<Group, Boolean>>()
        stack.addLast(group to false)
        while (stack.isNotEmpty()) {
            val (current, visited) = stack.removeLast()
            if (visited) {
                traversal += current
            } else {
                stack.addLast(current to true)
                children.getValue(current).asReversed().forEach { stack.addLast(it to false) }
            }
        }

        val bounds = mutableMapOf<Group, Rect>()
        for (current in traversal) {
            val childGroups = children.getValue(current)
            if (childGroups.isEmpty()) {
                centers[current] = widths.getValue(current) / 2.0
                bounds[current] = Rect(0.0, 0.0, widths.getValue(current), 0.0)
                continue
            }

            val orderedChildren = balancedOrder(childGroups, { bounds.getValue(it).width }, { it.order })
            var nextChildLeft = 0.0
            for (child in orderedChildren) {
                val childBounds = bounds.getValue(child)
                shiftSubtree(child, children, centers, nextChildLeft - childBounds.left)
                bounds[child] = childBounds.translated(nextChildLeft - childBounds.left, 0.0)
                nextChildLeft += childBounds.width + options.siblingSpacing
            }
            val childLeft = bounds.getValue(orderedChildren.first()).left
            val childRight = bounds.getValue(orderedChildren.last()).right
            val onlyChild = orderedChildren.singleOrNull()
            val partialLink = onlyChild?.let { linkedChildNodes[current to it] }
                ?.takeIf { it.isNotEmpty() && it.size < onlyChild.nodes.size }
            val center = if (onlyChild != null && partialLink != null) {
                partialLink.map { nodeCenterX(it, onlyChild, centers, widths, metrics, options.spouseSpacing) }
                    .average()
            } else {
                (childLeft + childRight) / 2.0
            }
            centers[current] = center
            val ownLeft = center - widths.getValue(current) / 2.0
            val left = minOf(ownLeft, childLeft)
            val right = maxOf(ownLeft + widths.getValue(current), childRight)
            bounds[current] = Rect(left, 0.0, right - left, 0.0)
        }
        return bounds.getValue(group)
    }

    private fun shiftSubtree(
        root: Group,
        children: Map<Group, List<Group>>,
        centers: MutableMap<Group, Double>,
        dx: Double,
    ) {
        if (dx == 0.0) return
        val stack = ArrayDeque(listOf(root))
        val visited = mutableSetOf<Group>()
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (!visited.add(current)) continue
            centers[current] = centers.getValue(current) + dx
            stack.addAll(children.getValue(current))
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
            groups += Group(nodes, nodes.joinToString("\u0000") { graph.orderOf(it) })
        }
        return groups.sortedBy { it.order }
    }

    private fun coParentsOf(graph: TreeGraph, node: NodeId): List<NodeId> =
        graph.children(node).flatMap(graph::parents).filter { it != node }

    private fun groupWidth(group: Group, metrics: NodeMetrics, spacing: Double): Double =
        group.nodes.sumOf { metrics.sizeOf(it).width } + spacing * (group.nodes.size - 1)

    private fun nodeCenterX(
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

    private data class Group(
        val nodes: List<NodeId>,
        val order: String,
    )

    private data class PlacedComponent(
        val nodes: Map<NodeId, Rect>,
        val order: String,
    )

    // Puts the widest item in the middle and alternates the next-widest to either side, so a
    // handful of large branches don't push a narrow one out to an extreme, off-center edge.
    private fun <T> balancedOrder(items: List<T>, width: (T) -> Double, order: (T) -> String): List<T> {
        val sorted = items.sortedWith(compareByDescending<T>(width).thenBy(order))
        val arranged = ArrayDeque<T>()
        for ((index, item) in sorted.withIndex()) {
            if (index == 0 || index % 2 == 1) arranged.addLast(item) else arranged.addFirst(item)
        }
        return arranged.toList()
    }
}
