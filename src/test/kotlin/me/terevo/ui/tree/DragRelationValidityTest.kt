package me.terevo.ui.tree

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import me.terevo.ui.person.RelationMode
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DragRelationValidityTest {

    @Test
    fun `dropping a node on itself is never a valid relation`() {
        val person = person()
        val tree = treeOf(person)

        assertFalse(canCreateAnyRelation(tree, person.id, person.id))
        RelationMode.entries.forEach { mode ->
            assertFalse(canCreateRelation(tree, mode, person.id, person.id))
        }
    }

    @Test
    fun `two unrelated people can be linked any way`() {
        val first = person()
        val second = person()
        val tree = treeOf(first, second)

        assertTrue(canCreateAnyRelation(tree, first.id, second.id))
        RelationMode.entries.forEach { mode ->
            assertTrue(canCreateRelation(tree, mode, first.id, second.id))
        }
    }

    @Test
    fun `an existing parent-child link blocks both reversal and marriage`() {
        val parent = person()
        val child = person()
        val tree = treeOf(parent, child)
            .addRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()).shouldBeOk()

        assertFalse(canCreateRelation(tree, RelationMode.PARENT, parent.id, child.id))
        assertFalse(canCreateRelation(tree, RelationMode.CHILD, parent.id, child.id))
        assertFalse(canCreateRelation(tree, RelationMode.SPOUSE, parent.id, child.id))
        assertFalse(canCreateAnyRelation(tree, parent.id, child.id))
    }

    private fun treeOf(vararg people: Person): FamilyTree =
        people.fold(FamilyTree.EMPTY) { tree, person -> tree.addPerson(person).shouldBeOk() }
}
