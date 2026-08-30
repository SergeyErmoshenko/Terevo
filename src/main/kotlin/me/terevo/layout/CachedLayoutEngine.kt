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
        val generations = cached.layout.generations.toMutableMap()
        val descendants = descendantsOf(request.graph, affected)
        val recalculated = recalculateGenerations(request.graph, generations, descendants)
        val nodes = cached.layout.nodes.mapValues { (id, rect) ->
            val size = request.metrics.sizeOf(id)
            val generation = recalculated[id] ?: generations.getValue(id)
            val top = generationTop(generation, size.height, request.options.generationSpacing)
            Rect(rect.left, top, size.width, size.height)
        }
        return Layout(
            nodes = nodes,
            edges = EdgeRouter.route(request.graph, nodes),
            generations = generations + recalculated,
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

    private fun descendantsOf(graph: TreeGraph, starts: Set<NodeId>): Set<NodeId> {
        val affected = starts.toMutableSet()
        val queue = ArrayDeque(starts)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (child in graph.children(current)) {
                if (affected.add(child)) queue.addLast(child)
            }
        }
        return affected
    }

    private fun recalculateGenerations(
        graph: TreeGraph,
        previous: Map<NodeId, Int>,
        affected: Set<NodeId>,
    ): Map<NodeId, Int> {
        val result = mutableMapOf<NodeId, Int>()
        val remainingParents = affected.associateWith { id -> graph.parents(id).count { it in affected } }.toMutableMap()
        val queue = ArrayDeque(affected.filter { remainingParents.getValue(it) == 0 }.sortedBy(graph::orderOf))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val parentGenerations = graph.parents(current).map { result[it] ?: previous[it] ?: 0 }
            result[current] = if (parentGenerations.isEmpty()) previous[current] ?: 0 else parentGenerations.max() + 1
            for (child in graph.children(current)) {
                if (child !in affected) continue
                val remaining = remainingParents.getValue(child) - 1
                remainingParents[child] = remaining
                if (remaining == 0) queue.addLast(child)
            }
        }
        return result
    }

    private fun generationTop(generation: Int, height: Double, spacing: Double): Double = generation * (height + spacing)

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
