package me.terevo.ui.tree

import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.layout.Size

data class Camera(
    val scale: Double = DEFAULT_SCALE,
    val offset: Point = Point(0.0, 0.0),
) {
    fun worldToScreen(point: Point): Point = Point(
        x = point.x * scale + offset.x,
        y = point.y * scale + offset.y,
    )

    fun screenToWorld(point: Point): Point = Point(
        x = (point.x - offset.x) / scale,
        y = (point.y - offset.y) / scale,
    )

    fun pan(delta: Point): Camera = copy(offset = Point(offset.x + delta.x, offset.y + delta.y))

    fun zoomAt(screenPoint: Point, factor: Double): Camera {
        val worldPoint = screenToWorld(screenPoint)
        val newScale = (scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
        return Camera(
            scale = newScale,
            offset = Point(
                x = screenPoint.x - worldPoint.x * newScale,
                y = screenPoint.y - worldPoint.y * newScale,
            ),
        )
    }

    fun fit(bounds: Rect, viewport: Size, padding: Double): Camera {
        if (bounds.width <= 0.0 || bounds.height <= 0.0) return this
        val availableWidth = (viewport.width - padding * 2.0).coerceAtLeast(1.0)
        val availableHeight = (viewport.height - padding * 2.0).coerceAtLeast(1.0)
        val fittedScale = minOf(availableWidth / bounds.width, availableHeight / bounds.height)
            .coerceIn(MIN_SCALE, DEFAULT_SCALE)
        return Camera(
            scale = fittedScale,
            offset = Point(
                x = (viewport.width - bounds.width * fittedScale) / 2.0 - bounds.left * fittedScale,
                y = (viewport.height - bounds.height * fittedScale) / 2.0 - bounds.top * fittedScale,
            ),
        )
    }

    fun center(rect: Rect, viewport: Size): Camera = copy(
        offset = Point(
            x = viewport.width / 2.0 - rect.centerX * scale,
            y = viewport.height / 2.0 - rect.centerY * scale,
        ),
    )

    companion object {
        const val MIN_SCALE: Double = 0.1
        const val MAX_SCALE: Double = 4.0
        const val DEFAULT_SCALE: Double = 1.0
    }
}
