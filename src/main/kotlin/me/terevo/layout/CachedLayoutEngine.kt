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
        // No node changed generation or lineage, so only sizes can differ - x stays valid.
        if (affected.isEmpty()) return resize(cached.layout, request)

        // Adding or removing a relation re-parents nodes, and horizontal placement is derived
        // entirely from lineage: which cluster a node belongs to, its order within its row, and
        // whose center it must sit above. Reusing the cached x here (only recomputing y from the
        // new generations) left a node wherever it happened to sit BEFORE it had that relation -
        // measured: attaching a child to its parent left the child 408px to the side, at x=1408
        // while its parent sat at x=1000, where a full relayout puts both at x=200. Nothing in the
        // incremental path can recover that, because it never consults the ordering or coordinate
        // passes that decide x in the first place.
        //
        // Structural changes are also exactly the case a user is watching for - they just added
        // someone and expect the tree to open up and make room - so this returns null and lets the
        // caller run the full layout. A full pass measures ~50ms at 100 people and ~250ms at 1000,
        // which is affordable for a one-off edit. The incremental path is kept for the genuinely
        // cheap cases above: identical graphs and metric-only changes, which is what keeps typing
        // in the name field responsive.
        return null
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
