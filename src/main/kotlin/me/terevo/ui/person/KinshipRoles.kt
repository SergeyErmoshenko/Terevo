package me.terevo.ui.person

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId

data class RelatedPerson(
    val person: Person,
    val role: String,
)

object KinshipRoles {
    fun resolve(tree: FamilyTree, selected: PersonId): List<RelatedPerson> {
        val ancestorDistances = tree.distancesFrom(selected, FamilyTree::parentsOf)
        val descendantDistances = tree.distancesFrom(selected, FamilyTree::childrenOf)
        return tree.persons.values.asSequence()
            .filter { it.id != selected }
            .mapNotNull { person ->
                roleOf(tree, selected, person, ancestorDistances, descendantDistances)?.let { RelatedPerson(person, it) }
            }
            .sortedWith(compareBy(RelatedPerson::role, { it.person.name.sortKey }))
            .toList()
    }

    private fun roleOf(
        tree: FamilyTree,
        selected: PersonId,
        relative: Person,
        ancestorDistances: Map<PersonId, Int>,
        descendantDistances: Map<PersonId, Int>,
    ): String? {
        ancestorDistances[relative.id]?.let { return relative.gender.ancestorRole(it) }
        descendantDistances[relative.id]?.let { return relative.gender.descendantRole(it) }
        val parents = tree.parentsOf(selected)
        return when {
            tree.areSpouses(selected, relative.id) -> relative.gender.spouseRole()
            relative.id in tree.siblingsOf(selected) -> relative.gender.siblingRole()
            relative.id in parents.flatMap(tree::siblingsOf) -> relative.gender.uncleRole()
            selected in tree.parentsOf(relative.id).flatMap(tree::siblingsOf) -> relative.gender.niblingRole()
            relative.id in parents.flatMap(tree::siblingsOf).flatMap(tree::childrenOf) -> relative.gender.cousinRole()
            else -> null
        }
    }
}

private fun FamilyTree.distancesFrom(
    start: PersonId,
    neighbors: FamilyTree.(PersonId) -> List<PersonId>,
): Map<PersonId, Int> {
    val distances = mutableMapOf<PersonId, Int>()
    val queue = ArrayDeque<Pair<PersonId, Int>>()
    queue.addLast(start to 0)
    while (queue.isNotEmpty()) {
        val (current, distance) = queue.removeFirst()
        for (neighbor in neighbors(current)) {
            if (neighbor == start || neighbor in distances) continue
            distances[neighbor] = distance + 1
            queue.addLast(neighbor to distance + 1)
        }
    }
    return distances
}

private fun FamilyTree.parentsOf(person: PersonId): List<PersonId> = relations.values
    .filterIsInstance<ParentChild>()
    .filter { it.child == person }
    .map(ParentChild::parent)

private fun FamilyTree.childrenOf(person: PersonId): List<PersonId> = relations.values
    .filterIsInstance<ParentChild>()
    .filter { it.parent == person }
    .map(ParentChild::child)

private fun FamilyTree.siblingsOf(person: PersonId): List<PersonId> {
    val parents = parentsOf(person).toSet()
    if (parents.isEmpty()) return emptyList()
    return persons.keys.filter { candidate -> candidate != person && parentsOf(candidate).any { it in parents } }
}

private fun FamilyTree.areSpouses(first: PersonId, second: PersonId): Boolean = relations.values
    .filterIsInstance<Marriage>()
    .any { first in it.participants && second in it.participants }

private fun Gender.ancestorRole(distance: Int): String = when (distance) {
    1 -> when (this) {
        Gender.MALE -> "Папа"
        Gender.FEMALE -> "Мама"
        Gender.UNKNOWN -> "Родитель"
    }
    2 -> grandparentRole()
    else -> "${"пра".repeat(distance - 2)}${grandparentRole().lowercase()}".replaceFirstChar(Char::uppercase)
}

private fun Gender.descendantRole(distance: Int): String = when (distance) {
    1 -> when (this) {
        Gender.MALE -> "Сын"
        Gender.FEMALE -> "Дочь"
        Gender.UNKNOWN -> "Ребёнок"
    }
    2 -> grandchildRole()
    else -> "${"пра".repeat(distance - 2)}${grandchildRole().lowercase()}".replaceFirstChar(Char::uppercase)
}

private fun Gender.spouseRole(): String = when (this) {
    Gender.MALE -> "Муж"
    Gender.FEMALE -> "Жена"
    Gender.UNKNOWN -> "Супруг"
}

private fun Gender.grandparentRole(): String = when (this) {
    Gender.MALE -> "Дедушка"
    Gender.FEMALE -> "Бабушка"
    Gender.UNKNOWN -> "Прародитель"
}

private fun Gender.grandchildRole(): String = when (this) {
    Gender.MALE -> "Внук"
    Gender.FEMALE -> "Внучка"
    Gender.UNKNOWN -> "Внук или внучка"
}

private fun Gender.siblingRole(): String = when (this) {
    Gender.MALE -> "Брат"
    Gender.FEMALE -> "Сестра"
    Gender.UNKNOWN -> "Брат или сестра"
}

private fun Gender.uncleRole(): String = when (this) {
    Gender.MALE -> "Дядя"
    Gender.FEMALE -> "Тётя"
    Gender.UNKNOWN -> "Дядя или тётя"
}

private fun Gender.niblingRole(): String = when (this) {
    Gender.MALE -> "Племянник"
    Gender.FEMALE -> "Племянница"
    Gender.UNKNOWN -> "Племянник или племянница"
}

private fun Gender.cousinRole(): String = when (this) {
    Gender.MALE -> "Двоюродный брат"
    Gender.FEMALE -> "Двоюродная сестра"
    Gender.UNKNOWN -> "Двоюродный родственник"
}
