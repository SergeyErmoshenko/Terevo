package me.terevo.testing

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.getOrElse
import me.terevo.domain.invariant.Change
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.port.TreeRepository

class InMemoryTreeRepository(initial: FamilyTree = FamilyTree.EMPTY) : TreeRepository {

    var stored: FamilyTree = initial
        private set

    val applied: MutableList<Change> = mutableListOf()

    var failure: DomainError? = null

    override fun load(): Outcome<FamilyTree> = failure?.let { Outcome.Err(it) } ?: Outcome.Ok(stored)

    override fun apply(changes: List<Change>): Outcome<Unit> {
        failure?.let { return Outcome.Err(it) }
        var current = stored
        for (change in changes) {
            current = when (change) {
                is Change.AddPerson -> current.addPerson(change.person).getOrElse { current }
                is Change.UpdatePerson -> current.updatePerson(change.person).getOrElse { current }
                is Change.RemovePerson -> current.removePerson(change.id).getOrElse { current }
                is Change.AddRelation -> current.addRelation(change.relation).getOrElse { current }
                is Change.RemoveRelation -> current.removeRelation(change.id).getOrElse { current }
            }
        }
        stored = current
        applied.addAll(changes)
        return Outcome.Ok(Unit)
    }
}
