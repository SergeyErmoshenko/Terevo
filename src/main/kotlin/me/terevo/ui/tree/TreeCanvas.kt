package me.terevo.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import me.terevo.layout.Point
import me.terevo.layout.Size
import me.terevo.ui.theme.TerevoTheme

@Composable
fun TreeCanvas(
    state: TreeCanvasState,
    onIntent: (TreeCanvasIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = TerevoTheme.colors
    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                onIntent(TreeCanvasIntent.Resize(Size(size.width.toDouble(), size.height.toDouble())))
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type != PointerEventType.Scroll) continue
                        val change = event.changes.firstOrNull() ?: continue
                        val factor = if (change.scrollDelta.y > 0f) ZOOM_OUT else ZOOM_IN
                        onIntent(TreeCanvasIntent.Zoom(change.position.toPoint(), factor))
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onIntent(TreeCanvasIntent.SelectAt(it.toPoint())) },
                    onDoubleTap = { onIntent(TreeCanvasIntent.EditAt(it.toPoint())) },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onIntent(TreeCanvasIntent.Pan(dragAmount.toPoint()))
                }
            },
    ) {
        val viewport = state.camera.visibleWorld(size.width.toDouble(), size.height.toDouble())
        val visible = state.spatialIndex.visible(state.layout, viewport)
        val layout = state.layout.copy(nodes = visible.nodes, edges = visible.edges)
        drawTree(layout, state.visuals, state.camera, colors, textMeasurer, state.highlight)
    }
}

private fun Offset.toPoint(): Point = Point(x.toDouble(), y.toDouble())

private const val ZOOM_IN: Double = 1.1
private const val ZOOM_OUT: Double = 1.0 / ZOOM_IN
