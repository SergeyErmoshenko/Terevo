package me.terevo.domain.model

import kotlinx.datetime.LocalDate
import me.terevo.domain.DomainError
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import me.terevo.testing.year
import kotlin.test.*

class RelationTest {

    @Test
    fun `person cannot be their own parent`() {
        val person = PersonId.next()

        val error = ParentChild.of(parent = person, child = person).shouldBeErr()

        assertTrue(error is DomainError.Link.SelfRelation)
    }

    @Test
    fun `person cannot marry themselves`() {
        val person = PersonId.next()

        val error = Marriage.of(first = person, second = person).shouldBeErr()

        assertTrue(error is DomainError.Link.SelfRelation)
    }

    @Test
    fun `marriage normalizes the order of spouses`() {
        val first = PersonId.next()
        val second = PersonId.next()

        val direct = Marriage.of(first = first, second = second).shouldBeOk()
        val reversed = Marriage.of(first = second, second = first).shouldBeOk()

        assertEquals(direct.spouseA, reversed.spouseA)
        assertEquals(direct.spouseB, reversed.spouseB)
        assertTrue(direct.sameLinkAs(reversed))
    }

    @Test
    fun `marriage ending before it started is rejected`() {
        val error = Marriage.of(
            first = PersonId.next(),
            second = PersonId.next(),
            since = year(1920),
            until = year(1910),
        ).shouldBeErr()

        assertTrue(error is DomainError.Link.MarriageEndsBeforeStart)
    }

    @Test
    fun `marriage with unknown dates is accepted`() {
        val marriage = Marriage.of(first = PersonId.next(), second = PersonId.next()).shouldBeOk()

        assertEquals(MarriageStatus.MARRIED, marriage.status)
        assertEquals(EventDate.Unknown, marriage.since)
    }

    @Test
    fun `spouse lookup returns the other participant`() {
        val first = PersonId.next()
        val second = PersonId.next()
        val stranger = PersonId.next()

        val marriage = Marriage.of(first = first, second = second).shouldBeOk()

        assertEquals(second, marriage.spouseOf(first))
        assertEquals(first, marriage.spouseOf(second))
        assertNull(marriage.spouseOf(stranger))
    }

    @Test
    fun `parent child relation knows its participants`() {
        val parent = PersonId.next()
        val child = PersonId.next()

        val relation = ParentChild.of(parent = parent, child = child).shouldBeOk()

        assertTrue(relation.involves(parent))
        assertTrue(relation.involves(child))
        assertFalse(relation.involves(PersonId.next()))
        assertTrue(relation.isBiological)
    }

    @Test
    fun `adoptive relation is not biological`() {
        val relation = ParentChild.of(
            parent = PersonId.next(),
            child = PersonId.next(),
            kind = ParentKind.ADOPTIVE,
        ).shouldBeOk()

        assertFalse(relation.isBiological)
    }

    @Test
    fun `same link ignores identifiers and kind`() {
        val parent = PersonId.next()
        val child = PersonId.next()

        val biological = ParentChild.of(parent = parent, child = child).shouldBeOk()
        val adoptive = ParentChild.of(parent = parent, child = child, kind = ParentKind.ADOPTIVE).shouldBeOk()

        assertTrue(biological.sameLinkAs(adoptive))
    }

    @Test
    fun `parent child and marriage are never the same link`() {
        val first = PersonId.next()
        val second = PersonId.next()

        val parentChild = ParentChild.of(parent = first, child = second).shouldBeOk()
        val marriage = Marriage.of(first = first, second = second).shouldBeOk()

        assertFalse(parentChild.sameLinkAs(marriage))
    }

    @Test
    fun `marriage keeps an explicit end date`() {
        val marriage = Marriage.of(
            first = PersonId.next(),
            second = PersonId.next(),
            since = EventDate.Exact(LocalDate(1910, 5, 1)),
            until = EventDate.Exact(LocalDate(1930, 6, 2)),
            status = MarriageStatus.DIVORCED,
        ).shouldBeOk()

        assertEquals(MarriageStatus.DIVORCED, marriage.status)
        assertEquals(EventDate.Exact(LocalDate(1930, 6, 2)), marriage.until)
    }

    @Test
    fun `marriage keeps its place and with replaces it`() {
        val place = Place.of("Москва").shouldBeOk()

        val marriage = Marriage.of(first = PersonId.next(), second = PersonId.next(), place = place).shouldBeOk()

        assertEquals(place, marriage.place)

        val otherPlace = Place.of("Санкт-Петербург").shouldBeOk()
        val updated = marriage.with(place = otherPlace).shouldBeOk()

        assertEquals(otherPlace, updated.place)
    }
}
