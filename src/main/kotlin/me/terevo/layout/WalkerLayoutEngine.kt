package me.terevo.layout

class WalkerLayoutEngine : LayoutEngine {

    override fun layout(request: LayoutRequest): Layout {
        if (request.graph.nodes.isEmpty()) return Layout.EMPTY

        val assignment = GenerationAssigner.assign(request.graph)
        val visible = visibleNodes(request, assignment.generations)
        if (visible.isEmpty()) return Layout.EMPTY

        val nodes = mutableMapOf<NodeId, Rect>()
        var componentLeft = 0.0
        for (component in assignment.components) {
            val componentNodes = component.filter { it in visible }
            if (componentNodes.isEmpty()) continue

            val placed = placeComponent(request, assignment.generations, componentNodes)
            val componentBounds = Rect.enclosing(placed.values)
            val dx = componentLeft - componentBounds.left
            placed.forEach { (id, rect) -> nodes[id] = rect.translated(dx, 0.0) }
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
        val children = groups.associateWith { group ->
            group.nodes
                .flatMap(graph::children)
                .filter { it in componentSet }
                .mapNotNull(groupByNode::get)
                .distinct()
                .sortedBy { it.order }
        }
        val parentGroups = children.values.flatten().toSet()
        val roots = groups.filter { it !in parentGroups }.sortedBy { it.order }
        val centers = mutableMapOf<Group, Double>()
        val widths = groups.associateWith { groupWidth(it, request.metrics, options.spouseSpacing) }
        var nextRootLeft = 0.0

        for (root in roots) {
            val subtree = placeSubtree(root, children, widths, options, centers)
            shiftSubtree(root, children, centers, nextRootLeft - subtree.left)
            nextRootLeft += subtree.width + options.subtreeSpacing
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

            var nextChildLeft = 0.0
            for (child in childGroups) {
                val childBounds = bounds.getValue(child)
                shiftSubtree(child, children, centers, nextChildLeft - childBounds.left)
                bounds[child] = childBounds.translated(nextChildLeft - childBounds.left, 0.0)
                nextChildLeft += childBounds.width + options.siblingSpacing
            }
            val childLeft = bounds.getValue(childGroups.first()).left
            val childRight = bounds.getValue(childGroups.last()).right
            val center = (childLeft + childRight) / 2.0
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
                for (partner in graph.partners(current)) {
                    if (generations[partner] == generation && remaining.remove(partner)) queue.addLast(partner)
                }
            }
            nodes.sortBy(graph::orderOf)
            groups += Group(nodes, nodes.joinToString("\u0000") { graph.orderOf(it) })
        }
        return groups.sortedBy { it.order }
    }

    private fun groupWidth(group: Group, metrics: NodeMetrics, spacing: Double): Double =
        group.nodes.sumOf { metrics.sizeOf(it).width } + spacing * (group.nodes.size - 1)

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
                LayoutMode.BOTH -> request.graph.parents(current) + request.graph.children(current) + request.graph.partners(current)
                LayoutMode.WHOLE_FAMILY -> request.graph.parents(current) + request.graph.children(current) + request.graph.partners(current)
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
}
