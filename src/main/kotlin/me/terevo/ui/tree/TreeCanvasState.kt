package me.terevo.ui.tree

import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.invariant.Invariants
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.PersonId
import me.terevo.layout.*
import me.terevo.ui.person.RelationMode

data class NodeDragState(
    val nodeId: NodeId,
    val origin: Rect,
    val startWorld: Point,
    val currentWorld: Point,
    val hoverTarget: NodeId? = null,
    val hoverValid: Boolean? = null,
) {
    val currentRect: Rect
        get() = origin.translated(currentWorld.x - startWorld.x, currentWorld.y - startWorld.y)
}

data class TreeCanvasState(
    val layout: Layout = Layout.EMPTY,
    val spatialIndex: SpatialIndex = SpatialIndex.build(Layout.EMPTY),
    val visuals: TreeVisuals = TreeVisuals(),
    val camera: Camera = Camera(),
    val viewport: Size = Size(0.0, 0.0),
    val highlight: TreeHighlight = TreeHighlight.NONE,
    val nodeDrag: NodeDragState? = null,
    val tree: FamilyTree = FamilyTree.EMPTY,
    // The person last picked from search results; the home button returns to them instead of
    // the default anchor.
    val searchAnchor: NodeId? = null,
) {
    val selected: NodeId? get() = highlight.selected

    // tree.persons keeps load order, which is the order people were added.
    val homePersonId: NodeId?
        get() = searchAnchor?.takeIf { it in layout.nodes }
            ?: tree.persons.keys.asSequence().map { NodeId(it.value.toString()) }.firstOrNull { it in layout.nodes }
            ?: layout.mainPersonId
}

fun candidateRelation(mode: RelationMode, source: PersonId, target: PersonId) = when (mode) {
    RelationMode.PARENT -> (ParentChild.of(parent = target, child = source) as? Outcome.Ok)?.value
    RelationMode.CHILD -> (ParentChild.of(parent = source, child = target) as? Outcome.Ok)?.value
    RelationMode.SPOUSE -> (Marriage.of(first = source, second = target) as? Outcome.Ok)?.value
}

fun canCreateRelation(tree: FamilyTree, mode: RelationMode, source: PersonId, target: PersonId): Boolean {
    val relation = candidateRelation(mode, source, target) ?: return false
    return Invariants.check(tree, Change.AddRelation(relation)) == null
}

fun canCreateAnyRelation(tree: FamilyTree, source: PersonId, target: PersonId): Boolean =
    RelationMode.entries.any { canCreateRelation(tree, it, source, target) }

private fun NodeId.toPersonId(): PersonId = PersonId.parse(value)

sealed interface TreeCanvasIntent {
    data class Resize(val viewport: Size) : TreeCanvasIntent
    data class Pan(val delta: Point) : TreeCanvasIntent
    data class Zoom(val position: Point, val factor: Double) : TreeCanvasIntent
    data class SelectAt(val position: Point) : TreeCanvasIntent
    data class EditAt(val position: Point) : TreeCanvasIntent
    data class AddPersonAt(val position: Point) : TreeCanvasIntent
    data class AddRelativeAt(val nodeId: NodeId, val mode: RelationMode) : TreeCanvasIntent
    data object ClearSelection : TreeCanvasIntent
    data object FitToScreen : TreeCanvasIntent
    data object ActualSize : TreeCanvasIntent
    data object CenterSelected : TreeCanvasIntent
    data class DragNodeStart(val nodeId: NodeId, val position: Point) : TreeCanvasIntent
    data class DragNodeMove(val position: Point) : TreeCanvasIntent
    data object DragNodeEnd : TreeCanvasIntent
    data object DragNodeCancel : TreeCanvasIntent
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
    is TreeCanvasIntent.AddPersonAt -> state
    is TreeCanvasIntent.AddRelativeAt -> state
    TreeCanvasIntent.ClearSelection -> state.copy(highlight = TreeHighlight.NONE)
    TreeCanvasIntent.FitToScreen -> {
        val mainPersonRect = state.homePersonId?.let(state.layout::rectOf)
        if (mainPersonRect != null) {
            // Resets to a known-good zoom instead of preserving whatever scale the user happened
            // to be at - returning to the main person at an arbitrary current zoom can leave them
            // too close in or too far out to make sense of where you landed.
            state.copy(
                camera = Camera(scale = Camera.DEFAULT_SCALE).center(mainPersonRect, state.viewport),
            )
        } else {
            state.copy(
                camera = state.camera.fit(
                    state.layout.bounds,
                    state.viewport,
                    FIT_PADDING
                )
            )
        }
    }

    TreeCanvasIntent.ActualSize -> state.copy(camera = Camera(scale = 1.0, offset = state.camera.offset))
    TreeCanvasIntent.CenterSelected -> {
        val selected = state.selected
        val rect = selected?.let(state.layout::rectOf)
        if (rect == null) state else state.copy(camera = state.camera.center(rect, state.viewport))
    }

    is TreeCanvasIntent.DragNodeStart -> {
        val origin = state.layout.rectOf(intent.nodeId)
        if (origin == null) {
            state
        } else {
            val startWorld = state.camera.screenToWorld(intent.position)
            state.copy(nodeDrag = NodeDragState(intent.nodeId, origin, startWorld, startWorld))
        }
    }

    is TreeCanvasIntent.DragNodeMove -> {
        val drag = state.nodeDrag
        if (drag == null) {
            state
        } else {
            val world = state.camera.screenToWorld(intent.position)
            val hoverTarget = state.spatialIndex.hitTest(world)?.takeIf { it != drag.nodeId }
            val hoverValid = hoverTarget?.let {
                canCreateAnyRelation(state.tree, drag.nodeId.toPersonId(), it.toPersonId())
            }
            state.copy(
                nodeDrag = drag.copy(currentWorld = world, hoverTarget = hoverTarget, hoverValid = hoverValid),
            )
        }
    }

    TreeCanvasIntent.DragNodeEnd -> state
    TreeCanvasIntent.DragNodeCancel -> state.copy(nodeDrag = null)
}

private const val FIT_PADDING: Double = 32.0
