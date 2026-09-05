package me.terevo.app

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.port.ProjectLocation
import me.terevo.layout.LayoutMode
import me.terevo.persistence.SqliteProjectService
import me.terevo.ui.Strings
import me.terevo.ui.person.*
import me.terevo.ui.tree.NodeAccent
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

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
        assertEquals("Иванов Иван", saved.canvas.visuals.persons.values.single().nameLines.joinToString(" "))
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
    fun `custom field persists and its key is suggested for next person`() {
        controller.create(locationOf("custom-fields"))
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(
            form.copy(
                surname = "Иванов",
                customFields = listOf(CustomFieldInput("Профессия", "врач")),
            ),
        )

        val saved = controller.savePerson()
        val next = assertNotNull(controller.startAddingPerson().personForm)

        assertEquals("врач", saved.selectedPerson?.customFields?.get("Профессия"))
        assertEquals(listOf("Профессия"), next.customFieldSuggestions)
    }

    @Test
    fun `media import attaches file to selected person and removal deletes unused content`() {
        val projectDirectory = workspace.resolve("media-project")
        val location = ProjectLocation(projectDirectory.resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())
        controller.create(location)
        val person = createPerson("Иванов", "Иван")
        controller.selectPerson(person)
        val source = workspace.resolve("photo.txt")
        Files.writeString(source, "photo")

        val attached = controller.importMedia(source.toString())
        val media = assertNotNull(attached.selectedMedia.singleOrNull())
        val content = projectDirectory.resolve(ProjectLocation.MEDIA_DIRECTORY).resolve(media.sha256.take(2))
            .resolve(media.sha256)

        assertTrue(Files.exists(content))
        assertEquals(media.id, attached.selectedPerson?.mediaIds?.singleOrNull())

        val opened = controller.openMedia(media.id)
        val zoomed = controller.changeMediaZoom(10f)

        assertTrue(opened.mediaViewer?.content?.isEmpty() == true)
        assertEquals(4f, zoomed.mediaViewer?.zoom)
        controller.closeMedia()
        val removed = controller.removeMedia(media.id)

        assertTrue(removed.selectedMedia.isEmpty())
        assertTrue(Files.notExists(content))
    }

    @Test
    fun `pending person media can be added and removed before saving`() {
        controller.create(locationOf("pending-media-form"))
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(form.copy(surname = "Иванов"))

        val withOne = controller.addPendingPersonMedia("/tmp/a.txt")
        assertEquals(listOf("/tmp/a.txt"), withOne.personForm?.pendingMediaPaths)

        val withThree = controller.addPendingPersonMedia(listOf("/tmp/b.txt", "/tmp/c.txt"))
        assertEquals(listOf("/tmp/a.txt", "/tmp/b.txt", "/tmp/c.txt"), withThree.personForm?.pendingMediaPaths)

        val withoutB = controller.removePendingPersonMedia("/tmp/b.txt")
        assertEquals(listOf("/tmp/a.txt", "/tmp/c.txt"), withoutB.personForm?.pendingMediaPaths)
    }

    @Test
    fun `person created with pending media attaches files after save`() {
        val projectDirectory = workspace.resolve("pending-media-project")
        val location = ProjectLocation(projectDirectory.resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())
        controller.create(location)
        val first = workspace.resolve("first.txt")
        val second = workspace.resolve("second.txt")
        Files.writeString(first, "one")
        Files.writeString(second, "two")
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(
            form.copy(
                surname = "Иванов",
                givenName = "Иван",
                pendingMediaPaths = listOf(first.toString(), second.toString()),
            ),
        )

        val saved = controller.savePerson()

        assertEquals(2, saved.selectedMedia.size)
        assertEquals(2, saved.selectedPerson?.mediaIds?.size)
    }

    @Test
    fun `deleting person removes media that is no longer referenced`() {
        val projectDirectory = workspace.resolve("delete-media-project")
        val location = ProjectLocation(projectDirectory.resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())
        controller.create(location)
        val person = createPerson("Иванов", "Иван")
        controller.selectPerson(person)
        val source = workspace.resolve("document.txt")
        Files.writeString(source, "document")
        val attached = controller.importMedia(source.toString())
        val media = assertNotNull(attached.selectedMedia.singleOrNull())
        val content = projectDirectory.resolve(ProjectLocation.MEDIA_DIRECTORY).resolve(media.sha256.take(2))
            .resolve(media.sha256)

        controller.deleteSelectedPerson()

        assertTrue(Files.notExists(content))
    }

    @Test
    fun `search results are exposed and highlighted on canvas`() {
        controller.create(locationOf("search"))
        val expected = createPerson("Иванов", "Иван")
        createPerson("Петров", "Пётр")

        val state = controller.changeSearchFilter(PersonSearchFilter(query = "Иванов"))
        val resultNode = me.terevo.layout.NodeId(expected.value.toString())

        assertEquals(listOf(expected), state.searchResults.map { it.id })
        assertEquals(NodeAccent.RELATED, state.canvas.highlight.accentOf(resultNode))
    }

    @Test
    fun `GEDCOM preview imports as one undoable batch and exports file`() {
        controller.create(locationOf("gedcom"))
        val source = workspace.resolve("family.ged")
        Files.writeString(
            source,
            """
                0 HEAD
                1 CHAR UTF-8
                0 @I1@ INDI
                1 NAME Иван /Иванов/
                1 SEX M
                0 TRLR
            """.trimIndent(),
        )

        val preview = controller.previewGedcom(source.toString())
        val imported = controller.confirmGedcomImport()
        val target = workspace.resolve("export.ged")
        val exported = controller.exportGedcom(target.toString())

        assertEquals(1, preview.gedcomPreview?.people)
        assertEquals(1, imported.personCount)
        assertTrue(imported.canUndo)
        assertTrue(Files.readString(target).contains("2 VERS 5.5.1"))
        assertEquals("GEDCOM экспортирован", exported.status)
        assertEquals(0, controller.undo().personCount)
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
        val childNode = state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванов Иван" }.id
        val parentNode = state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванова Мария" }.id
        val spouseNode = state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванова Анна" }.id
        val unrelatedNode = state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Петров Пётр" }.id

        assertEquals(NodeAccent.FOCUSED, highlight.accentOf(childNode))
        assertEquals(Strings.MAIN_PERSON, highlight.roleOf(childNode))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(parentNode))
        assertEquals(NodeAccent.RELATED, highlight.accentOf(spouseNode))
        assertEquals("Родитель", highlight.roleOf(parentNode))
        assertEquals("Супруг", highlight.roleOf(spouseNode))
        assertEquals(NodeAccent.MUTED, highlight.accentOf(unrelatedNode))
    }

    @Test
    fun `tree mode and depth change the actual layout around selected root`() {
        controller.create(locationOf("tree-mode"))
        val grandparent = createPerson("Иванов", "Пётр")
        val parent = createPerson("Иванов", "Иван")
        val child = createPerson("Иванов", "Алексей")
        controller.selectPerson(parent)
        var dialog = assertNotNull(controller.startAddingRelation(RelationMode.PARENT).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = grandparent))
        controller.saveRelation()
        dialog = assertNotNull(controller.startAddingRelation(RelationMode.CHILD).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = child))
        controller.saveRelation()

        controller.changeLayoutDepth(1)
        val ancestors = controller.changeLayoutMode(LayoutMode.ANCESTORS)
        val descendants = controller.changeLayoutMode(LayoutMode.DESCENDANTS)

        assertEquals(
            setOf(parent, grandparent),
            ancestors.canvas.layout.nodes.keys.map { me.terevo.domain.model.PersonId.parse(it.value) }.toSet()
        )
        assertEquals(
            setOf(parent, child),
            descendants.canvas.layout.nodes.keys.map { me.terevo.domain.model.PersonId.parse(it.value) }.toSet()
        )
    }

    @Test
    fun `selection navigation centers camera and supports back and forward`() {
        controller.create(locationOf("navigation"))
        val first = createPerson("Иванов", "Иван")
        val second = createPerson("Петров", "Пётр")
        controller.updateCanvas(me.terevo.ui.tree.TreeCanvasIntent.Resize(me.terevo.layout.Size(800.0, 600.0)))

        controller.selectPerson(first)
        val secondState = controller.selectPerson(second)
        val secondNode = me.terevo.layout.NodeId(second.value.toString())
        val secondRect = assertNotNull(secondState.canvas.layout.rectOf(secondNode))

        assertEquals(
            secondState.canvas.camera.center(secondRect, secondState.canvas.viewport),
            secondState.canvas.camera
        )
        assertEquals(first, controller.navigateBack().selectedPerson?.id)
        assertEquals(second, controller.navigateForward().selectedPerson?.id)
    }

    @Test
    fun `undo and redo expose menu state and remap tree`() {
        controller.create(locationOf("history"))
        val person = createPerson("Иванов", "Иван")

        assertTrue(controller.state.canUndo)
        assertEquals("Отменить: удаление человека", controller.state.undoLabel)

        val undone = controller.undo()

        assertEquals(0, undone.personCount)
        assertTrue(undone.canRedo)
        assertEquals("Повторить: восстановление человека", undone.redoLabel)

        val redone = controller.redo()

        assertEquals(1, redone.personCount)
        assertEquals(person, redone.selectedPerson?.id)
    }

    @Test
    fun `dirty person form requires discard confirmation`() {
        controller.create(locationOf("discard"))
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(form.copy(surname = "Иванов"))

        val requested = controller.cancelPerson()

        assertTrue(requested.personForm?.isDiscardConfirmationVisible == true)
        assertNotNull(controller.keepEditingPerson().personForm)
        assertEquals(null, controller.confirmDiscardPerson().personForm)
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
