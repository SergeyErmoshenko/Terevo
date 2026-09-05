package me.terevo.ui.persons

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.port.MediaRepository
import me.terevo.testing.person
import me.terevo.testing.place
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonsMapperTest {
    private val today = LocalDate(2026, 9, 5)

    @Test
    fun `age is computed for alive person and for deceased person and unknown for unknown dates`() {
        val alive = person(surname = "Иванов", givenName = "Иван", born = 2000)
        val deceased = person(surname = "Петров", givenName = "Пётр", born = 1930, died = 2010)
        val unknown = person(surname = "Сидоров", givenName = "Сидор")
        val tree = FamilyTree.of(listOf(alive, deceased, unknown), emptyList()).shouldBeOk()

        val rows = mapPersonRows(tree, MediaRepository.NONE, today).associateBy { it.fullName }

        assertEquals("26", rows.getValue("Иванов Иван").age)
        assertEquals("80", rows.getValue("Петров Пётр").age)
        assertEquals("—", rows.getValue("Сидоров Сидор").age)
    }

    @Test
    fun `residence occupation comment and alive status are mapped`() {
        val alive = person(surname = "Иванов", givenName = "Иван")
            .with(residence = place("Казань"), occupation = "врач", notes = "заметка")
            .shouldBeOk()
        val tree = FamilyTree.of(listOf(alive), emptyList()).shouldBeOk()

        val row = mapPersonRows(tree, MediaRepository.NONE, today).single()

        assertEquals("Казань", row.residence)
        assertEquals("врач", row.occupation)
        assertEquals("заметка", row.comment)
        assertTrue(row.alive)
    }

    @Test
    fun `person without media has no thumbnail`() {
        val alive = person(surname = "Иванов", givenName = "Иван")
        val tree = FamilyTree.of(listOf(alive), emptyList()).shouldBeOk()

        val row = mapPersonRows(tree, MediaRepository.NONE, today).single()

        assertNull(row.thumbnailPath)
    }

    @Test
    fun `rows are sorted by full name`() {
        val second = person(surname = "Петров", givenName = "Пётр")
        val first = person(surname = "Иванов", givenName = "Иван")
        val tree = FamilyTree.of(listOf(second, first), emptyList()).shouldBeOk()

        val rows = mapPersonRows(tree, MediaRepository.NONE, today)

        assertEquals(listOf("Иванов Иван", "Петров Пётр"), rows.map { it.fullName })
    }
}
