package me.terevo.layout

class DirectionalLayoutEngine(private val delegate: LayoutEngine) : LayoutEngine {
    override fun layout(request: LayoutRequest): Layout {
        if (request.options.direction == LayoutDirection.TOP_DOWN) return delegate.layout(request)
        val transposedMetrics = NodeMetrics(
            sizes = request.metrics.sizes.mapValues { (_, size) -> size.transposed() },
            defaultSize = request.metrics.defaultSize.transposed(),
        )
        return delegate.layout(request.copy(metrics = transposedMetrics)).transposed()
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
