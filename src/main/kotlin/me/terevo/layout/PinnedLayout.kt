package me.terevo.layout

fun Layout.withPinned(pinned: Map<NodeId, Point>, graph: TreeGraph): Layout {
    if (pinned.isEmpty()) return this
    val nodes = nodes.mapValues { (id, rect) ->
        val override = pinned[id] ?: return@mapValues rect
        Rect(override.x, override.y, rect.width, rect.height)
    }
    return copy(
        nodes = nodes,
        edges = EdgeRouter.route(graph, nodes),
        bounds = Rect.enclosing(nodes.values),
    )
}
