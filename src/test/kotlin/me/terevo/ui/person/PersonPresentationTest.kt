package me.terevo.ui.person

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.LifeSpan
import me.terevo.testing.shouldBeOk

class PersonPresentationTest {

    @Test
    fun `unknown dates are readable in details and hidden on card`() {
        assertEquals("Даты жизни не указаны", LifeSpan.UNKNOWN.displayText())
        assertEquals("", LifeSpan.UNKNOWN.cardDates())
    }

    @Test
    fun `known birth without death shows complete date`() {
        val birth = LocalDate(1980, 2, 3)
        val lifeSpan = LifeSpan.of(
            birth = EventDate.Exact(birth),
            death = EventDate.Unknown,
        ).shouldBeOk()
        val expected = listOf(3, 2, 1980).joinToString(".") { it.toString().padStart(if (it == 1980) 4 else 2, '0') }

        assertEquals("Родился: $expected", lifeSpan.displayText())
        assertEquals(expected, lifeSpan.cardDates())
    }
}
