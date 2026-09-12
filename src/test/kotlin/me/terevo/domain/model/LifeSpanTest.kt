package me.terevo.domain.model

import kotlinx.datetime.LocalDate
import me.terevo.domain.DomainError
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class LifeSpanTest {

    @Test
    fun `death strictly before birth is rejected`() {
        val error = LifeSpan.of(
            birth = EventDate.Exact(LocalDate(1900, 1, 1)),
            death = EventDate.Exact(LocalDate(1890, 1, 1)),
        ).shouldBeErr()

        assertTrue(error is DomainError.Date.DeathBeforeBirth)
    }

    @Test
    fun `death within the uncertainty of birth is accepted`() {
        val span = LifeSpan.of(
            birth = EventDate.Approximate(LocalDate(1900, 6, 1), DatePrecision.YEAR),
            death = EventDate.Exact(LocalDate(1900, 3, 1)),
        ).shouldBeOk()

        assertFalse(span.isAlive)
    }

    @Test
    fun `unknown death means alive`() {
        val span = LifeSpan.of(EventDate.Exact(LocalDate(1980, 1, 1)), EventDate.Unknown).shouldBeOk()

        assertTrue(span.isAlive)
        assertFalse(span.hasBothDates)
    }

    @Test
    fun `age is not incremented before the birthday`() {
        val span = LifeSpan.of(EventDate.Exact(LocalDate(1900, 5, 10)), EventDate.Unknown).shouldBeOk()

        assertEquals(49, span.ageAt(LocalDate(1950, 5, 9)))
        assertEquals(50, span.ageAt(LocalDate(1950, 5, 10)))
    }

    @Test
    fun `age before birth is undefined`() {
        val span = LifeSpan.of(EventDate.Exact(LocalDate(1900, 5, 10)), EventDate.Unknown).shouldBeOk()

        assertNull(span.ageAt(LocalDate(1899, 1, 1)))
    }

    @Test
    fun `age is undefined without a birth date`() {
        assertNull(LifeSpan.UNKNOWN.ageAt(LocalDate(2000, 1, 1)))
        assertNull(LifeSpan.UNKNOWN.ageAtDeath)
    }

    @Test
    fun `age at death uses both dates`() {
        val span = LifeSpan.of(
            birth = EventDate.Exact(LocalDate(1900, 5, 10)),
            death = EventDate.Exact(LocalDate(1975, 5, 9)),
        ).shouldBeOk()

        assertEquals(74, span.ageAtDeath)
        assertTrue(span.hasBothDates)
    }

    @Test
    fun `spans with equal dates are equal`() {
        val birth = EventDate.Exact(LocalDate(1900, 1, 1))

        val first = LifeSpan.of(birth, EventDate.Unknown).shouldBeOk()
        val second = LifeSpan.of(birth, EventDate.Unknown).shouldBeOk()

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
