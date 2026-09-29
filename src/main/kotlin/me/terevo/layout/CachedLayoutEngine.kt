package me.terevo.layout

class CachedLayoutEngine(
    private val delegate: LayoutEngine = GraphvizLayoutEngine(),
    private val capacity: Int = DEFAULT_CAPACITY,
) : LayoutEngine {
    private val cache = object : LinkedHashMap<LayoutOptions, CacheEntry>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LayoutOptions, CacheEntry>): Boolean = size > capacity
    }

    override fun layout(request: LayoutRequest): Layout {
        val cached = cache[request.options]
        if (cached != null && cached.matches(request.graph)) {
            if (cached.metrics == request.metrics) return cached.layout
            resizedInPlace(cached.layout, request)?.let { return store(request, it) }
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

    // A metrics-only change (a name edited while typing) keeps every card's center and the edges,
    // which attach to card centers, as long as no card changed height and none now overlaps its
    // neighbor - that keeps the name field responsive without a full layout per keystroke.
    private fun resizedInPlace(layout: Layout, request: LayoutRequest): Layout? {
        val nodes = layout.nodes.mapValues { (id, rect) ->
            val size = request.metrics.sizeOf(id)
            if (size.height != rect.height) return null
            Rect(rect.centerX - size.width / 2, rect.top, size.width, size.height)
        }
        if (overlapsWithinRow(nodes, layout.generations)) return null
        return layout.copy(nodes = nodes, bounds = layout.bounds.union(Rect.enclosing(nodes.values)))
    }

    private fun overlapsWithinRow(nodes: Map<NodeId, Rect>, generations: Map<NodeId, Int>): Boolean {
        for (ids in nodes.keys.groupBy { generations[it] }.values) {
            val row = ids.map { nodes.getValue(it) }.sortedBy { it.left }
            for ((a, b) in row.zipWithNext()) {
                if (b.left < a.right) return true
            }
        }
        return false
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
        const val LOAD_FACTOR: Float = 0.75f
    }
}
