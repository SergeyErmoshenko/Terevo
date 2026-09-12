package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.getOrNull
import me.terevo.domain.model.*
import me.terevo.persistence.db.Event as EventRow
import me.terevo.persistence.db.Event_participant as EventParticipantRow

data class EventColumns(
    val id: String,
    val type: String,
    val date: EncodedDate,
    val place: String?,
    val placeLatitude: Double?,
    val placeLongitude: Double?,
    val notes: String,
)

object EventMapper {

    fun toColumns(event: Event): EventColumns = EventColumns(
        id = event.id.toString(),
        type = event.type,
        date = EventDateCodec.encode(event.date),
        place = event.place?.title,
        placeLatitude = event.place?.coordinates?.latitude,
        placeLongitude = event.place?.coordinates?.longitude,
        notes = event.notes,
    )

    fun toDomain(row: EventRow, participantRows: List<EventParticipantRow>): Outcome<Event> {
        val id = parseId(row.id) ?: return corrupted(row.id, "identifier is not a UUID")
        val date = EventDateCodec.decode(
            EncodedDate(row.date_kind, row.date_value, row.date_end, row.date_precision),
        ) ?: return corrupted(row.id, "date is unreadable")
        val place = place(row.place, row.place_latitude, row.place_longitude)
        val participants = mutableListOf<EventParticipant>()
        for (participantRow in participantRows) {
            val personId = parseId(participantRow.person_id)
                ?: return corrupted(row.id, "participant is not a UUID")
            participants.add(EventParticipant(PersonId(personId), participantRow.role))
        }
        return Event.of(
            id = EventId(id),
            type = row.type,
            date = date,
            place = place,
            participants = participants,
            notes = row.notes,
        )
    }

    private fun place(title: String?, latitude: Double?, longitude: Double?): Place? {
        if (title == null) return null
        val coordinates = if (latitude != null && longitude != null) Coordinates(latitude, longitude) else null
        return Place.of(title, coordinates).getOrNull()
    }

    private fun corrupted(id: String, reason: String): Outcome<Nothing> =
        Outcome.Err(DomainError.Storage.CorruptedRecord("event", id, reason))
}
