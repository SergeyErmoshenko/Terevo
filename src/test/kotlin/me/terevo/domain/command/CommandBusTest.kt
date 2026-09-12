package me.terevo.domain.command

import me.terevo.domain.DomainError
import me.terevo.domain.invariant.ValidationWarning
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.testing.name
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class CommandBusTest {

    @Test
    fun `executing a command updates the tree and enables undo`() {
        val bus = CommandBus()
        val created = person()

        bus.execute(AddPerson(created)).shouldBeOk()

        assertEquals(1, bus.tree.value.size)
        assertTrue(bus.canUndo)
        assertFalse(bus.canRedo)
    }

    @Test
    fun `undo restores the previous tree and enables redo`() {
        val bus = CommandBus()
        val created = person()
        bus.execute(AddPerson(created)).shouldBeOk()

        bus.undo().shouldBeOk()

        assertEquals(FamilyTree.EMPTY, bus.tree.value)
        assertFalse(bus.canUndo)
        assertTrue(bus.canRedo)
    }

    @Test
    fun `redo reapplies the undone command`() {
        val bus = CommandBus()
        val created = person()
        bus.execute(AddPerson(created)).shouldBeOk()
        bus.undo().shouldBeOk()

        bus.redo().shouldBeOk()

        assertEquals(created, bus.tree.value.person(created.id))
        assertTrue(bus.canUndo)
        assertFalse(bus.canRedo)
    }

    @Test
    fun `undo on an empty history is rejected`() {
        val error = CommandBus().undo().shouldBeErr()

        assertEquals(DomainError.History.NothingToUndo, error)
    }

    @Test
    fun `redo on an empty history is rejected`() {
        val error = CommandBus().redo().shouldBeErr()

        assertEquals(DomainError.History.NothingToRedo, error)
    }

    @Test
    fun `new command clears the redo history`() {
        val bus = CommandBus()
        bus.execute(AddPerson(person())).shouldBeOk()
        bus.undo().shouldBeOk()

        bus.execute(AddPerson(person())).shouldBeOk()

        assertFalse(bus.canRedo)
    }

    @Test
    fun `failed command leaves the tree and history untouched`() {
        val bus = CommandBus()
        val created = person()
        bus.execute(AddPerson(created)).shouldBeOk()
        val before = bus.tree.value

        bus.execute(AddPerson(created)).shouldBeErr()

        assertEquals(before, bus.tree.value)
        assertEquals(1, bus.undoDepth)
    }

    @Test
    fun `removing a person restores their relations on undo`() {
        val bus = CommandBus()
        val parent = person()
        val child = person()
        bus.execute(AddPerson(parent)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
        bus.execute(AddRelation(link)).shouldBeOk()
        val before = bus.tree.value

        bus.execute(RemovePerson(parent.id)).shouldBeOk()
        assertNull(bus.tree.value.relation(link.id))

        bus.undo().shouldBeOk()

        assertEquals(before, bus.tree.value)
        assertEquals(listOf(parent.id), bus.tree.value.parentsOf(child.id))
    }

    @Test
    fun `batch is undone as a single step`() {
        val bus = CommandBus()
        val parent = person()
        val child = person()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        bus.execute(Batch(listOf(AddPerson(parent), AddPerson(child), AddRelation(link)))).shouldBeOk()
        assertEquals(1, bus.undoDepth)

        bus.undo().shouldBeOk()

        assertEquals(FamilyTree.EMPTY, bus.tree.value)
    }

    @Test
    fun `failing batch applies nothing`() {
        val bus = CommandBus()
        val created = person()

        bus.execute(Batch(listOf(AddPerson(created), AddPerson(created)))).shouldBeErr()

        assertEquals(FamilyTree.EMPTY, bus.tree.value)
        assertFalse(bus.canUndo)
    }

    @Test
    fun `update is inverted to the previous version of the person`() {
        val bus = CommandBus()
        val created = person(surname = "Иванов")
        bus.execute(AddPerson(created)).shouldBeOk()
        val renamed = created.with(name = name(surname = "Петров")).shouldBeOk()

        bus.execute(UpdatePerson(renamed)).shouldBeOk()
        assertEquals("Петров", bus.tree.value.person(created.id)?.name?.surname)

        bus.undo().shouldBeOk()

        assertEquals("Иванов", bus.tree.value.person(created.id)?.name?.surname)
    }

    @Test
    fun `warnings are reported without blocking the command`() {
        val bus = CommandBus()
        val parent = person(born = 1950)
        val child = person(born = 1930)
        bus.execute(AddPerson(parent)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()

        val warnings = bus.execute(AddRelation(link)).shouldBeOk()

        assertEquals(listOf(ValidationWarning.ParentBornAfterChild(parent.id, child.id)), warnings)
        assertEquals(listOf(parent.id), bus.tree.value.parentsOf(child.id))
    }

    @Test
    fun `undo history is bounded by the limit`() {
        val bus = CommandBus(undoLimit = 3)

        repeat(10) { bus.execute(AddPerson(person())).shouldBeOk() }

        assertEquals(3, bus.undoDepth)
    }
}
