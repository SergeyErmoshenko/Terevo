package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTest {

    @Test
    fun `blank type is rejected`() {
        val error = Event.of(
            type = "  ",
            participants = listOf(EventParticipant(PersonId.next(), "Участник")),
        ).shouldBeErr()

        assertTrue(error is DomainError.Event.BlankType)
    }

    @Test
    fun `event without participants is rejected`() {
        val error = Event.of(type = "Свадьба", participants = emptyList()).shouldBeErr()

        assertTrue(error is DomainError.Event.NoParticipants)
    }

    @Test
    fun `duplicate participant is rejected`() {
        val person = PersonId.next()

        val error = Event.of(
            type = "Свадьба",
            participants = listOf(EventParticipant(person, "Жених"), EventParticipant(person, "Свидетель")),
        ).shouldBeErr()

        assertTrue(error is DomainError.Event.DuplicateParticipant)
        assertEquals(person, error.person)
    }

    @Test
    fun `valid event keeps its participants and type is trimmed`() {
        val groom = PersonId.next()
        val bride = PersonId.next()
        val witness = PersonId.next()

        val event = Event.of(
            type = " Свадьба ",
            participants = listOf(
                EventParticipant(groom, "Жених"),
                EventParticipant(bride, "Невеста"),
                EventParticipant(witness, "Свидетель"),
            ),
        ).shouldBeOk()

        assertEquals("Свадьба", event.type)
        assertEquals(3, event.participants.size)
    }

    @Test
    fun `with replaces fields and revalidates`() {
        val person = PersonId.next()
        val event = Event.of(
            type = "Юбилей",
            participants = listOf(EventParticipant(person, "Именинник")),
        ).shouldBeOk()

        val updated = event.with(type = "Выпускной", notes = "Заметка").shouldBeOk()

        assertEquals(event.id, updated.id)
        assertEquals("Выпускной", updated.type)
        assertEquals("Заметка", updated.notes)

        val error = event.with(type = " ").shouldBeErr()
        assertTrue(error is DomainError.Event.BlankType)
    }
}
