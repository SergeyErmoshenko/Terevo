package me.terevo.domain

import kotlin.test.*

class OutcomeTest {

    @Test
    fun `map transforms the value of a successful outcome`() {
        val result = Outcome.Ok(2).map { it * 3 }

        assertEquals(Outcome.Ok(6), result)
    }

    @Test
    fun `map keeps the error untouched`() {
        val failure: Outcome<Int> = Outcome.Err(DomainError.Name.Blank)

        val result = failure.map { it * 3 }

        assertEquals(DomainError.Name.Blank, result.errorOrNull())
    }

    @Test
    fun `flat map chains successful steps`() {
        val result = Outcome.Ok(2).flatMap { Outcome.Ok(it + 1) }.flatMap { Outcome.Ok(it * 2) }

        assertEquals(6, result.getOrNull())
    }

    @Test
    fun `flat map stops at the first failure`() {
        var reached = false

        val result = Outcome.Ok(2)
            .flatMap<Int, Int> { Outcome.Err(DomainError.Place.Blank) }
            .flatMap { reached = true; Outcome.Ok(it) }

        assertFalse(reached)
        assertEquals(DomainError.Place.Blank, result.errorOrNull())
    }

    @Test
    fun `get or else uses the fallback only on failure`() {
        val failure: Outcome<Int> = Outcome.Err(DomainError.Media.Duplicate)

        assertEquals(7, Outcome.Ok(7).getOrElse { 0 })
        assertEquals(0, failure.getOrElse { 0 })
    }

    @Test
    fun `accessors distinguish success from failure`() {
        val success: Outcome<String> = "готово".asOk()
        val failure: Outcome<String> = DomainError.Name.Blank.asErr()

        assertTrue(success.isOk)
        assertFalse(failure.isOk)
        assertEquals("готово", success.getOrNull())
        assertNull(failure.getOrNull())
        assertNull(success.errorOrNull())
    }
}
