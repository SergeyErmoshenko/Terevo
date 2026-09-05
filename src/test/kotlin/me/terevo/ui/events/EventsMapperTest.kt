package me.terevo.ui.events

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.*
import me.terevo.domain.port.MediaRepository
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class EventsMapperTest {
    private val today = LocalDate(2026, 9, 5)

    @Test
    fun `birth wedding and death events appear in the list`() {
        val alive = person(surname = "Иванов", givenName = "Иван", born = 2000)
        val deceased = person(surname = "Петров", givenName = "Пётр", born = 1930, died = 2010)
        val spouseA = person(surname = "Сидоров", givenName = "Сидор", born = 1990)
        val spouseB = person(surname = "Сидорова", givenName = "Мария", born = 1992)
        val marriage = Marriage.of(
            first = spouseA.id,
            second = spouseB.id,
            since = EventDate.Exact(LocalDate(2015, 6, 1)),
        ).shouldBeOk()
        val tree = FamilyTree.of(listOf(alive, deceased, spouseA, spouseB), listOf(marriage)).shouldBeOk()

        val rows = mapEventRows(tree, MediaRepository.NONE, today)

        assertTrue(rows.any { it.type == EventType.BIRTH && it.participants == "Иванов Иван" })
        assertTrue(rows.any { it.type == EventType.BIRTH && it.participants == "Петров Пётр" })
        assertTrue(rows.any { it.type == EventType.DEATH && it.participants == "Петров Пётр" })
        val wedding = assertNotNull(rows.singleOrNull { it.type == EventType.WEDDING })
        assertTrue(wedding.participants.contains("Сидоров Сидор"))
        assertTrue(wedding.participants.contains("Сидорова Мария"))
    }

    @Test
    fun `persons and marriages with unknown dates are excluded`() {
        val unknown = person(surname = "Сидоров", givenName = "Сидор")
        val partner = person(surname = "Кузнецов", givenName = "Кузьма")
        val marriage = Marriage.of(first = unknown.id, second = partner.id).shouldBeOk()
        val tree = FamilyTree.of(listOf(unknown, partner), listOf(marriage)).shouldBeOk()

        val rows = mapEventRows(tree, MediaRepository.NONE, today)

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `days until anniversary and years passed are only computed for exact dates`() {
        val exact = person(surname = "Иванов", givenName = "Иван", born = 2000)
        val approximate = person(surname = "Петров", givenName = "Пётр").with(
            lifeSpan = LifeSpan.of(
                birth = EventDate.Approximate(LocalDate(1995, 1, 1), DatePrecision.YEAR),
                death = EventDate.Unknown,
            ).shouldBeOk(),
        ).shouldBeOk()
        val tree = FamilyTree.of(listOf(exact, approximate), emptyList()).shouldBeOk()

        val rows = mapEventRows(tree, MediaRepository.NONE, today).associateBy { it.participants }

        assertTrue(rows.getValue("Иванов Иван").daysUntilAnniversary != null)
        assertEquals(26, rows.getValue("Иванов Иван").yearsPassed)
        assertNull(rows.getValue("Петров Пётр").daysUntilAnniversary)
        assertNull(rows.getValue("Петров Пётр").yearsPassed)
    }

    @Test
    fun `rows are sorted by nearest anniversary`() {
        val soon = person(surname = "Иванов", givenName = "Иван").with(
            lifeSpan = LifeSpan.of(birth = EventDate.Exact(LocalDate(1990, 9, 6)), death = EventDate.Unknown)
                .shouldBeOk(),
        ).shouldBeOk()
        val later = person(surname = "Петров", givenName = "Пётр").with(
            lifeSpan = LifeSpan.of(birth = EventDate.Exact(LocalDate(1990, 12, 25)), death = EventDate.Unknown)
                .shouldBeOk(),
        ).shouldBeOk()
        val tree = FamilyTree.of(listOf(later, soon), emptyList()).shouldBeOk()

        val rows = mapEventRows(tree, MediaRepository.NONE, today)

        assertEquals(listOf("Иванов Иван", "Петров Пётр"), rows.map { it.participants })
    }
}
