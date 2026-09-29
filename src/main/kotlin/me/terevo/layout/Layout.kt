package me.terevo.layout

enum class LayoutMode {
    ANCESTORS,
    DESCENDANTS,
    BOTH,
    WHOLE_FAMILY,
}

enum class EdgeStyle {
    BIOLOGICAL,
    NON_BIOLOGICAL,
    MARRIAGE,
    DISSOLVED_MARRIAGE,
}

enum class LayoutDirection {
    TOP_DOWN,
    LEFT_RIGHT,
}

data class NodeMetrics(
    val sizes: Map<NodeId, Size>,
    val defaultSize: Size,
) {
    fun sizeOf(id: NodeId): Size = sizes[id] ?: defaultSize
}

data class LayoutOptions(
    val root: NodeId? = null,
    val mode: LayoutMode = LayoutMode.WHOLE_FAMILY,
    val depth: Int = UNLIMITED_DEPTH,
    val siblingSpacing: Double = DEFAULT_SIBLING_SPACING,
    val subtreeSpacing: Double = DEFAULT_SUBTREE_SPACING,
    val generationSpacing: Double = DEFAULT_GENERATION_SPACING,
    val spouseSpacing: Double = DEFAULT_SPOUSE_SPACING,
    val clusterSpacing: Double = DEFAULT_CLUSTER_SPACING,
    val direction: LayoutDirection = LayoutDirection.TOP_DOWN,
) {
    companion object {
        const val UNLIMITED_DEPTH: Int = Int.MAX_VALUE
        const val DEFAULT_SIBLING_SPACING: Double = 40.0
        const val DEFAULT_SUBTREE_SPACING: Double = 48.0
        const val DEFAULT_GENERATION_SPACING: Double = 160.0
        const val DEFAULT_SPOUSE_SPACING: Double = 32.0
        const val DEFAULT_CLUSTER_SPACING: Double = 320.0
    }
}

data class LayoutRequest(
    val graph: TreeGraph,
    val metrics: NodeMetrics,
    val options: LayoutOptions = LayoutOptions(),
)

data class EdgePath(
    val edge: LayoutEdge,
    val segments: List<Point>,
    val style: EdgeStyle,
)

data class Layout(
    val nodes: Map<NodeId, Rect>,
    val edges: List<EdgePath>,
    val generations: Map<NodeId, Int>,
    val bounds: Rect,
    // The topmost-generation node of the layout's largest family, used by the focus button to
    // return to a sensible anchor instead of zooming out to fit the whole tree.
    val mainPersonId: NodeId? = null,
) {
    fun rectOf(id: NodeId): Rect? = nodes[id]

    companion object {
        val EMPTY: Layout = Layout(emptyMap(), emptyList(), emptyMap(), Rect.EMPTY, null)
    }
}

fun interface LayoutEngine {
    fun layout(request: LayoutRequest): Layout
}
