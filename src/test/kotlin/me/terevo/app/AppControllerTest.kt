package me.terevo.app

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.SqliteProjectService
import me.terevo.ui.Strings
import me.terevo.ui.person.EventDateInput
import me.terevo.ui.person.EventDateMode
import me.terevo.ui.person.RelationMode
import me.terevo.ui.tree.NodeAccent

class AppControllerTest {
    private lateinit var workspace: Path
    private lateinit var controller: AppController

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-app")
        controller = AppController(SqliteProjectService(timestamp = { "2026-08-30T12:00:00Z" }))
    }

    @AfterTest
    fun cleanup() {
        controller.close()
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    @Test
    fun `create project opens empty tree and updates visible state`() {
        val location = locationOf("family")

        val state = controller.create(location)

        assertTrue(state.isProjectOpen)
        assertEquals("family", state.projectName)
        assertEquals(0, state.personCount)
        assertNotNull(controller.commandBus)
        assertTrue(Files.exists(Path.of(location.path)))
    }

    @Test
    fun `open existing project updates visible state`() {
        val location = locationOf("family")
        controller.create(location)
        controller.close()
        controller = AppController(SqliteProjectService(timestamp = { "2026-08-30T12:00:00Z" }))

        val state = controller.open(location)

        assertTrue(state.isProjectOpen)
        assertEquals("family", state.projectName)
    }

    @Test
    fun `failed open keeps empty state and shows Russian error`() {
        val state = controller.open(locationOf("missing"))

        assertFalse(state.isProjectOpen)
        assertEquals("Файл проекта не найден", state.status)
    }

    @Test
    fun `switching project releases previous file lock`() {
        val first = locationOf("first")
        val second = locationOf("second")
        controller.create(first)
        controller.create(second)

        val reopened = SqliteProjectService().open(first)

        assertTrue(reopened is me.terevo.domain.Outcome.Ok)
        reopened.value.close()
    }

    @Test
    fun `deceased person created in form persists death data after reopening project`() {
        val location = locationOf("family")
        controller.create(location)
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(
            form.copy(
                surname = "Иванов",
                givenName = "Иван",
                isAlive = false,
                death = EventDateInput(EventDateMode.EXACT, listOf("02", "01", "2020").joinToString(".")),
                deathPlace = "Москва",
            ),
        )

        val saved = controller.savePerson()

        assertEquals(1, saved.personCount)
        assertEquals(1, saved.canvas.layout.nodes.size)
        assertEquals("Иванов Иван", saved.canvas.visuals.persons.values.single().name)
        assertEquals(null, saved.personForm)
        controller.close()
        controller = AppController(SqliteProjectService(timestamp = { "2026-08-30T12:00:00Z" }))
        controller.open(location)
        val person = assertNotNull(controller.commandBus?.tree?.value?.persons?.values?.single())
        assertEquals("Иванов Иван", person.name.display)
        assertTrue(person.lifeSpan.death is EventDate.Exact)
        assertEquals("Москва", person.deathPlace?.title)
    }

    @Test
    fun `editing person with valid birth date saves and closes form`() {
        controller.create(locationOf("edit-person"))
        val id = createPerson("Иванов", "Иван")
        controller.selectPerson(id)
        val form = assertNotNull(controller.startEditingPerson().personForm)
        controller.updatePersonForm(
            form.copy(
                birth = EventDateInput(EventDateMode.EXACT, listOf("20", "10", "2000").joinToString(".")),
                birthPlace = "Казань",
            ),
        )

        val saved = controller.savePerson()

        assertEquals(null, saved.personForm)
        assertEquals("Казань", saved.selectedPerson?.birthPlace?.title)
        assertEquals(EventDate.Exact(kotlinx.datetime.LocalDate(2000, 10, 20)), saved.selectedPerson?.lifeSpan?.birth)
    }

    @Test
    fun `selected person can be linked to existing parent and spouse`() {
        controller.create(locationOf("relations"))
        val child = createPerson("Иванов", "Иван")
        val parent = createPerson("Иванов", "Пётр")
        val spouse = createPerson("Иванова", "Анна")

        controller.selectPerson(child)
        val parentDialog = assertNotNull(controller.startAddingRelation(RelationMode.PARENT).relationDialog)
        controller.updateRelationDialog(parentDialog.copy(selected = parent))
        val withParent = controller.saveRelation()

        assertEquals(listOf(parent), withParent.selectedParents.map { it.id })
        assertTrue(controller.commandBus?.tree?.value?.relations?.values?.any { it is ParentChild } == true)

        val spouseDialog = assertNotNull(controller.startAddingRelation(RelationMode.SPOUSE).relationDialog)
        controller.updateRelationDialog(spouseDialog.copy(selected = spouse))
        val withSpouse = controller.saveRelation()

        assertEquals(listOf(spouse), withSpouse.selectedSpouses.map { it.id })
        assertTrue(controller.commandBus?.tree?.value?.relations?.values?.any { it is Marriage } == true)
    }

    @Test
    fun `selection highlights main person and every resolved relative`() {
        controller.create(locationOf("highlight"))
        val child = createPerson("Иванов", "Иван")
        val parent = createPerson("Иванова", "Мария")
        val spouse = createPerson("Иванова", "Анна")
        val unrelated = createPerson("Петров", "Пётр")
        controller.selectPerson(child)
        var dialog = assertNotNull(controller.startAddingRelation(RelationMode.PARENT).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = parent))
        controller.saveRelation()
        dialog = assertNotNull(controller.startAddingRelation(RelationMode.SPOUSE).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = spouse))
        controller.saveRelation()

        val state = controller.selectPerson(child)
        val highlight = state.canvas.highlight
        val childNode = state.canvas.visuals.persons.values.single { it.name == "Иванов Иван" }.id
        val parentNode = state.canvas.visuals.persons.values.single { it.name == "Иванова Мария" }.id
        val spouseNode = state.canvas.visuals.persons.values.single { it.name == "Иванова Анна" }.id
        val unrelatedNode = state.canvas.visuals.persons.values.single { it.name == "Петров Пётр" }.id

        assertEquals(NodeAccent.FOCUSED, highlight.accentOf(childNode))
        assertEquals(Strings.MAIN_PERSON, highlight.roleOf(childNode))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(parentNode))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(spouseNode))
        assertEquals("Родитель", highlight.roleOf(parentNode))
        assertEquals("Супруг", highlight.roleOf(spouseNode))
        assertEquals(NodeAccent.MUTED, highlight.accentOf(unrelatedNode))
    }

    @Test
    fun `duplicate relation remains open and shows domain error`() {
        controller.create(locationOf("duplicate"))
        val child = createPerson("Иванов", "Иван")
        val parent = createPerson("Иванов", "Пётр")
        controller.selectPerson(child)
        var dialog = assertNotNull(controller.startAddingRelation(RelationMode.PARENT).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = parent))
        controller.saveRelation()

        dialog = assertNotNull(controller.startAddingRelation(RelationMode.PARENT).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = parent))
        val state = controller.saveRelation()

        assertEquals("Такая связь уже существует", state.relationDialog?.error)
    }

    @Test
    fun `new parent uses full form and person plus relation undo as one action`() {
        controller.create(locationOf("new-relative"))
        val child = createPerson("Иванов", "Иван")
        controller.selectPerson(child)
        controller.startAddingRelation(RelationMode.PARENT)
        val form = assertNotNull(controller.startCreatingRelative().personForm)
        controller.updatePersonForm(
            form.copy(
                surname = "Иванова",
                givenName = "Мария",
                birthPlace = "Казань",
                notes = "Полные данные",
            ),
        )

        val state = controller.savePerson()

        val parent = assertNotNull(state.selectedParents.singleOrNull())
        assertEquals("Казань", parent.birthPlace?.title)
        assertEquals("Полные данные", parent.notes)
        assertEquals(2, state.personCount)
        assertEquals(2, controller.commandBus?.undoDepth)

        controller.commandBus?.undo()
        val undone = controller.refreshTree()

        assertEquals(1, undone.personCount)
        assertTrue(undone.selectedParents.isEmpty())
    }

    private fun createPerson(surname: String, givenName: String): me.terevo.domain.model.PersonId {
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(form.copy(surname = surname, givenName = givenName))
        controller.savePerson()
        return form.id
    }

    private fun locationOf(name: String): ProjectLocation =
        ProjectLocation(workspace.resolve("$name.${ProjectLocation.EXTENSION}").toString())
}
