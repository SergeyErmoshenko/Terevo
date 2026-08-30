package me.terevo.domain.invariant

import me.terevo.domain.DomainError
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.sameLinkAs

fun interface Invariant {
    fun check(tree: FamilyTree, change: Change): DomainError?
}

object NoSelfRelation : Invariant {
    override fun check(tree: FamilyTree, change: Change): DomainError? {
        val relation = (change as? Change.AddRelation)?.relation ?: return null
        return when (relation) {
            is ParentChild ->
                if (relation.parent == relation.child) DomainError.Link.SelfRelation(relation.parent) else null

            is Marriage ->
                if (relation.spouseA == relation.spouseB) DomainError.Link.SelfRelation(relation.spouseA) else null
        }
    }
}

object NoDuplicateRelation : Invariant {
    override fun check(tree: FamilyTree, change: Change): DomainError? {
        val relation = (change as? Change.AddRelation)?.relation ?: return null
        val anchor = relation.participants.firstOrNull() ?: return null
        val duplicate = tree.relationsOf(anchor).firstOrNull { it.id != relation.id && it.sameLinkAs(relation) }
        return duplicate?.let { DomainError.Link.Duplicate(it.id) }
    }
}

object AtMostTwoBiologicalParents : Invariant {
    private const val LIMIT = 2

    override fun check(tree: FamilyTree, change: Change): DomainError? {
        val relation = (change as? Change.AddRelation)?.relation as? ParentChild ?: return null
        if (!relation.isBiological) return null
        val existing = tree.biologicalParentsOf(relation.child).filter { it != relation.parent }
        return if (existing.size >= LIMIT) DomainError.Link.TooManyBiologicalParents(relation.child) else null
    }
}

object NoCycles : Invariant {
    override fun check(tree: FamilyTree, change: Change): DomainError? {
        val relation = (change as? Change.AddRelation)?.relation as? ParentChild ?: return null
        val path = tree.ancestryPath(from = relation.parent, to = relation.child) ?: return null
        return DomainError.Link.CycleDetected(path + relation.parent)
    }
}

object Invariants {
    val all: List<Invariant> = listOf(
        NoSelfRelation,
        NoDuplicateRelation,
        AtMostTwoBiologicalParents,
        NoCycles,
    )

    fun check(tree: FamilyTree, change: Change): DomainError? =
        all.firstNotNullOfOrNull { it.check(tree, change) }

    fun warningsFor(tree: FamilyTree, change: Change): List<ValidationWarning> =
        ChronologyPlausible.check(tree, change)
}
