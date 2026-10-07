package me.terevo.ui.tree

import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.invariant.Invariants
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.PersonId
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.ui.person.RelationMode

// What the tree view shows. Camera, hit testing and drag preview live in the frontend; the
// backend only asks it to center on someone by bumping centerRequest.
data class TreeCanvasState(
    val layout: Layout = Layout.EMPTY,
    val visuals: TreeVisuals = TreeVisuals(),
    val highlight: TreeHighlight = TreeHighlight.NONE,
    val tree: FamilyTree = FamilyTree.EMPTY,
    // The person last picked from search results; the home button returns to them instead of
    // the default anchor.
    val searchAnchor: NodeId? = null,
    val centerOn: NodeId? = null,
    val centerRequest: Long = 0,
) {
    val selected: NodeId? get() = highlight.selected

    // tree.persons keeps load order, which is the order people were added.
    val homePersonId: NodeId?
        get() = searchAnchor?.takeIf { it in layout.nodes }
            ?: tree.persons.keys.asSequence().map { NodeId(it.value.toString()) }.firstOrNull { it in layout.nodes }
            ?: layout.mainPersonId

    fun centeredOnSelected(): TreeCanvasState =
        if (selected == null) this else copy(centerOn = selected, centerRequest = centerRequest + 1)
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
