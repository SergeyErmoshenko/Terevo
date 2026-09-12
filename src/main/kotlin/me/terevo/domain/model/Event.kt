package me.terevo.domain.model

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

data class EventParticipant(val personId: PersonId, val role: String)

class Event private constructor(
    val id: EventId,
    val type: String,
    val date: EventDate,
    val place: Place?,
    val participants: PersistentList<EventParticipant>,
    val notes: String,
) {
    fun with(
        type: String = this.type,
        date: EventDate = this.date,
        place: Place? = this.place,
        participants: List<EventParticipant> = this.participants,
        notes: String = this.notes,
    ): Outcome<Event> = of(id, type, date, place, participants, notes)

    override fun equals(other: Any?): Boolean =
        other is Event &&
                id == other.id &&
                type == other.type &&
                date == other.date &&
                place == other.place &&
                participants == other.participants &&
                notes == other.notes

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = result * PRIME + type.hashCode()
        result = result * PRIME + date.hashCode()
        result = result * PRIME + place.hashCode()
        result = result * PRIME + participants.hashCode()
        result = result * PRIME + notes.hashCode()
        return result
    }

    override fun toString(): String = "Event($id, $type)"

    companion object {
        private const val PRIME = 31

        fun of(
            id: EventId = EventId.next(),
            type: String,
            date: EventDate = EventDate.Unknown,
            place: Place? = null,
            participants: List<EventParticipant>,
            notes: String = "",
        ): Outcome<Event> {
            if (type.isBlank()) return Outcome.Err(DomainError.Event.BlankType)
            if (participants.isEmpty()) return Outcome.Err(DomainError.Event.NoParticipants)
            val duplicate = participants
                .groupingBy { it.personId }
                .eachCount()
                .entries
                .firstOrNull { it.value > 1 }
            if (duplicate != null) return Outcome.Err(DomainError.Event.DuplicateParticipant(duplicate.key))
            return Outcome.Ok(
                Event(
                    id = id,
                    type = type.trim(),
                    date = date,
                    place = place,
                    participants = participants.toPersistentList(),
                    notes = notes.trim(),
                ),
            )
        }

        val NO_PARTICIPANTS: PersistentList<EventParticipant> = persistentListOf()
    }
}
