package me.terevo.layout

internal data class GenerationAssignment(
    val generations: Map<NodeId, Int>,
    val components: List<List<NodeId>>,
)

internal object GenerationAssigner {

    fun assign(graph: TreeGraph): GenerationAssignment {
        if (graph.nodes.isEmpty()) return GenerationAssignment(emptyMap(), emptyList())

        val generations = longestPathGenerations(graph).toMutableMap()
        alignPartners(graph, generations)
        return GenerationAssignment(
            generations = generations,
            components = connectedComponents(graph),
        )
    }

    private fun longestPathGenerations(graph: TreeGraph): Map<NodeId, Int> {
        val remainingParents = graph.sortedNodeIds().associateWith { graph.parents(it).size }.toMutableMap()
        val generations = graph.sortedNodeIds().associateWith { 0 }.toMutableMap()
        val queue = ArrayDeque(graph.sortedNodeIds().filter { remainingParents.getValue(it) == 0 })
        val processed = mutableSetOf<NodeId>()

        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (!processed.add(parent)) continue

            for (child in graph.children(parent)) {
                generations[child] = maxOf(generations.getValue(child), generations.getValue(parent) + 1)
                val remaining = remainingParents.getValue(child) - 1
                remainingParents[child] = remaining
                if (remaining == 0) queue.addLast(child)
            }
        }

        for (id in graph.sortedNodeIds()) {
            if (id !in processed) generations[id] = 0
        }
        return generations
    }

    private fun alignPartners(graph: TreeGraph, generations: MutableMap<NodeId, Int>) {
        // Co-parents without a recorded marriage still need to land on the same generation as
        // married partners do, otherwise their shared child's two parent lines start from
        // different rows.
        val pairs = (graph.unions.map { it.first to it.second } + coParentPairs(graph))
            .distinct()
            .sortedWith(compareBy({ graph.orderOf(it.first) }, { graph.orderOf(it.second) }))

        repeat(pairs.size.coerceAtLeast(1)) {
            var changed = false
            for ((first, second) in pairs) {
                val firstGeneration = generations.getValue(first)
                val secondGeneration = generations.getValue(second)
                if (firstGeneration == secondGeneration) continue
                changed = true

                // Always pull the shallower partner down to match the deeper one, never the
                // other way around: a node's own generation already reflects the longest path
                // from a root through it, and its children were placed one generation below that.
                // Overwriting it with a shallower number (as a plain assignment would) leaves those
                // already-placed children at the same generation as their parent, or above it.
                val target = maxOf(firstGeneration, secondGeneration)
                relax(graph, generations, first, target)
                relax(graph, generations, second, target)
            }
            if (!changed) return
        }
    }

    // Raises a node's generation to at least minGeneration and, only where that actually forces
    // a change, cascades the same requirement to its children one generation deeper - so a shared
    // child that's already deep enough via another parent is left untouched instead of being
    // pushed further down than it needs to be.
    //
    // The raise also propagates backward to a parent that was tightly (no slack) one generation
    // above the node's *old* generation: without this, pulling one spouse down to match a partner
    // whose own lineage simply has more recorded generations leaves that spouse's own parent
    // stranded behind by two or more generations - turning a plain one-hop parent/child edge into
    // a multi-generation edge that gets rendered as a dummy-waypoint zigzag instead of a straight
    // connector.
    //
    // But raising the parent is only ever worth it when that parent has no OTHER children: the
    // forward cascade above re-applies to every child of a raised node unconditionally, so pulling
    // a multi-child parent down to save one child's edge from a zigzag drags every sibling's edge
    // into a zigzag instead - trading one bent connector for several, and stranding the parent
    // itself (and any of its own untouched relatives) many generations below where it actually
    // belongs. A real family tree hits this constantly: one child marries into a lineage with a
    // long recorded ancestry while their siblings stay childless or shallow, and the shallow
    // siblings (and the parent) used to get needlessly dragged down with the deep one. Restricting
    // this to sole children keeps the zigzag-avoidance behavior for the case it actually helps -
    // a lone ancestor with a single child - without letting it cascade sideways onto siblings.
    private fun relax(graph: TreeGraph, generations: MutableMap<NodeId, Int>, node: NodeId, minGeneration: Int) {
        val previous = generations.getValue(node)
        if (previous >= minGeneration) return
        generations[node] = minGeneration
        for (child in graph.children(node)) {
            relax(graph, generations, child, minGeneration + 1)
        }
        for (parent in graph.parents(node)) {
            if (graph.children(parent).size == 1 && generations.getValue(parent) == previous - 1) {
                relax(graph, generations, parent, minGeneration - 1)
            }
        }
    }

    private fun coParentPairs(graph: TreeGraph): List<Pair<NodeId, NodeId>> =
        graph.sortedNodeIds()
            .flatMap { child ->
                val parents = graph.parents(child)
                parents.indices.flatMap { i -> (i + 1 until parents.size).map { j -> parents[i] to parents[j] } }
            }
            .distinct()

    private fun connectedComponents(graph: TreeGraph): List<List<NodeId>> {
        val neighbors = graph.sortedNodeIds().associateWith { id ->
            (graph.parents(id) + graph.children(id) + graph.partners(id))
                .distinct()
                .sortedBy { graph.orderOf(it) }
        }
        val remaining = graph.sortedNodeIds().toMutableSet()
        val components = mutableListOf<List<NodeId>>()

        while (remaining.isNotEmpty()) {
            val first = remaining.first()
            val component = mutableListOf<NodeId>()
            val queue = ArrayDeque(listOf(first))
            remaining.remove(first)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                component += current
                for (neighbor in neighbors.getValue(current)) {
                    if (remaining.remove(neighbor)) queue.addLast(neighbor)
                }
            }
            components += component.sortedBy { graph.orderOf(it) }
        }
        return components
    }
}
