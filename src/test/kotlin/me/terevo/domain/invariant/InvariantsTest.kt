package me.terevo.domain.invariant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.terevo.domain.DomainError
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.Person
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk

class InvariantsTest {

    @Test
    fun `person cannot become their own ancestor`() {
        val grandfather = person()
        val father = person()
        val child = person()
        val tree = treeOf(grandfather, father, child)
            .addRelation(ParentChild.of(parent = grandfather.id, child = father.id).shouldBeOk()).shouldBeOk()
            .addRelation(ParentChild.of(parent = father.id, child = child.id).shouldBeOk()).shouldBeOk()

        val error = tree.addRelation(
            ParentChild.of(parent = child.id, child = grandfather.id).shouldBeOk(),
        ).shouldBeErr()

        assertTrue(error is DomainError.Link.CycleDetected)
        assertEquals(listOf(child.id, father.id, grandfather.id, child.id), error.path)
    }

    @Test
    fun `direct parent child inversion is a cycle`() {
        val parent = person()
        val child = person()
        val tree = treeOf(parent, child)
            .addRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()).shouldBeOk()

        val error = tree.addRelation(ParentChild.of(parent = child.id, child = parent.id).shouldBeOk()).shouldBeErr()

        assertTrue(error is DomainError.Link.CycleDetected)
    }

    @Test
    fun `third biological parent is rejected`() {
        val first = person()
        val second = person()
        val third = person()
        val child = person()
        val tree = treeOf(first, second, third, child)
            .addRelation(ParentChild.of(parent = first.id, child = child.id).shouldBeOk()).shouldBeOk()
            .addRelation(ParentChild.of(parent = second.id, child = child.id).shouldBeOk()).shouldBeOk()

        val error = tree.addRelation(ParentChild.of(parent = third.id, child = child.id).shouldBeOk()).shouldBeErr()

        assertTrue(error is DomainError.Link.TooManyBiologicalParents)
    }

    @Test
    fun `third parent is allowed when the link is not biological`() {
        val first = person()
        val second = person()
        val third = person()
        val child = person()
        val tree = treeOf(first, second, third, child)
            .addRelation(ParentChild.of(parent = first.id, child = child.id).shouldBeOk()).shouldBeOk()
            .addRelation(ParentChild.of(parent = second.id, child = child.id).shouldBeOk()).shouldBeOk()

        val extended = tree.addRelation(
            ParentChild.of(parent = third.id, child = child.id, kind = ParentKind.ADOPTIVE).shouldBeOk(),
        ).shouldBeOk()

        assertEquals(3, extended.parentsOf(child.id).size)
        assertEquals(2, extended.biologicalParentsOf(child.id).size)
    }

    @Test
    fun `unrelated branches are not treated as cycles`() {
        val left = person()
        val right = person()
        val child = person()
        val tree = treeOf(left, right, child)
            .addRelation(ParentChild.of(parent = left.id, child = child.id).shouldBeOk()).shouldBeOk()

        val extended = tree.addRelation(ParentChild.of(parent = right.id, child = child.id).shouldBeOk()).shouldBeOk()

        assertEquals(2, extended.parentsOf(child.id).size)
    }

    @Test
    fun `valid change passes every invariant`() {
        val parent = person()
        val child = person()
        val tree = treeOf(parent, child)
        val relation = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        assertNull(Invariants.check(tree, Change.AddRelation(relation)))
    }

    private fun treeOf(vararg people: Person): FamilyTree =
        people.fold(FamilyTree.EMPTY) { tree, person -> tree.addPerson(person).shouldBeOk() }
}
