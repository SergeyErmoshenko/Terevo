package me.terevo.domain.command

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.invariant.Invariants
import me.terevo.domain.invariant.ValidationWarning
import me.terevo.domain.map
import me.terevo.domain.model.*

data class CommandResult(
    val tree: FamilyTree,
    val inverse: Command,
    val warnings: List<ValidationWarning>,
    val changes: List<Change>,
)

sealed interface Command {
    fun applyTo(tree: FamilyTree): Outcome<CommandResult>
}

data class AddPerson(val person: Person) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> =
        tree.addPerson(person).map {
            CommandResult(
                tree = it,
                inverse = RemovePerson(person.id),
                warnings = emptyList(),
                changes = listOf(Change.AddPerson(person)),
            )
        }
}

data class UpdatePerson(val person: Person) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val previous = tree.person(person.id) ?: return Outcome.Err(DomainError.Missing.Person(person.id))
        return tree.updatePerson(person).map {
            CommandResult(
                tree = it,
                inverse = UpdatePerson(previous),
                warnings = emptyList(),
                changes = listOf(Change.UpdatePerson(person)),
            )
        }
    }
}

data class RemovePerson(val id: PersonId) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val removed = tree.person(id) ?: return Outcome.Err(DomainError.Missing.Person(id))
        val detachedLinks = tree.relationsOf(id)
        val detachedEvents = tree.events.values.filter { event -> event.participants.any { it.personId == id } }
        val restore = Batch(
            listOf(AddPerson(removed)) +
                    detachedLinks.map { AddRelation(it) } +
                    detachedEvents.map { AddEvent(it) },
        )
        return tree.removePerson(id).map {
            CommandResult(
                tree = it,
                inverse = restore,
                warnings = emptyList(),
                changes = detachedLinks.map { Change.RemoveRelation(it.id) } +
                        detachedEvents.map { Change.RemoveEvent(it.id) } +
                        Change.RemovePerson(id),
            )
        }
    }
}

data class AddRelation(val relation: Relation) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val warnings = Invariants.warningsFor(tree, Change.AddRelation(relation))
        return tree.addRelation(relation).map {
            CommandResult(
                tree = it,
                inverse = RemoveRelation(relation.id),
                warnings = warnings,
                changes = listOf(Change.AddRelation(relation)),
            )
        }
    }
}

data class RemoveRelation(val id: RelationId) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val removed = tree.relation(id) ?: return Outcome.Err(DomainError.Missing.Relation(id))
        return tree.removeRelation(id).map {
            CommandResult(
                tree = it,
                inverse = AddRelation(removed),
                warnings = emptyList(),
                changes = listOf(Change.RemoveRelation(id)),
            )
        }
    }
}

data class AddEvent(val event: Event) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> =
        tree.addEvent(event).map {
            CommandResult(
                tree = it,
                inverse = RemoveEvent(event.id),
                warnings = emptyList(),
                changes = listOf(Change.AddEvent(event)),
            )
        }
}

data class UpdateEvent(val event: Event) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val previous = tree.event(event.id) ?: return Outcome.Err(DomainError.Missing.Event(event.id))
        return tree.updateEvent(event).map {
            CommandResult(
                tree = it,
                inverse = UpdateEvent(previous),
                warnings = emptyList(),
                changes = listOf(Change.UpdateEvent(event)),
            )
        }
    }
}

data class RemoveEvent(val id: EventId) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        val removed = tree.event(id) ?: return Outcome.Err(DomainError.Missing.Event(id))
        return tree.removeEvent(id).map {
            CommandResult(
                tree = it,
                inverse = AddEvent(removed),
                warnings = emptyList(),
                changes = listOf(Change.RemoveEvent(id)),
            )
        }
    }
}

data class Batch(val commands: List<Command>) : Command {
    override fun applyTo(tree: FamilyTree): Outcome<CommandResult> {
        var current = tree
        val inverses = mutableListOf<Command>()
        val warnings = mutableListOf<ValidationWarning>()
        val changes = mutableListOf<Change>()
        for (command in commands) {
            when (val step = command.applyTo(current)) {
                is Outcome.Ok -> {
                    current = step.value.tree
                    inverses.add(step.value.inverse)
                    warnings.addAll(step.value.warnings)
                    changes.addAll(step.value.changes)
                }

                is Outcome.Err -> return step
            }
        }
        return Outcome.Ok(
            CommandResult(
                tree = current,
                inverse = Batch(inverses.reversed()),
                warnings = warnings,
                changes = changes,
            ),
        )
    }
}
