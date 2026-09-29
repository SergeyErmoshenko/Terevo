package me.terevo.layout

internal fun visibleNodes(request: LayoutRequest, generations: Map<NodeId, Int>): Set<NodeId> {
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
