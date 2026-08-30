package me.terevo.domain.invariant

import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.Relation
import me.terevo.domain.model.RelationId

sealed interface Change {
    data class AddPerson(val person: Person) : Change
    data class UpdatePerson(val person: Person) : Change
    data class RemovePerson(val id: PersonId) : Change
    data class AddRelation(val relation: Relation) : Change
    data class RemoveRelation(val id: RelationId) : Change
}
