package me.terevo.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDate
import me.terevo.domain.DomainError
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk

class EventDateTest {

    @Test
    fun `exact date spans a single day`() {
        val date = LocalDate(1900, 5, 10)

        val interval = EventDate.Exact(date).interval

        assertEquals(DateInterval(date, date), interval)
    }

    @Test
    fun `approximate month spans the whole month including leap day`() {
        val around = LocalDate(2000, 2, 15)

        val interval = EventDate.Approximate(around, DatePrecision.MONTH).interval

        assertEquals(DateInterval(LocalDate(2000, 2, 1), LocalDate(2000, 2, 29)), interval)
    }

    @Test
    fun `approximate year spans the whole year`() {
        val interval = EventDate.Approximate(LocalDate(1887, 7, 3), DatePrecision.YEAR).interval

        assertEquals(DateInterval(LocalDate(1887, 1, 1), LocalDate(1887, 12, 31)), interval)
    }

    @Test
    fun `approximate decade spans ten years from the decade start`() {
        val interval = EventDate.Approximate(LocalDate(1995, 6, 6), DatePrecision.DECADE).interval

        assertEquals(DateInterval(LocalDate(1990, 1, 1), LocalDate(1999, 12, 31)), interval)
    }

    @Test
    fun `unknown date has no interval`() {
        assertNull(EventDate.Unknown.interval)
    }

    @Test
    fun `range rejects reversed bounds`() {
        val error = EventDate.Range.of(LocalDate(1910, 1, 1), LocalDate(1900, 1, 1)).shouldBeErr()

        assertTrue(error is DomainError.Date.RangeReversed)
    }

    @Test
    fun `range accepts equal bounds`() {
        val date = LocalDate(1900, 1, 1)

        val range = EventDate.Range.of(date, date).shouldBeOk()

        assertEquals(DateInterval(date, date), range.interval)
    }

    @Test
    fun `earlier date is definitely before later one`() {
        val earlier = EventDate.Exact(LocalDate(1900, 1, 1))
        val later = EventDate.Exact(LocalDate(1900, 1, 2))

        assertTrue(earlier.definitelyBefore(later))
    }

    @Test
    fun `same date is not definitely before itself`() {
        val date = EventDate.Exact(LocalDate(1900, 1, 1))

        assertFalse(date.definitelyBefore(date))
    }

    @Test
    fun `overlapping uncertainty is not definitely before`() {
        val approximate = EventDate.Approximate(LocalDate(1900, 6, 1), DatePrecision.YEAR)
        val exact = EventDate.Exact(LocalDate(1900, 3, 1))

        assertFalse(approximate.definitelyBefore(exact))
        assertTrue(approximate.overlaps(exact))
    }

    @Test
    fun `unknown date is never definitely before and never overlaps`() {
        val exact = EventDate.Exact(LocalDate(1900, 1, 1))

        assertFalse(EventDate.Unknown.definitelyBefore(exact))
        assertFalse(exact.definitelyBefore(EventDate.Unknown))
        assertFalse(EventDate.Unknown.overlaps(exact))
    }
}
