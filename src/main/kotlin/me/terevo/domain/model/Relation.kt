package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

enum class ParentKind {
    BIOLOGICAL,
    ADOPTIVE,
    STEP,
    FOSTER,
}

enum class MarriageStatus {
    MARRIED,
    DIVORCED,
    WIDOWED,
    PARTNERS,
}

sealed interface Relation {
    val id: RelationId
    val participants: Set<PersonId>

    fun involves(person: PersonId): Boolean = person in participants
}

class ParentChild private constructor(
    override val id: RelationId,
    val parent: PersonId,
    val child: PersonId,
    val kind: ParentKind,
) : Relation {
    override val participants: Set<PersonId> get() = setOf(parent, child)

    val isBiological: Boolean get() = kind == ParentKind.BIOLOGICAL

    fun with(kind: ParentKind): Outcome<ParentChild> = of(id, parent, child, kind)

    override fun equals(other: Any?): Boolean =
        other is ParentChild &&
                id == other.id &&
                parent == other.parent &&
                child == other.child &&
                kind == other.kind

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = result * PRIME + parent.hashCode()
        result = result * PRIME + child.hashCode()
        result = result * PRIME + kind.hashCode()
        return result
    }

    override fun toString(): String = "ParentChild($parent -> $child, $kind)"

    companion object {
        private const val PRIME = 31

        fun of(
            id: RelationId = RelationId.next(),
            parent: PersonId,
            child: PersonId,
            kind: ParentKind = ParentKind.BIOLOGICAL,
        ): Outcome<ParentChild> =
            if (parent == child) {
                Outcome.Err(DomainError.Link.SelfRelation(parent))
            } else {
                Outcome.Ok(ParentChild(id, parent, child, kind))
            }
    }
}

class Marriage private constructor(
    override val id: RelationId,
    val spouseA: PersonId,
    val spouseB: PersonId,
    val since: EventDate,
    val until: EventDate,
    val status: MarriageStatus,
    val place: Place?,
) : Relation {
    override val participants: Set<PersonId> get() = setOf(spouseA, spouseB)

    fun spouseOf(person: PersonId): PersonId? = when (person) {
        spouseA -> spouseB
        spouseB -> spouseA
        else -> null
    }

    fun with(
        since: EventDate = this.since,
        until: EventDate = this.until,
        status: MarriageStatus = this.status,
        place: Place? = this.place,
    ): Outcome<Marriage> = of(id, spouseA, spouseB, since, until, status, place)

    override fun equals(other: Any?): Boolean =
        other is Marriage &&
                id == other.id &&
                spouseA == other.spouseA &&
                spouseB == other.spouseB &&
                since == other.since &&
                until == other.until &&
                status == other.status &&
                place == other.place

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = result * PRIME + spouseA.hashCode()
        result = result * PRIME + spouseB.hashCode()
        result = result * PRIME + since.hashCode()
        result = result * PRIME + until.hashCode()
        result = result * PRIME + status.hashCode()
        result = result * PRIME + place.hashCode()
        return result
    }

    override fun toString(): String = "Marriage($spouseA <-> $spouseB, $status)"

    companion object {
        private const val PRIME = 31

        @Suppress("LongParameterList")
        fun of(
            id: RelationId = RelationId.next(),
            first: PersonId,
            second: PersonId,
            since: EventDate = EventDate.Unknown,
            until: EventDate = EventDate.Unknown,
            status: MarriageStatus = MarriageStatus.MARRIED,
            place: Place? = null,
        ): Outcome<Marriage> {
            if (first == second) return Outcome.Err(DomainError.Link.SelfRelation(first))
            if (until.definitelyBefore(since)) {
                return Outcome.Err(DomainError.Link.MarriageEndsBeforeStart(since, until))
            }
            val ordered = if (first.value <= second.value) first to second else second to first
            return Outcome.Ok(Marriage(id, ordered.first, ordered.second, since, until, status, place))
        }
    }
}

fun Relation.sameLinkAs(other: Relation): Boolean = when {
    this is ParentChild && other is ParentChild -> parent == other.parent && child == other.child
    this is Marriage && other is Marriage -> spouseA == other.spouseA && spouseB == other.spouseB
    else -> false
}
