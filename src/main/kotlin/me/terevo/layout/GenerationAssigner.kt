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
        val descendants = descendantCounts(graph)
        for (union in graph.unions.sortedWith(compareBy({ graph.orderOf(it.first) }, { graph.orderOf(it.second) }))) {
            val firstGeneration = generations.getValue(union.first)
            val secondGeneration = generations.getValue(union.second)
            if (firstGeneration == secondGeneration) continue

            val firstPriority = descendants.getValue(union.first)
            val secondPriority = descendants.getValue(union.second)
            when {
                firstPriority > secondPriority -> generations[union.second] = firstGeneration
                secondPriority > firstPriority -> generations[union.first] = secondGeneration
                graph.orderOf(union.first) <= graph.orderOf(union.second) -> generations[union.second] = firstGeneration
                else -> generations[union.first] = secondGeneration
            }
        }
    }

    private fun descendantCounts(graph: TreeGraph): Map<NodeId, Int> {
        val counts = graph.sortedNodeIds().associateWith { 0 }.toMutableMap()
        val remainingChildren = graph.sortedNodeIds().associateWith { graph.children(it).size }.toMutableMap()
        val queue = ArrayDeque(graph.sortedNodeIds().filter { remainingChildren.getValue(it) == 0 })

        while (queue.isNotEmpty()) {
            val child = queue.removeFirst()
            for (parent in graph.parents(child)) {
                counts[parent] = counts.getValue(parent) + counts.getValue(child) + 1
                val remaining = remainingChildren.getValue(parent) - 1
                remainingChildren[parent] = remaining
                if (remaining == 0) queue.addLast(parent)
            }
        }
        return counts
    }

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
