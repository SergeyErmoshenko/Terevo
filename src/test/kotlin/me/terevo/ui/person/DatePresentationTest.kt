package me.terevo.ui.person

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DatePresentationTest {

    @Test
    fun `date formats and parses as day month year`() {
        val date = LocalDate(2000, 10, 2)
        val expected = listOf("02", "10", "2000").joinToString(".")

        assertEquals(expected, date.toDisplayDate())
        assertEquals(date, parseDisplayDate(expected))
    }

    @Test
    fun `parser rejects ISO and invalid calendar dates`() {
        assertFailsWith<IllegalArgumentException> { parseDisplayDate("2000-10-02") }
        assertFailsWith<IllegalArgumentException> { parseDisplayDate(listOf("31", "02", "2000").joinToString(".")) }
    }
}
