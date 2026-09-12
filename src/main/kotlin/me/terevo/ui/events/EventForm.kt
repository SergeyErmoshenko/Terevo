package me.terevo.ui.events

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.flatMap
import me.terevo.domain.model.*
import me.terevo.ui.person.*

data class EventParticipantInput(
    val personId: PersonId? = null,
    val query: String = "",
    val role: String = "",
) {
    fun filteredPeople(people: List<Person>): List<Person> {
        val normalized = query.trim().lowercase()
        return people.filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
    }
}

data class EventFormState(
    val id: EventId = EventId.next(),
    val type: String = "",
    val date: EventDateInput = EventDateInput(),
    val place: String = "",
    val notes: String = "",
    val participants: List<EventParticipantInput> = listOf(EventParticipantInput()),
    val people: List<Person> = emptyList(),
    val original: Event? = null,
    val blockingError: String? = null,
) {
    val canSave: Boolean get() = blockingError == null && type.isNotBlank() && participants.any { it.personId != null }

    companion object {
        fun fromEvent(event: Event, people: List<Person>): EventFormState = EventFormState(
            id = event.id,
            type = event.type,
            date = event.date.toInput(),
            place = event.place?.title.orEmpty(),
            notes = event.notes,
            participants = event.participants.map { EventParticipantInput(personId = it.personId, role = it.role) },
            people = people,
            original = event,
        )
    }
}

fun EventFormState.toEvent(): Outcome<Event> {
    val participantList = participants.mapNotNull { input ->
        input.personId?.let { EventParticipant(it, input.role.trim()) }
    }
    return date.toEventDate().flatMap { eventDate ->
        placeOf(place).flatMap { eventPlace ->
            Event.of(
                id = id,
                type = type,
                date = eventDate,
                place = eventPlace,
                participants = participantList,
                notes = notes,
            )
        }
    }
}

fun validateEventForm(state: EventFormState): EventFormState = when (val event = state.toEvent()) {
    is Outcome.Ok -> state.copy(blockingError = null)
    is Outcome.Err -> state.copy(blockingError = event.error.toEventRussianMessage())
}

fun DomainError.toEventRussianMessage(): String = when (this) {
    DomainError.Event.BlankType -> "Укажите тип события"
    DomainError.Event.NoParticipants -> "Добавьте хотя бы одного участника"
    is DomainError.Event.DuplicateParticipant -> "Этот участник уже добавлен"
    else -> toRussianMessage()
}
