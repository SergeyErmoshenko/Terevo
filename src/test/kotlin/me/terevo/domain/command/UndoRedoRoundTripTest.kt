package me.terevo.domain.command

import me.terevo.domain.Outcome
import me.terevo.domain.model.*
import me.terevo.testing.name
import me.terevo.testing.person
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UndoRedoRoundTripTest {

    @Test
    fun `add update and remove event can be undone and redone`() {
        val bus = CommandBus(undoLimit = 10)
        val person = person()
        bus.execute(AddPerson(person))
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(person.id, "Именинник")))
            .let { (it as Outcome.Ok).value }

        bus.execute(AddEvent(event))
        assertEquals(event, bus.tree.value.event(event.id))

        val renamed = event.with(type = "Выпускной").let { (it as Outcome.Ok).value }
        bus.execute(UpdateEvent(renamed))
        assertEquals("Выпускной", bus.tree.value.event(event.id)?.type)

        bus.execute(RemoveEvent(event.id))
        assertEquals(null, bus.tree.value.event(event.id))

        bus.undo()
        assertEquals(renamed, bus.tree.value.event(event.id))
        bus.undo()
        assertEquals(event, bus.tree.value.event(event.id))
        bus.undo()
        assertEquals(null, bus.tree.value.event(event.id))

        bus.redo()
        bus.redo()
        bus.redo()
        assertEquals(null, bus.tree.value.event(event.id))
    }

    @Test
    fun `undoing removal of a person restores their events`() {
        val bus = CommandBus(undoLimit = 10)
        val person = person()
        bus.execute(AddPerson(person))
        val event = Event.of(type = "Юбилей", participants = listOf(EventParticipant(person.id, "Именинник")))
            .let { (it as Outcome.Ok).value }
        bus.execute(AddEvent(event))

        bus.execute(RemovePerson(person.id))
        assertEquals(null, bus.tree.value.person(person.id))
        assertEquals(null, bus.tree.value.event(event.id))

        bus.undo()
        assertEquals(person, bus.tree.value.person(person.id))
        assertEquals(event, bus.tree.value.event(event.id))
    }

    @Test
    fun `undoing every command returns the initial tree and redoing returns the final one`() {
        val random = Random(SEED)
        val bus = CommandBus(undoLimit = COMMANDS)
        val people = mutableListOf<PersonId>()
        val applied = mutableListOf<FamilyTree>()

        repeat(COMMANDS) {
            val command = randomCommand(bus.tree.value, people, random)
            if (bus.execute(command) is Outcome.Ok) {
                applied.add(bus.tree.value)
            }
        }

        assertTrue(applied.size > COMMANDS / 2)
        val finalTree = bus.tree.value

        repeat(applied.size) { bus.undo() }
        assertEquals(FamilyTree.EMPTY, bus.tree.value)

        repeat(applied.size) { bus.redo() }
        assertEquals(finalTree, bus.tree.value)
    }

    @Test
    fun `tree state matches the snapshot after each undo step`() {
        val random = Random(SEED)
        val bus = CommandBus(undoLimit = COMMANDS)
        val people = mutableListOf<PersonId>()
        val snapshots = mutableListOf(FamilyTree.EMPTY)

        repeat(COMMANDS) {
            val command = randomCommand(bus.tree.value, people, random)
            if (bus.execute(command) is Outcome.Ok) {
                snapshots.add(bus.tree.value)
            }
        }

        for (index in snapshots.lastIndex downTo 1) {
            assertEquals(snapshots[index], bus.tree.value)
            bus.undo()
        }
        assertEquals(FamilyTree.EMPTY, bus.tree.value)
    }

    private fun randomCommand(tree: FamilyTree, people: MutableList<PersonId>, random: Random): Command =
        when (random.nextInt(OPERATIONS)) {
            0, 1 -> {
                val created = person()
                people.add(created.id)
                AddPerson(created)
            }

            2 -> parentLink(people, random)
            3 -> marriageLink(people, random)
            4 -> renameSomeone(tree, people, random)
            5 -> dropRelation(tree, random)
            else -> dropPerson(people, random)
        }

    private fun parentLink(people: List<PersonId>, random: Random): Command {
        val pair = pickPair(people, random) ?: return Batch(emptyList())
        return when (val relation = ParentChild.of(parent = pair.first, child = pair.second)) {
            is Outcome.Ok -> AddRelation(relation.value)
            is Outcome.Err -> Batch(emptyList())
        }
    }

    private fun marriageLink(people: List<PersonId>, random: Random): Command {
        val pair = pickPair(people, random) ?: return Batch(emptyList())
        return when (val relation = Marriage.of(first = pair.first, second = pair.second)) {
            is Outcome.Ok -> AddRelation(relation.value)
            is Outcome.Err -> Batch(emptyList())
        }
    }

    private fun renameSomeone(tree: FamilyTree, people: List<PersonId>, random: Random): Command {
        if (people.isEmpty()) return Batch(emptyList())
        val target = tree.person(people[random.nextInt(people.size)]) ?: return Batch(emptyList())
        return when (val renamed = target.with(name = name(surname = "Фамилия${random.nextInt(HUNDRED)}"))) {
            is Outcome.Ok -> UpdatePerson(renamed.value)
            is Outcome.Err -> Batch(emptyList())
        }
    }

    private fun dropRelation(tree: FamilyTree, random: Random): Command {
        val ids = tree.relations.keys.sortedBy { it.value }
        if (ids.isEmpty()) return Batch(emptyList())
        return RemoveRelation(ids[random.nextInt(ids.size)])
    }

    private fun dropPerson(people: MutableList<PersonId>, random: Random): Command {
        if (people.isEmpty()) return Batch(emptyList())
        return RemovePerson(people.removeAt(random.nextInt(people.size)))
    }

    private fun pickPair(people: List<PersonId>, random: Random): Pair<PersonId, PersonId>? {
        if (people.size < 2) return null
        val first = people[random.nextInt(people.size)]
        val second = people[random.nextInt(people.size)]
        return if (first == second) null else first to second
    }

    private companion object {
        const val SEED = 4242
        const val COMMANDS = 300
        const val OPERATIONS = 7
        const val HUNDRED = 100
    }
}
