package me.terevo.statistics

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.isKnown

data class NamedCount(val name: String, val count: Int)

data class Statistics(
    val totalPersons: Int,
    val livingCount: Int,
    val deceasedCount: Int,
    val averageLifespanYears: Double?,
    val lifespanSampleSize: Int,
    val generationDistribution: Map<Int, Int>,
    val topSurnames: List<NamedCount>,
    val topPlaces: List<NamedCount>,
    val missingBirthDateCount: Int,
    val missingParentsCount: Int,
)

object TreeStatistics {

    fun compute(tree: FamilyTree, topN: Int = 10): Statistics {
        val persons = tree.persons.values
        val lifespans = persons.mapNotNull { it.lifeSpan.ageAtDeath }
        val surnames = persons.map { it.name.surname }.filter { it.isNotEmpty() }
        val places = persons.flatMap { listOfNotNull(it.birthPlace, it.deathPlace) }.map { it.title }

        return Statistics(
            totalPersons = persons.size,
            livingCount = persons.count { it.isAlive },
            deceasedCount = persons.count { !it.isAlive },
            averageLifespanYears = lifespans.takeIf { it.isNotEmpty() }?.average(),
            lifespanSampleSize = lifespans.size,
            generationDistribution = assignGenerations(tree).values.groupingBy { it }.eachCount(),
            topSurnames = surnames.toRankedCaseInsensitive(topN),
            topPlaces = places.toRankedCaseInsensitive(topN),
            missingBirthDateCount = persons.count { !it.lifeSpan.birth.isKnown },
            missingParentsCount = persons.count { tree.parentsOf(it.id).isEmpty() },
        )
    }

    private fun List<String>.toRankedCaseInsensitive(topN: Int): List<NamedCount> =
        groupBy { it.lowercase() }
            .map { (key, values) -> NamedCount(values.first(), values.size) to key }
            .sortedWith(compareByDescending<Pair<NamedCount, String>> { it.first.count }.thenBy { it.second })
            .take(topN)
            .map { it.first }

    private fun assignGenerations(tree: FamilyTree): Map<PersonId, Int> {
        val remainingParents = tree.persons.keys.associateWith { tree.parentsOf(it).size }.toMutableMap()
        val generations = tree.persons.keys.associateWith { 0 }.toMutableMap()
        val queue = ArrayDeque(tree.persons.keys.filter { remainingParents.getValue(it) == 0 })
        val processed = mutableSetOf<PersonId>()
        while (queue.isNotEmpty()) {
            val parent = queue.removeFirst()
            if (!processed.add(parent)) continue
            for (child in tree.childrenOf(parent)) {
                generations[child] = maxOf(generations.getValue(child), generations.getValue(parent) + 1)
                val remaining = remainingParents.getValue(child) - 1
                remainingParents[child] = remaining
                if (remaining == 0) queue.addLast(child)
            }
        }
        return generations
    }
}
