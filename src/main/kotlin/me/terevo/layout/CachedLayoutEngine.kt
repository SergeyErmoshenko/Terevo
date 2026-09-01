package me.terevo.layout

class CachedLayoutEngine(
    private val delegate: LayoutEngine = WalkerLayoutEngine(),
    private val capacity: Int = DEFAULT_CAPACITY,
) : LayoutEngine {
    private val cache = object : LinkedHashMap<LayoutOptions, CacheEntry>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutOptions, CacheEntry>): Boolean = size > capacity
    }

    override fun layout(request: LayoutRequest): Layout {
        val cached = cache[request.options]
        if (cached != null) {
            if (cached.matches(request.graph)) {
                if (cached.metrics == request.metrics) return cached.layout
                return store(request, resize(cached.layout, request))
            }
            incrementalLayout(cached, request)?.let { return store(request, it) }
        }
        return store(request, delegate.layout(request))
    }

    fun clear() {
        cache.clear()
    }

    private fun store(request: LayoutRequest, layout: Layout): Layout {
        cache[request.options] = CacheEntry(request.graph, request.metrics, layout)
        return layout
    }

    private fun resize(layout: Layout, request: LayoutRequest): Layout {
        val nodes = layout.nodes.mapValues { (id, rect) ->
            val size = request.metrics.sizeOf(id)
            Rect(rect.left, rect.top, size.width, size.height)
        }
        return layout.copy(
            nodes = nodes,
            edges = EdgeRouter.route(request.graph, nodes),
            bounds = Rect.enclosing(nodes.values),
        )
    }

    private fun incrementalLayout(cached: CacheEntry, request: LayoutRequest): Layout? {
        if (cached.graph.nodes != request.graph.nodes) return null
        val oldEdges = cached.graph.edges.toHashSet()
        val newEdges = request.graph.edges.toHashSet()
        val removed = oldEdges - newEdges
        val added = newEdges - oldEdges
        if (removed.size + added.size > MAX_INCREMENTAL_EDGE_CHANGES) return null

        val affected = changedNodes(removed + added)
        if (affected.isEmpty()) return resize(cached.layout, request)
        val generations = incrementalGenerations(cached.layout.generations, request.graph, added)
        val generationHeight = request.metrics.defaultSize.height
        val nodes = cached.layout.nodes.mapValues { (id, rect) ->
            val size = request.metrics.sizeOf(id)
            val top = generations.getValue(id) * (generationHeight + request.options.generationSpacing)
            Rect(rect.left, top, size.width, size.height)
        }
        return Layout(
            nodes = nodes,
            edges = EdgeRouter.route(request.graph, nodes),
            generations = generations,
            bounds = Rect.enclosing(nodes.values),
        )
    }

    private fun changedNodes(edges: Collection<LayoutEdge>): Set<NodeId> = buildSet {
        for (edge in edges) {
            when (edge) {
                is LayoutEdge.Parentage -> {
                    add(edge.parent)
                    add(edge.child)
                }
                is LayoutEdge.Union -> {
                    add(edge.first)
                    add(edge.second)
                }
            }
        }
    }

    private fun incrementalGenerations(
        previous: Map<NodeId, Int>,
        graph: TreeGraph,
        added: Set<LayoutEdge>,
    ): Map<NodeId, Int> {
        val generations = previous.toMutableMap()
        val queue = ArrayDeque<NodeId>()
        for (edge in added) {
            when (edge) {
                is LayoutEdge.Parentage -> {
                    val childGeneration = generations.getValue(edge.child)
                    if (graph.parents(edge.parent).isEmpty() && generations.getValue(edge.parent) >= childGeneration) {
                        generations[edge.parent] = childGeneration - 1
                    }
                    queue.addLast(edge.child)
                }
                is LayoutEdge.Union -> {
                    val generation = minOf(generations.getValue(edge.first), generations.getValue(edge.second))
                    generations[edge.first] = generation
                    generations[edge.second] = generation
                }
            }
        }
        val visited = mutableSetOf<NodeId>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val parentGeneration = graph.parents(current).maxOfOrNull(generations::getValue) ?: generations.getValue(current)
            val next = if (graph.parents(current).isEmpty()) generations.getValue(current) else parentGeneration + 1
            if (generations[current] != next) generations[current] = next
            if (visited.add(current)) graph.children(current).forEach(queue::addLast)
        }
        val minimum = generations.values.minOrNull() ?: 0
        if (minimum < 0) generations.replaceAll { _, generation -> generation - minimum }
        return generations
    }

    private data class CacheEntry(
        val graph: TreeGraph,
        val metrics: NodeMetrics,
        val layout: Layout,
    ) {
        fun matches(other: TreeGraph): Boolean =
            graph === other || graph.structuralHash == other.structuralHash && graph.nodes == other.nodes && graph.edges == other.edges
    }

    private companion object {
        const val DEFAULT_CAPACITY: Int = 8
        const val MAX_INCREMENTAL_EDGE_CHANGES: Int = 8
        const val LOAD_FACTOR: Float = 0.75f
    }
}
