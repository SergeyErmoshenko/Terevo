package me.terevo.export

import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Rect
import me.terevo.layout.Size
import kotlin.math.ceil

enum class JoinDirection {
    UP,
    DOWN,
    LEFT,
    RIGHT,
}

data class JoinLabel(
    val personLabel: String,
    val direction: JoinDirection,
    val continuesOnPage: Int,
)

data class ExportPage(
    val index: Int,
    val worldBounds: Rect,
    val joinLabels: List<JoinLabel>,
)

object TreePagination {
    fun paginate(
        layout: Layout,
        labels: Map<NodeId, ExportPersonLabel>,
        pageSize: Size,
        overlap: Double = DEFAULT_OVERLAP,
    ): List<ExportPage> {
        val bounds = layout.bounds
        if (bounds.width <= 0.0 || bounds.height <= 0.0) {
            return listOf(ExportPage(index = 0, worldBounds = bounds, joinLabels = emptyList()))
        }
        val usableWidth = (pageSize.width - overlap).coerceAtLeast(1.0)
        val usableHeight = (pageSize.height - overlap).coerceAtLeast(1.0)
        val columns = ceil(bounds.width / usableWidth).toInt().coerceAtLeast(1)
        val rows = ceil(bounds.height / usableHeight).toInt().coerceAtLeast(1)

        val tiles = (0 until rows).flatMap { row ->
            (0 until columns).map { column ->
                Rect(
                    left = bounds.left + column * usableWidth,
                    top = bounds.top + row * usableHeight,
                    width = pageSize.width,
                    height = pageSize.height,
                )
            }
        }

        return tiles.mapIndexed { index, tile ->
            val row = index / columns
            val column = index % columns
            ExportPage(
                index = index,
                worldBounds = tile,
                joinLabels = joinLabelsFor(layout, labels, tile, row, column, columns, rows),
            )
        }
    }

    private fun joinLabelsFor(
        layout: Layout,
        labels: Map<NodeId, ExportPersonLabel>,
        tile: Rect,
        row: Int,
        column: Int,
        columns: Int,
        rows: Int,
    ): List<JoinLabel> {
        val index = row * columns + column
        val result = mutableListOf<JoinLabel>()
        layout.nodes.forEach { (id, rect) ->
            if (!tile.intersects(rect)) return@forEach
            val label = labels[id]?.name ?: return@forEach
            if (rect.right > tile.right && column + 1 < columns) {
                result += JoinLabel(label, JoinDirection.RIGHT, index + 1)
            }
            if (rect.bottom > tile.bottom && row + 1 < rows) {
                result += JoinLabel(label, JoinDirection.DOWN, index + columns)
            }
            if (rect.left < tile.left && column > 0) {
                result += JoinLabel(label, JoinDirection.LEFT, index - 1)
            }
            if (rect.top < tile.top && row > 0) {
                result += JoinLabel(label, JoinDirection.UP, index - columns)
            }
        }
        return result.distinct()
    }

    private const val DEFAULT_OVERLAP: Double = 24.0
}
