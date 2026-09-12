package me.terevo.domain.command

import me.terevo.domain.DomainError
import me.terevo.domain.invariant.Change
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.testing.InMemoryTreeRepository
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandBusPersistenceTest {

    @Test
    fun `successful command is written to the repository`() {
        val repository = InMemoryTreeRepository()
        val bus = CommandBus(repository = repository)
        val created = person()

        bus.execute(AddPerson(created)).shouldBeOk()

        assertEquals(bus.tree.value, repository.stored)
        assertEquals(1, repository.applied.size)
    }

    @Test
    fun `storage failure leaves the tree and history untouched`() {
        val repository = InMemoryTreeRepository()
        val bus = CommandBus(repository = repository)
        repository.failure = DomainError.Storage.Failure("диск недоступен")

        val error = bus.execute(AddPerson(person())).shouldBeErr()

        assertEquals(DomainError.Storage.Failure("диск недоступен"), error)
        assertEquals(FamilyTree.EMPTY, bus.tree.value)
        assertFalse(bus.canUndo)
    }

    @Test
    fun `storage failure during undo keeps the command in the history`() {
        val repository = InMemoryTreeRepository()
        val bus = CommandBus(repository = repository)
        val created = person()
        bus.execute(AddPerson(created)).shouldBeOk()
        val before = bus.tree.value
        repository.failure = DomainError.Storage.Failure("диск недоступен")

        bus.undo().shouldBeErr()

        assertEquals(before, bus.tree.value)
        assertTrue(bus.canUndo)
        assertFalse(bus.canRedo)
    }

    @Test
    fun `removing a person reports the removal of their relations`() {
        val repository = InMemoryTreeRepository()
        val bus = CommandBus(repository = repository)
        val parent = person()
        val child = person()
        bus.execute(AddPerson(parent)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        bus.execute(
            AddRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()),
        ).shouldBeOk()
        repository.applied.clear()

        bus.execute(RemovePerson(parent.id)).shouldBeOk()

        assertEquals(2, repository.applied.size)
        assertTrue(repository.applied.first() is Change.RemoveRelation)
        assertEquals(bus.tree.value, repository.stored)
    }
}
