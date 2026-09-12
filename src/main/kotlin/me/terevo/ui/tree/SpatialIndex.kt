package me.terevo.ui.tree

import me.terevo.layout.*
import kotlin.math.floor

data class VisibleLayout(
    val nodes: Map<NodeId, Rect>,
    val edges: List<EdgePath>,
)

class SpatialIndex private constructor(
    private val nodes: Map<NodeId, Rect>,
    private val cells: Map<Cell, Set<NodeId>>,
    private val cellSize: Double,
) {
    fun query(viewport: Rect): Set<NodeId> = cellsOf(viewport)
        .flatMapTo(mutableSetOf()) { cells[it].orEmpty() }
        .filterTo(mutableSetOf()) { id -> nodes.getValue(id).intersects(viewport) }

    fun hitTest(point: Point): NodeId? = cells[cellOf(point)]
        .orEmpty()
        .sortedBy(NodeId::value)
        .firstOrNull { id -> nodes.getValue(id).contains(point) }

    fun visible(layout: Layout, viewport: Rect): VisibleLayout {
        val visibleNodes = query(viewport)
        return VisibleLayout(
            nodes = layout.nodes.filterKeys { it in visibleNodes },
            edges = layout.edges.filter { edge -> edge.segments.any { it in viewport } || edge.bounds().intersects(viewport) },
        )
    }

    private fun cellsOf(rect: Rect): Sequence<Cell> = sequence {
        val minX = floor(rect.left / cellSize).toInt()
        val maxX = floor(rect.right / cellSize).toInt()
        val minY = floor(rect.top / cellSize).toInt()
        val maxY = floor(rect.bottom / cellSize).toInt()
        for (x in minX..maxX) for (y in minY..maxY) yield(Cell(x, y))
    }

    private fun cellOf(point: Point): Cell = Cell(
        x = floor(point.x / cellSize).toInt(),
        y = floor(point.y / cellSize).toInt(),
    )

    private data class Cell(val x: Int, val y: Int)

    companion object {
        fun build(layout: Layout, cellSize: Double = DEFAULT_CELL_SIZE): SpatialIndex {
            val cells = mutableMapOf<Cell, MutableSet<NodeId>>()
            val index = SpatialIndex(layout.nodes, emptyMap(), cellSize)
            for ((id, rect) in layout.nodes) {
                for (cell in index.cellsOf(rect)) cells.getOrPut(cell, ::mutableSetOf).add(id)
            }
            return SpatialIndex(layout.nodes, cells.mapValues { it.value.toSet() }, cellSize)
        }

        private const val DEFAULT_CELL_SIZE: Double = 256.0
    }
}

fun Camera.visibleWorld(viewportWidth: Double, viewportHeight: Double, marginScreens: Double = 1.0): Rect {
    val topLeft = screenToWorld(Point(-viewportWidth * marginScreens, -viewportHeight * marginScreens))
    val bottomRight = screenToWorld(
        Point(viewportWidth * (1.0 + marginScreens), viewportHeight * (1.0 + marginScreens)),
    )
    return Rect(topLeft.x, topLeft.y, bottomRight.x - topLeft.x, bottomRight.y - topLeft.y)
}

private operator fun Rect.contains(point: Point): Boolean =
    point.x >= left && point.x <= right && point.y >= top && point.y <= bottom

private fun EdgePath.bounds(): Rect {
    val minX = segments.minOfOrNull { it.x } ?: 0.0
    val maxX = segments.maxOfOrNull { it.x } ?: 0.0
    val minY = segments.minOfOrNull { it.y } ?: 0.0
    val maxY = segments.maxOfOrNull { it.y } ?: 0.0
    return Rect(minX, minY, maxX - minX, maxY - minY)
}
