package me.terevo.domain.model

import me.terevo.domain.Outcome
import me.terevo.testing.person
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeIndexConsistencyTest {

    @Test
    fun `navigation matches a full scan after a thousand random mutations`() {
        val random = Random(SEED)
        var tree = FamilyTree.EMPTY
        val people = mutableListOf<PersonId>()

        repeat(MUTATIONS) {
            tree = when (random.nextInt(OPERATIONS)) {
                0 -> addPerson(tree, people)
                1 -> addParentLink(tree, people, random)
                2 -> addMarriage(tree, people, random)
                3 -> dropRelation(tree, random)
                else -> dropPerson(tree, people, random)
            }
            assertConsistent(tree)
        }

        assertTrue(tree.relations.isNotEmpty())
        assertTrue(tree.size > 0)
    }

    private fun addPerson(tree: FamilyTree, people: MutableList<PersonId>): FamilyTree {
        val created = person()
        return when (val added = tree.addPerson(created)) {
            is Outcome.Ok -> {
                people.add(created.id)
                added.value
            }

            is Outcome.Err -> tree
        }
    }

    private fun addParentLink(tree: FamilyTree, people: List<PersonId>, random: Random): FamilyTree {
        val pair = pickPair(people, random) ?: return tree
        val link = ParentChild.of(parent = pair.first, child = pair.second)
        return applyRelation(tree, link)
    }

    private fun addMarriage(tree: FamilyTree, people: List<PersonId>, random: Random): FamilyTree {
        val pair = pickPair(people, random) ?: return tree
        val link = Marriage.of(first = pair.first, second = pair.second)
        return applyRelation(tree, link)
    }

    private fun dropRelation(tree: FamilyTree, random: Random): FamilyTree {
        val ids = tree.relations.keys.sortedBy { it.value }
        if (ids.isEmpty()) return tree
        return unwrapOrKeep(tree, tree.removeRelation(ids[random.nextInt(ids.size)]))
    }

    private fun dropPerson(tree: FamilyTree, people: MutableList<PersonId>, random: Random): FamilyTree {
        if (people.isEmpty()) return tree
        val victim = people.removeAt(random.nextInt(people.size))
        return unwrapOrKeep(tree, tree.removePerson(victim))
    }

    private fun applyRelation(tree: FamilyTree, relation: Outcome<Relation>): FamilyTree = when (relation) {
        is Outcome.Ok -> unwrapOrKeep(tree, tree.addRelation(relation.value))
        is Outcome.Err -> tree
    }

    private fun unwrapOrKeep(tree: FamilyTree, result: Outcome<FamilyTree>): FamilyTree = when (result) {
        is Outcome.Ok -> result.value
        is Outcome.Err -> tree
    }

    private fun pickPair(people: List<PersonId>, random: Random): Pair<PersonId, PersonId>? {
        if (people.size < 2) return null
        val first = people[random.nextInt(people.size)]
        val second = people[random.nextInt(people.size)]
        return if (first == second) null else first to second
    }

    private fun assertConsistent(tree: FamilyTree) {
        val links = tree.relations.values.toList()
        for (id in tree.persons.keys) {
            assertEquals(
                links.filterIsInstance<ParentChild>().filter { it.child == id }.map { it.parent }.sortedBy { it.value },
                tree.parentsOf(id),
            )
            assertEquals(
                links.filterIsInstance<ParentChild>().filter { it.parent == id }.map { it.child }.sortedBy { it.value },
                tree.childrenOf(id),
            )
            assertEquals(
                links.filterIsInstance<Marriage>().mapNotNull { it.spouseOf(id) }.sortedBy { it.value },
                tree.spousesOf(id),
            )
        }
        for (relation in links) {
            assertTrue(relation.participants.all { tree.contains(it) })
        }
    }

    private companion object {
        const val SEED = 20260829
        const val MUTATIONS = 1000
        const val OPERATIONS = 5
    }
}
