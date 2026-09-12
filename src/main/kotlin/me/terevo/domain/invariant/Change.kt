package me.terevo.domain.invariant

import me.terevo.domain.model.*

sealed interface Change {
    data class AddPerson(val person: Person) : Change
    data class UpdatePerson(val person: Person) : Change
    data class RemovePerson(val id: PersonId) : Change
    data class AddRelation(val relation: Relation) : Change
    data class RemoveRelation(val id: RelationId) : Change
    data class AddEvent(val event: Event) : Change
    data class UpdateEvent(val event: Event) : Change
    data class RemoveEvent(val id: EventId) : Change
}
