package me.terevo.statistics

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.testing.largeFamilyTree
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk

class TreeStatisticsTest {

    private class Fixture {
        val greatGrandparent =
            person(surname = "Иванов", born = 1900, died = 1970, birthPlace = "Москва", deathPlace = "Москва")
        val otherRoot =
            person(surname = "Петров", born = 1920, died = 1990, birthPlace = "Санкт-Петербург", deathPlace = "Москва")
        val rootWithoutBirthDate = person(surname = "Петров")
        val lowercaseSurnameRoot = person(surname = "иванов", born = 1930, birthPlace = "москва")
        val child = person(surname = "Иванов", born = 1950, birthPlace = "Москва")
        val grandchild = person(surname = "Сидоров", born = 1980)

        val tree: FamilyTree = FamilyTree.of(
            persons = listOf(
                greatGrandparent,
                otherRoot,
                rootWithoutBirthDate,
                lowercaseSurnameRoot,
                child,
                grandchild
            ),
            relations = listOf(
                ParentChild.of(parent = greatGrandparent.id, child = child.id).shouldBeOk(),
                ParentChild.of(parent = child.id, child = grandchild.id).shouldBeOk(),
            ),
        ).shouldBeOk()
    }

    @Test
    fun `computes person counts living deceased and average lifespan`() {
        val statistics = TreeStatistics.compute(Fixture().tree)
        assertEquals(6, statistics.totalPersons)
        assertEquals(4, statistics.livingCount)
        assertEquals(2, statistics.deceasedCount)
        assertEquals(2, statistics.lifespanSampleSize)
        assertEquals(70.0, statistics.averageLifespanYears)
    }

    @Test
    fun `computes generation distribution across branching tree`() {
        val statistics = TreeStatistics.compute(Fixture().tree)
        assertEquals(mapOf(0 to 4, 1 to 1, 2 to 1), statistics.generationDistribution)
    }

    @Test
    fun `ranks top surnames and places case insensitively`() {
        val statistics = TreeStatistics.compute(Fixture().tree)
        assertEquals(3, statistics.topSurnames.first { it.name.equals("Иванов", ignoreCase = true) }.count)
        assertEquals(2, statistics.topSurnames.first { it.name.equals("Петров", ignoreCase = true) }.count)
        assertEquals(1, statistics.topSurnames.first { it.name.equals("Сидоров", ignoreCase = true) }.count)
        assertEquals(5, statistics.topPlaces.first { it.name.equals("Москва", ignoreCase = true) }.count)
        assertEquals(1, statistics.topPlaces.first { it.name.equals("Санкт-Петербург", ignoreCase = true) }.count)
    }

    @Test
    fun `counts persons missing birth date or parents`() {
        val statistics = TreeStatistics.compute(Fixture().tree)
        assertEquals(1, statistics.missingBirthDateCount)
        assertEquals(4, statistics.missingParentsCount)
    }

    @Test
    fun `statistics over ten thousand people complete under two hundred milliseconds`() {
        val tree = largeFamilyTree(PEOPLE)
        var statistics: Statistics? = null
        val elapsed = measureTimeMillis { statistics = TreeStatistics.compute(tree) }
        assertEquals(PEOPLE, statistics?.totalPersons)
        assertTrue(elapsed < BUDGET_MILLIS, "statistics took $elapsed ms")
    }

    private companion object {
        const val PEOPLE = 10_000
        const val BUDGET_MILLIS = 200L
    }
}
