package me.terevo.domain.invariant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk

class ChronologyPlausibleTest {

    @Test
    fun `parent born after child produces a warning`() {
        val parent = person(born = 1950)
        val child = person(born = 1930)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        val warnings = Invariants.warningsFor(tree, Change.AddRelation(relation))

        assertEquals(listOf(ValidationWarning.ParentBornAfterChild(parent.id, child.id)), warnings)
    }

    @Test
    fun `too small age gap produces a warning`() {
        val parent = person(born = 1950)
        val child = person(born = 1960)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        val warnings = Invariants.warningsFor(tree, Change.AddRelation(relation))

        assertEquals(listOf(ValidationWarning.ParentTooYoung(parent.id, child.id, 10)), warnings)
    }

    @Test
    fun `plausible age gap produces no warning`() {
        val parent = person(born = 1930)
        val child = person(born = 1960)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        assertTrue(Invariants.warningsFor(tree, Change.AddRelation(relation)).isEmpty())
    }

    @Test
    fun `child born long after parent death produces a warning`() {
        val parent = person(born = 1900, died = 1940)
        val child = person(born = 1945)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        val warnings = Invariants.warningsFor(tree, Change.AddRelation(relation))

        assertEquals(listOf(ValidationWarning.ChildBornAfterParentDeath(parent.id, child.id, 5)), warnings)
    }

    @Test
    fun `child born within the grace period after parent death produces no warning`() {
        val parent = person(born = 1900, died = 1940)
        val child = person(born = 1941)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        assertTrue(Invariants.warningsFor(tree, Change.AddRelation(relation)).isEmpty())
    }

    @Test
    fun `missing dates produce no warnings`() {
        val parent = person()
        val child = person(born = 1960)
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        assertTrue(Invariants.warningsFor(tree, Change.AddRelation(relation)).isEmpty())
    }

    @Test
    fun `implausible chronology does not block the relation`() {
        val parent = person(born = 1950)
        val child = person(born = 1930)
        val tree = treeOf(parent, child)

        val extended = tree.addRelation(
            ParentChild.of(parent = parent.id, child = child.id).shouldBeOk(),
        ).shouldBeOk()

        assertEquals(listOf(parent.id), extended.parentsOf(child.id))
    }

    private fun treeOf(vararg people: Person): FamilyTree =
        people.fold(FamilyTree.EMPTY) { tree, person -> tree.addPerson(person).shouldBeOk() }
}
