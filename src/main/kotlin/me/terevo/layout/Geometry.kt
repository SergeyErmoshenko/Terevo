package me.terevo.layout

data class Point(val x: Double, val y: Double)

data class Size(val width: Double, val height: Double)

data class Rect(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
) {
    val right: Double get() = left + width
    val bottom: Double get() = top + height
    val centerX: Double get() = left + width / HALF
    val centerY: Double get() = top + height / HALF
    val center: Point get() = Point(centerX, centerY)
    val topCenter: Point get() = Point(centerX, top)
    val bottomCenter: Point get() = Point(centerX, bottom)

    fun translated(dx: Double, dy: Double): Rect = copy(left = left + dx, top = top + dy)

    fun intersects(other: Rect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun union(other: Rect): Rect {
        val newLeft = minOf(left, other.left)
        val newTop = minOf(top, other.top)
        return Rect(newLeft, newTop, maxOf(right, other.right) - newLeft, maxOf(bottom, other.bottom) - newTop)
    }

    companion object {
        private const val HALF = 2.0

        val EMPTY: Rect = Rect(0.0, 0.0, 0.0, 0.0)

        fun of(origin: Point, size: Size): Rect = Rect(origin.x, origin.y, size.width, size.height)

        fun enclosing(rects: Collection<Rect>): Rect =
            rects.reduceOrNull { acc, rect -> acc.union(rect) } ?: EMPTY
    }
}
