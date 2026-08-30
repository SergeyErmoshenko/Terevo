package me.terevo.domain

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.RelationId

sealed interface DomainError {

    sealed interface Missing : DomainError {
        data class Person(val id: PersonId) : Missing
        data class Relation(val id: RelationId) : Missing
    }

    sealed interface Storage : DomainError {
        data class Failure(val reason: String) : Storage
        data class CorruptedRecord(val table: String, val id: String, val reason: String) : Storage
    }

    sealed interface Project : DomainError {
        data class AlreadyExists(val path: String) : Project
        data class NotFound(val path: String) : Project
        data class NotAProject(val path: String) : Project
        data class Corrupted(val path: String, val reason: String) : Project
        data class SchemaTooNew(val found: Long, val supported: Long) : Project
        data class Locked(val path: String) : Project
    }

    sealed interface History : DomainError {
        data object NothingToUndo : History
        data object NothingToRedo : History
    }

    sealed interface Tree : DomainError {
        data class PersonAlreadyExists(val id: PersonId) : Tree
        data class RelationAlreadyExists(val id: RelationId) : Tree
    }

    sealed interface Link : DomainError {
        data class SelfRelation(val person: PersonId) : Link
        data class Duplicate(val existing: RelationId) : Link
        data class CycleDetected(val path: List<PersonId>) : Link
        data class TooManyBiologicalParents(val child: PersonId) : Link
        data class MarriageEndsBeforeStart(val since: EventDate, val until: EventDate) : Link
    }

    sealed interface Name : DomainError {
        data object Blank : Name
    }

    sealed interface Date : DomainError {
        data class RangeReversed(val from: LocalDate, val to: LocalDate) : Date
        data class DeathBeforeBirth(val birth: EventDate, val death: EventDate) : Date
    }

    sealed interface Place : DomainError {
        data object Blank : Place
    }

    sealed interface CustomField : DomainError {
        data object BlankKey : CustomField
    }

    sealed interface Media : DomainError {
        data object Duplicate : Media
    }
}
