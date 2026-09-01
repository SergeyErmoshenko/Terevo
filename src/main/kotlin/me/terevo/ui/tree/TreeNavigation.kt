package me.terevo.ui.tree

import me.terevo.layout.NodeId
import me.terevo.layout.Point

data class PersonDetails(
    val person: PersonVisual,
    val parents: List<PersonVisual>,
    val children: List<PersonVisual>,
    val spouses: List<PersonVisual>,
)

data class TreeNavigationState(
    val canvas: TreeCanvasState,
    val details: PersonDetails? = null,
    val backHistory: List<NodeId> = emptyList(),
    val forwardHistory: List<NodeId> = emptyList(),
    val editorPerson: NodeId? = null,
    val cameraAnimationMillis: Int = CAMERA_ANIMATION_MILLIS,
) {
    val canGoBack: Boolean get() = backHistory.isNotEmpty()
    val canGoForward: Boolean get() = forwardHistory.isNotEmpty()
}

sealed interface TreeNavigationIntent {
    data class Click(val screenPoint: Point) : TreeNavigationIntent
    data class Select(val id: NodeId) : TreeNavigationIntent
    data class DoubleClick(val screenPoint: Point) : TreeNavigationIntent
    data object ClearSelection : TreeNavigationIntent
    data object Back : TreeNavigationIntent
    data object Forward : TreeNavigationIntent
}

fun reduceNavigation(
    state: TreeNavigationState,
    intent: TreeNavigationIntent,
    detailsOf: (NodeId) -> PersonDetails?,
): TreeNavigationState = when (intent) {
    is TreeNavigationIntent.Click -> {
        val id = state.canvas.spatialIndex.hitTest(state.canvas.camera.screenToWorld(intent.screenPoint))
        if (id == null) clearSelection(state) else select(state, id, detailsOf)
    }
    is TreeNavigationIntent.Select -> select(state, intent.id, detailsOf)
    is TreeNavigationIntent.DoubleClick -> {
        val id = state.canvas.spatialIndex.hitTest(state.canvas.camera.screenToWorld(intent.screenPoint))
        if (id == null) state else select(state, id, detailsOf).copy(editorPerson = id)
    }
    TreeNavigationIntent.ClearSelection -> clearSelection(state)
    TreeNavigationIntent.Back -> navigateHistory(state, state.backHistory, state.forwardHistory, detailsOf, isBack = true)
    TreeNavigationIntent.Forward -> navigateHistory(state, state.forwardHistory, state.backHistory, detailsOf, isBack = false)
}

private fun select(
    state: TreeNavigationState,
    id: NodeId,
    detailsOf: (NodeId) -> PersonDetails?,
): TreeNavigationState {
    if (state.canvas.selected == id) return state
    val previous = state.canvas.selected
    val rect = state.canvas.layout.rectOf(id) ?: return state
    return state.copy(
        canvas = state.canvas.copy(
            highlight = TreeHighlight.focusedOn(id),
            camera = state.canvas.camera.center(rect, state.canvas.viewport),
        ),
        details = detailsOf(id),
        backHistory = if (previous == null) state.backHistory else state.backHistory + previous,
        forwardHistory = emptyList(),
        editorPerson = null,
    )
}

private fun clearSelection(state: TreeNavigationState): TreeNavigationState = state.copy(
    canvas = state.canvas.copy(highlight = TreeHighlight.NONE),
    details = null,
    editorPerson = null,
)

private fun navigateHistory(
    state: TreeNavigationState,
    source: List<NodeId>,
    destination: List<NodeId>,
    detailsOf: (NodeId) -> PersonDetails?,
    isBack: Boolean,
): TreeNavigationState {
    val target = source.lastOrNull() ?: return state
    val current = state.canvas.selected
    val rect = state.canvas.layout.rectOf(target) ?: return state
    return state.copy(
        canvas = state.canvas.copy(
            highlight = TreeHighlight.focusedOn(target),
            camera = state.canvas.camera.center(rect, state.canvas.viewport),
        ),
        details = detailsOf(target),
        backHistory = if (isBack) source.dropLast(1) else if (current == null) destination else destination + current,
        forwardHistory = if (isBack) if (current == null) destination else destination + current else source.dropLast(1),
        editorPerson = null,
    )
}

private const val CAMERA_ANIMATION_MILLIS: Int = 250
