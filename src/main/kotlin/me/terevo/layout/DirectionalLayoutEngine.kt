package me.terevo.layout

class DirectionalLayoutEngine(private val delegate: LayoutEngine) : LayoutEngine {
    override fun layout(request: LayoutRequest): Layout {
        if (request.options.direction == LayoutDirection.TOP_DOWN) {
            return delegate.layout(request).mirroredVertically()
        }
        val transposedMetrics = NodeMetrics(
            sizes = request.metrics.sizes.mapValues { (_, size) -> size.transposed() },
            defaultSize = request.metrics.defaultSize.transposed(),
        )
        return delegate.layout(request.copy(metrics = transposedMetrics)).mirroredVertically().transposed()
    }
}

private fun Size.transposed(): Size = Size(width = height, height = width)

private fun Point.transposed(): Point = Point(x = y, y = x)

private fun Rect.transposed(): Rect = Rect(left = top, top = left, width = height, height = width)

private fun Layout.transposed(): Layout = Layout(
    nodes = nodes.mapValues { (_, rect) -> rect.transposed() },
    edges = edges.map { it.copy(segments = it.segments.map { point -> point.transposed() }) },
    generations = generations,
    bounds = bounds.transposed(),
)

/**
 * Flips the layout so that descendants (higher generations) render above ancestors
 * (lower generations): the person being explored sits at the top of the canvas and
 * their ancestors extend downward, rather than the reverse.
 */
private fun Layout.mirroredVertically(): Layout {
    if (nodes.isEmpty()) return this
    val axis = bounds.top + bounds.bottom

    fun mirrorPoint(point: Point): Point = Point(x = point.x, y = axis - point.y)
    fun mirrorRect(rect: Rect): Rect =
        Rect(left = rect.left, top = axis - rect.bottom, width = rect.width, height = rect.height)

    return Layout(
        nodes = nodes.mapValues { (_, rect) -> mirrorRect(rect) },
        edges = edges.map { it.copy(segments = it.segments.map(::mirrorPoint)) },
        generations = generations,
        bounds = mirrorRect(bounds),
    )
}
