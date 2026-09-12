package me.terevo.ui.tree

import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.CommandBus
import me.terevo.domain.model.FamilyTree
import me.terevo.testing.InMemoryTreeRepository
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.test.*

class HistoryControllerTest {

    @Test
    fun `menu state exposes Russian action and availability`() {
        val repository = InMemoryTreeRepository(FamilyTree.EMPTY)
        val bus = CommandBus(repository = repository)
        val controller = HistoryController(bus)
        val person = person()

        bus.execute(AddPerson(person)).shouldBeOk()
        val state = controller.state()

        assertTrue(state.canUndo)
        assertFalse(state.canRedo)
        assertEquals("Отменить: удаление человека", state.undoLabel)
    }

    @Test
    fun `twenty actions undo and redo preserve database and tree after every step`() {
        val repository = InMemoryTreeRepository(FamilyTree.EMPTY)
        val bus = CommandBus(repository = repository)
        val controller = HistoryController(bus)
        val people = (0 until 20).map { person(surname = "Иванов$it", givenName = "Иван$it") }

        people.forEach { person ->
            bus.execute(AddPerson(person)).shouldBeOk()
            assertEquals(bus.tree.value, repository.load().shouldBeOk())
        }
        repeat(20) {
            val state = controller.undo()
            assertEquals(bus.tree.value, repository.load().shouldBeOk())
            assertNotNull(state.centerOn)
        }
        assertEquals(FamilyTree.EMPTY, bus.tree.value)
        repeat(20) {
            val state = controller.redo()
            assertEquals(bus.tree.value, repository.load().shouldBeOk())
            assertNotNull(state.centerOn)
        }
        assertEquals(20, bus.tree.value.size)
    }
}
