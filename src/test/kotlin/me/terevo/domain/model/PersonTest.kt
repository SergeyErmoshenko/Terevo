package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.testing.name
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PersonTest {

    @Test
    fun `blank custom field key is rejected`() {
        val error = Person.create(name = name(), customFields = mapOf("  " to "значение")).shouldBeErr()

        assertTrue(error is DomainError.CustomField.BlankKey)
    }

    @Test
    fun `maiden name is rejected for non-female person`() {
        val error = Person.create(
            name = name().with(maidenName = "Петрова").shouldBeOk(),
            gender = Gender.MALE,
        ).shouldBeErr()

        assertEquals(DomainError.Name.MaidenNameRequiresFemaleGender, error)
    }

    @Test
    fun `maiden name is accepted for female person`() {
        val created = Person.create(
            name = name().with(maidenName = "Петрова").shouldBeOk(),
            gender = Gender.FEMALE,
        ).shouldBeOk()

        assertEquals("Петрова", created.name.maidenName)
    }

    @Test
    fun `custom field keys are trimmed`() {
        val created = Person.create(name = name(), customFields = mapOf(" Профессия " to "врач")).shouldBeOk()

        assertEquals(mapOf("Профессия" to "врач"), created.customFields)
    }

    @Test
    fun `repeated media attachment is rejected`() {
        val media = MediaId.next()

        val error = Person.create(name = name(), mediaIds = listOf(media, media)).shouldBeErr()

        assertTrue(error is DomainError.Media.Duplicate)
    }

    @Test
    fun `notes are trimmed`() {
        val created = Person.create(name = name(), notes = "  запись  ").shouldBeOk()

        assertEquals("запись", created.notes)
    }

    @Test
    fun `occupation is trimmed`() {
        val created = Person.create(name = name(), occupation = "  врач  ").shouldBeOk()

        assertEquals("врач", created.occupation)
    }

    @Test
    fun `with replaces residence and occupation`() {
        val original = person(surname = "Иванов")
        val residence = Place.of("Москва").shouldBeOk()

        val updated = original.with(residence = residence, occupation = "инженер").shouldBeOk()

        assertEquals(residence, updated.residence)
        assertEquals("инженер", updated.occupation)
    }

    @Test
    fun `with keeps the identifier and replaces the field`() {
        val original = person(surname = "Иванов")

        val renamed = original.with(name = name(surname = "Петров")).shouldBeOk()

        assertEquals(original.id, renamed.id)
        assertEquals("Петров", renamed.name.surname)
        assertNotEquals(original, renamed)
    }

    @Test
    fun `with revalidates custom fields`() {
        val error = person().with(customFields = mapOf("" to "значение")).shouldBeErr()

        assertTrue(error is DomainError.CustomField.BlankKey)
    }

    @Test
    fun `people with equal content are equal`() {
        val id = PersonId.next()

        val first = person(id = id, surname = "Иванов", born = 1900)
        val second = person(id = id, surname = "Иванов", born = 1900)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `person without death date is alive`() {
        assertTrue(person(born = 1980).isAlive)
    }
}
