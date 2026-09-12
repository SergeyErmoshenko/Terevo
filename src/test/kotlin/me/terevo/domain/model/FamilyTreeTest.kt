package me.terevo.domain.model

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class FamilyTreeTest {

    @Test
    fun `adding the same person twice is rejected`() {
        val person = person()
        val tree = FamilyTree.EMPTY.addPerson(person).shouldBeOk()

        val error = tree.addPerson(person).shouldBeErr()

        assertTrue(error is DomainError.Tree.PersonAlreadyExists)
    }

    @Test
    fun `updating an unknown person is rejected`() {
        val error = FamilyTree.EMPTY.updatePerson(person()).shouldBeErr()

        assertTrue(error is DomainError.Missing.Person)
    }

    @Test
    fun `relation to an unknown person is rejected`() {
        val known = person()
        val tree = FamilyTree.EMPTY.addPerson(known).shouldBeOk()
        val link = ParentChild.of(parent = known.id, child = PersonId.next()).shouldBeOk()

        val error = tree.addRelation(link).shouldBeErr()

        assertTrue(error is DomainError.Missing.Person)
    }

    @Test
    fun `duplicate link is rejected regardless of kind`() {
        val parent = person()
        val child = person()
        val tree = treeOf(parent, child)
            .addRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk())
            .shouldBeOk()

        val error = tree.addRelation(
            ParentChild.of(parent = parent.id, child = child.id, kind = ParentKind.ADOPTIVE).shouldBeOk(),
        ).shouldBeErr()

        assertTrue(error is DomainError.Link.Duplicate)
    }

    @Test
    fun `reversed marriage is recognized as duplicate`() {
        val husband = person()
        val wife = person()
        val tree = treeOf(husband, wife)
            .addRelation(Marriage.of(first = husband.id, second = wife.id).shouldBeOk())
            .shouldBeOk()

        val error = tree.addRelation(Marriage.of(first = wife.id, second = husband.id).shouldBeOk()).shouldBeErr()

        assertTrue(error is DomainError.Link.Duplicate)
    }

    @Test
    fun `navigation follows parent child and marriage links`() {
        val father = person(surname = "Отец")
        val mother = person(surname = "Мать")
        val child = person(surname = "Ребёнок")
        val tree = treeOf(father, mother, child)
            .addRelation(ParentChild.of(parent = father.id, child = child.id).shouldBeOk()).shouldBeOk()
            .addRelation(ParentChild.of(parent = mother.id, child = child.id).shouldBeOk()).shouldBeOk()
            .addRelation(Marriage.of(first = father.id, second = mother.id).shouldBeOk()).shouldBeOk()

        assertEquals(listOf(father.id, mother.id).sortedBy { it.value }, tree.parentsOf(child.id))
        assertEquals(listOf(child.id), tree.childrenOf(father.id))
        assertEquals(listOf(mother.id), tree.spousesOf(father.id))
        assertEquals(listOf(father.id), tree.spousesOf(mother.id))
    }

    @Test
    fun `biological parents exclude adoptive links`() {
        val biological = person()
        val adoptive = person()
        val child = person()
        val tree = treeOf(biological, adoptive, child)
            .addRelation(ParentChild.of(parent = biological.id, child = child.id).shouldBeOk()).shouldBeOk()
            .addRelation(
                ParentChild.of(parent = adoptive.id, child = child.id, kind = ParentKind.ADOPTIVE).shouldBeOk(),
            ).shouldBeOk()

        assertEquals(2, tree.parentsOf(child.id).size)
        assertEquals(listOf(biological.id), tree.biologicalParentsOf(child.id))
    }

    @Test
    fun `removing a person removes their relations`() {
        val parent = person()
        val child = person()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
        val tree = treeOf(parent, child).addRelation(link).shouldBeOk()

        val without = tree.removePerson(parent.id).shouldBeOk()

        assertFalse(without.contains(parent.id))
        assertNull(without.relation(link.id))
        assertTrue(without.parentsOf(child.id).isEmpty())
        assertTrue(without.relationsOf(child.id).isEmpty())
    }

    @Test
    fun `removing an unknown person is rejected`() {
        val error = FamilyTree.EMPTY.removePerson(PersonId.next()).shouldBeErr()

        assertTrue(error is DomainError.Missing.Person)
    }

    @Test
    fun `removing an unknown relation is rejected`() {
        val error = FamilyTree.EMPTY.removeRelation(RelationId.next()).shouldBeErr()

        assertTrue(error is DomainError.Missing.Relation)
    }

    @Test
    fun `ancestors respect the depth limit`() {
        val grandfather = person()
        val father = person()
        val child = person()
        val tree = chain(grandfather, father, child)

        assertEquals(setOf(father.id), tree.ancestors(child.id, depth = 1))
        assertEquals(setOf(father.id, grandfather.id), tree.ancestors(child.id))
        assertTrue(tree.ancestors(child.id, depth = 0).isEmpty())
    }

    @Test
    fun `descendants respect the depth limit`() {
        val grandfather = person()
        val father = person()
        val child = person()
        val tree = chain(grandfather, father, child)

        assertEquals(setOf(father.id), tree.descendants(grandfather.id, depth = 1))
        assertEquals(setOf(father.id, child.id), tree.descendants(grandfather.id))
    }

    @Test
    fun `ancestry path lists the chain from descendant to ancestor`() {
        val grandfather = person()
        val father = person()
        val child = person()
        val tree = chain(grandfather, father, child)

        assertEquals(listOf(child.id, father.id, grandfather.id), tree.ancestryPath(child.id, grandfather.id))
        assertNull(tree.ancestryPath(grandfather.id, child.id))
    }

    @Test
    fun `tree built from collections matches incremental construction`() {
        val parent = person()
        val child = person()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        val built = FamilyTree.of(listOf(parent, child), listOf(link)).shouldBeOk()

        assertEquals(2, built.size)
        assertEquals(listOf(parent.id), built.parentsOf(child.id))
    }

    @Test
    fun `adding an event with unknown participant is rejected`() {
        val known = person()
        val tree = treeOf(known)
        val event = Event.of(
            type = "Свадьба",
            participants = listOf(EventParticipant(known.id, "Гость"), EventParticipant(PersonId.next(), "Гость")),
        ).shouldBeOk()

        val error = tree.addEvent(event).shouldBeErr()

        assertTrue(error is DomainError.Missing.Person)
    }

    @Test
    fun `adding the same event twice is rejected`() {
        val person = person()
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(person.id, "Именинник")))
            .shouldBeOk()
        val tree = treeOf(person).addEvent(event).shouldBeOk()

        val error = tree.addEvent(event).shouldBeErr()

        assertTrue(error is DomainError.Tree.EventAlreadyExists)
    }

    @Test
    fun `removing an unknown event is rejected`() {
        val error = FamilyTree.EMPTY.removeEvent(EventId.next()).shouldBeErr()

        assertTrue(error is DomainError.Missing.Event)
    }

    @Test
    fun `removing a person cascades to their events without touching others`() {
        val alone = person()
        val shared1 = person()
        val shared2 = person()
        val soleEvent = Event.of(type = "Юбилей", participants = listOf(EventParticipant(alone.id, "Именинник")))
            .shouldBeOk()
        val sharedEvent = Event.of(
            type = "Свадьба",
            participants = listOf(EventParticipant(shared1.id, "Жених"), EventParticipant(shared2.id, "Невеста")),
        ).shouldBeOk()
        val tree = treeOf(alone, shared1, shared2)
            .addEvent(soleEvent).shouldBeOk()
            .addEvent(sharedEvent).shouldBeOk()

        val without = tree.removePerson(alone.id).shouldBeOk()

        assertNull(without.event(soleEvent.id))
        assertEquals(sharedEvent, without.event(sharedEvent.id))
    }

    private fun treeOf(vararg people: Person): FamilyTree =
        people.fold(FamilyTree.EMPTY) { tree, person ->
            when (val added = tree.addPerson(person)) {
                is Outcome.Ok -> added.value
                is Outcome.Err -> error("unexpected ${added.error}")
            }
        }

    private fun chain(grandparent: Person, parent: Person, child: Person): FamilyTree =
        treeOf(grandparent, parent, child)
            .addRelation(ParentChild.of(parent = grandparent.id, child = parent.id).shouldBeOk()).shouldBeOk()
            .addRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()).shouldBeOk()
}
