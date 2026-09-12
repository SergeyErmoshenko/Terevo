package me.terevo.ui.person

import me.terevo.domain.command.CommandBus
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.ParentKind
import me.terevo.testing.InMemoryTreeRepository
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RelationEditorTest {

    @Test
    fun `search finds existing people by Russian name`() {
        val ivan = person(surname = "Иванов", givenName = "Иван")
        val petr = person(surname = "Петров", givenName = "Пётр")
        val editor = editor(FamilyTree.of(listOf(ivan, petr), emptyList()).shouldBeOk())

        assertEquals(listOf(ivan), editor.search("иван"))
    }

    @Test
    fun `parent kind can be changed atomically`() {
        val parent = person()
        val child = person()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
        val tree = FamilyTree.of(listOf(parent, child), listOf(link)).shouldBeOk()
        val repository = InMemoryTreeRepository(tree)
        val bus = CommandBus(tree, repository)
        val editor = RelationEditor(bus)

        assertIs<RelationResult.Success>(editor.changeParentKind(link.id, ParentKind.ADOPTIVE))
        assertEquals(ParentKind.ADOPTIVE, (bus.tree.value.relation(link.id) as ParentChild).kind)
        assertEquals(bus.tree.value, repository.load().shouldBeOk())
    }

    @Test
    fun `duplicate relation returns clear Russian message`() {
        val parent = person()
        val child = person()
        val link = ParentChild.of(parent = parent.id, child = child.id).shouldBeOk()
        val editor = editor(FamilyTree.of(listOf(parent, child), listOf(link)).shouldBeOk())

        val error = assertIs<RelationResult.Error>(editor.addParent(parent.id, child.id, ParentKind.BIOLOGICAL))

        assertEquals("Такая связь уже существует", error.message)
    }

    @Test
    fun `third biological parent returns clear Russian message`() {
        val first = person()
        val second = person()
        val third = person()
        val child = person()
        val tree = FamilyTree.of(
            listOf(first, second, third, child),
            listOf(
                ParentChild.of(parent = first.id, child = child.id).shouldBeOk(),
                ParentChild.of(parent = second.id, child = child.id).shouldBeOk(),
            ),
        ).shouldBeOk()

        val error = assertIs<RelationResult.Error>(editor(tree).addParent(third.id, child.id, ParentKind.BIOLOGICAL))

        assertEquals("У человека уже есть два биологических родителя", error.message)
    }

    @Test
    fun `cycle returns path in Russian message`() {
        val grandparent = person(surname = "Иванов", givenName = "Иван")
        val parent = person(surname = "Петров", givenName = "Пётр")
        val child = person(surname = "Сидоров", givenName = "Семён")
        val tree = FamilyTree.of(
            listOf(grandparent, parent, child),
            listOf(
                ParentChild.of(parent = grandparent.id, child = parent.id).shouldBeOk(),
                ParentChild.of(parent = parent.id, child = child.id).shouldBeOk(),
            ),
        ).shouldBeOk()

        val error = assertIs<RelationResult.Error>(editor(tree).addParent(child.id, grandparent.id, ParentKind.BIOLOGICAL))

        assertTrue(error.message.startsWith("Эта связь создаст цикл"))
        assertTrue("→" in error.message)
    }

    private fun editor(tree: FamilyTree): RelationEditor = RelationEditor(CommandBus(tree, InMemoryTreeRepository(tree)))
}
