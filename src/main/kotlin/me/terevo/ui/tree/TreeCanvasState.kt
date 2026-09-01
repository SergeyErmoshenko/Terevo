package me.terevo.ui.tree

import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Size

data class TreeCanvasState(
    val layout: Layout = Layout.EMPTY,
    val spatialIndex: SpatialIndex = SpatialIndex.build(Layout.EMPTY),
    val visuals: TreeVisuals = TreeVisuals(),
    val camera: Camera = Camera(),
    val viewport: Size = Size(0.0, 0.0),
    val highlight: TreeHighlight = TreeHighlight.NONE,
) {
    val selected: NodeId? get() = highlight.selected
}

sealed interface TreeCanvasIntent {
    data class Resize(val viewport: Size) : TreeCanvasIntent
    data class Pan(val delta: Point) : TreeCanvasIntent
    data class Zoom(val position: Point, val factor: Double) : TreeCanvasIntent
    data class SelectAt(val position: Point) : TreeCanvasIntent
    data class EditAt(val position: Point) : TreeCanvasIntent
    data object ClearSelection : TreeCanvasIntent
    data object FitToScreen : TreeCanvasIntent
    data object ActualSize : TreeCanvasIntent
    data object CenterSelected : TreeCanvasIntent
}

fun reduceTreeCanvas(state: TreeCanvasState, intent: TreeCanvasIntent): TreeCanvasState = when (intent) {
    is TreeCanvasIntent.Resize -> {
        val resized = state.copy(viewport = intent.viewport)
        if (state.viewport.width == 0.0 && state.viewport.height == 0.0 && state.layout.nodes.isNotEmpty()) {
            resized.copy(camera = resized.camera.fit(resized.layout.bounds, resized.viewport, FIT_PADDING))
        } else {
            resized
        }
    }
    is TreeCanvasIntent.Pan -> state.copy(camera = state.camera.pan(intent.delta))
    is TreeCanvasIntent.Zoom -> state.copy(camera = state.camera.zoomAt(intent.position, intent.factor))
    is TreeCanvasIntent.SelectAt -> state.copy(
        highlight = TreeHighlight.focusedOn(state.spatialIndex.hitTest(state.camera.screenToWorld(intent.position))),
    )
    is TreeCanvasIntent.EditAt -> state
    TreeCanvasIntent.ClearSelection -> state.copy(highlight = TreeHighlight.NONE)
    TreeCanvasIntent.FitToScreen -> state.copy(camera = state.camera.fit(state.layout.bounds, state.viewport, FIT_PADDING))
    TreeCanvasIntent.ActualSize -> state.copy(camera = Camera(scale = 1.0, offset = state.camera.offset))
    TreeCanvasIntent.CenterSelected -> {
        val selected = state.selected
        val rect = selected?.let(state.layout::rectOf)
        if (rect == null) state else state.copy(camera = state.camera.center(rect, state.viewport))
    }
}

private const val FIT_PADDING: Double = 32.0
