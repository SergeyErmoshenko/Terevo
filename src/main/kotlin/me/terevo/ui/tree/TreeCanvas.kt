package me.terevo.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import me.terevo.layout.NodeId
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
    val cornerRadiusPx = with(LocalDensity.current) { TerevoTheme.spacing.cornerRadius.toPx() }
    val latestState = rememberUpdatedState(state)
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
                var draggedNode: NodeId? = null
                detectDragGestures(
                    onDragStart = { offset ->
                        val world = latestState.value.camera.screenToWorld(offset.toPoint())
                        val hit = latestState.value.spatialIndex.hitTest(world)
                        draggedNode = hit
                        if (hit != null) onIntent(TreeCanvasIntent.DragNodeStart(hit, offset.toPoint()))
                    },
                    onDragEnd = {
                        if (draggedNode != null) onIntent(TreeCanvasIntent.DragNodeEnd)
                        draggedNode = null
                    },
                    onDragCancel = {
                        if (draggedNode != null) onIntent(TreeCanvasIntent.DragNodeCancel)
                        draggedNode = null
                    },
                ) { change, dragAmount ->
                    change.consume()
                    if (draggedNode != null) {
                        onIntent(TreeCanvasIntent.DragNodeMove(change.position.toPoint()))
                    } else {
                        onIntent(TreeCanvasIntent.Pan(dragAmount.toPoint()))
                    }
                }
            },
    ) {
        val viewport = state.camera.visibleWorld(size.width.toDouble(), size.height.toDouble())
        drawDotGrid(state.camera, colors, viewport)
        val visible = state.spatialIndex.visible(state.layout, viewport)
        val layout = state.layout.copy(nodes = visible.nodes, edges = visible.edges)
        drawTree(layout, state.visuals, state.camera, colors, textMeasurer, state.highlight, cornerRadiusPx)
        state.nodeDrag?.let { drawDragPreview(it, state.layout, state.camera, colors, cornerRadiusPx) }
    }
}

private fun Offset.toPoint(): Point = Point(x.toDouble(), y.toDouble())

private const val ZOOM_IN: Double = 1.1
private const val ZOOM_OUT: Double = 1.0 / ZOOM_IN
