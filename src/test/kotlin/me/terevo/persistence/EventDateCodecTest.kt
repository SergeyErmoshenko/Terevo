package me.terevo.persistence

import kotlinx.datetime.LocalDate
import me.terevo.domain.model.DatePrecision
import me.terevo.domain.model.EventDate
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventDateCodecTest {

    @Test
    fun `exact date survives a round trip`() {
        val date: EventDate = EventDate.Exact(LocalDate(1900, 5, 10))

        assertEquals(date, EventDateCodec.decode(EventDateCodec.encode(date)))
    }

    @Test
    fun `approximate date survives a round trip with its precision`() {
        val date: EventDate = EventDate.Approximate(LocalDate(1995, 6, 6), DatePrecision.DECADE)

        assertEquals(date, EventDateCodec.decode(EventDateCodec.encode(date)))
    }

    @Test
    fun `range survives a round trip`() {
        val date = EventDate.Range.of(LocalDate(1900, 1, 1), LocalDate(1910, 1, 1)).shouldBeOk()

        assertEquals(date, EventDateCodec.decode(EventDateCodec.encode(date)))
    }

    @Test
    fun `unknown date survives a round trip`() {
        assertEquals(EventDate.Unknown, EventDateCodec.decode(EventDateCodec.encode(EventDate.Unknown)))
    }

    @Test
    fun `unknown kind is not decoded`() {
        assertNull(EventDateCodec.decode(EncodedDate("WHATEVER", "1900-01-01", null, null)))
    }

    @Test
    fun `malformed date is not decoded`() {
        assertNull(EventDateCodec.decode(EncodedDate("EXACT", "не дата", null, null)))
    }

    @Test
    fun `approximate without precision is not decoded`() {
        assertNull(EventDateCodec.decode(EncodedDate("APPROXIMATE", "1900-01-01", null, null)))
    }

    @Test
    fun `reversed range is not decoded`() {
        assertNull(EventDateCodec.decode(EncodedDate("RANGE", "1910-01-01", "1900-01-01", null)))
    }
}
