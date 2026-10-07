package me.terevo.app

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.port.ProjectLocation
import me.terevo.layout.LayoutMode
import me.terevo.persistence.SqliteProjectService
import me.terevo.ui.Strings
import me.terevo.ui.events.EventParticipantInput
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

        assertEquals(null, opened.mediaViewer?.imagePath)
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
    fun `deleting a person by id works without them being selected`() {
        // The tree's context menu opens on right-click without selecting the card, so deletion has
        // to work from an explicit id rather than from the current selection.
        controller.create(locationOf("delete-by-id"))
        val kept = createPerson("Иванов", "Иван")
        val removed = createPerson("Петров", "Пётр")
        controller.selectPerson(kept)

        val state = controller.deletePerson(removed)

        assertEquals(1, state.personCount)
        // Deleting someone else must not disturb the current selection.
        assertEquals(kept, state.selectedPerson?.id)
    }

    @Test
    fun `deleting the selected person by id clears the selection`() {
        controller.create(locationOf("delete-selected-by-id"))
        val person = createPerson("Иванов", "Иван")
        controller.selectPerson(person)

        val state = controller.deletePerson(person)

        assertNull(state.selectedPerson)
        assertEquals(0, state.personCount)
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
        val childNode =
            state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванов Иван" }.id
        val parentNode =
            state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванова Мария" }.id
        val spouseNode =
            state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Иванова Анна" }.id
        val unrelatedNode =
            state.canvas.visuals.persons.values.single { it.nameLines.joinToString(" ") == "Петров Пётр" }.id

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
    fun `selection navigation asks the view to center and supports back and forward`() {
        controller.create(locationOf("navigation"))
        val first = createPerson("Иванов", "Иван")
        val second = createPerson("Петров", "Пётр")

        controller.selectPerson(first)
        val before = controller.state.canvas.centerRequest
        val secondState = controller.selectPerson(second)

        assertEquals(me.terevo.layout.NodeId(second.value.toString()), secondState.canvas.centerOn)
        assertTrue(secondState.canvas.centerRequest > before)
        assertEquals(first, controller.navigateBack().selectedPerson?.id)
        assertEquals(second, controller.navigateForward().selectedPerson?.id)
    }

    @Test
    fun `adding a child never asks the view to center on anything but the selected parent`() {
        // Reported as "the tree did not move right after adding a person": re-fitting the view on
        // every edit zoomed out by exactly the factor the tree had grown, so the change looked
        // frozen. There is no auto-refit left to regress, but centerOn must still track only
        // deliberate selection (addChild below reselects the parent before adding, which is
        // itself a legitimate center request) and never drift to the tree's new bounds or the
        // freshly created child.
        controller.create(locationOf("camera-stays"))
        val parent = createPerson("Иванов", "Иван")
        controller.selectPerson(parent)
        val parentNode = me.terevo.layout.NodeId(parent.value.toString())

        addChild(parent, "Иванов", "Пётр")
        assertEquals(parentNode, controller.state.canvas.centerOn)
        val widthAfterFirst = controller.state.canvas.layout.bounds.width
        addChild(parent, "Иванов", "Сергей")

        assertEquals(parentNode, controller.state.canvas.centerOn)
        assertTrue(
            controller.state.canvas.layout.bounds.width > widthAfterFirst,
            "the second child must actually widen the tree, otherwise this proves nothing",
        )
        assertEquals(2, controller.state.selectedChildren.size)
    }

    @Test
    fun `home is the first person added`() {
        controller.create(locationOf("explicit-fit"))
        val parent = createPerson("Иванов", "Иван")
        controller.selectPerson(parent)
        repeat(3) { index -> addChild(parent, "Иванов", "Ребёнок$index") }

        assertEquals(me.terevo.layout.NodeId(parent.value.toString()), controller.state.canvas.homePersonId)
    }

    @Test
    fun `home goes to the person picked from search until the filters are cleared`() {
        controller.create(locationOf("search-home"))
        val first = createPerson("Иванов", "Иван")
        val found = createPerson("Петров", "Пётр")
        fun node(id: me.terevo.domain.model.PersonId) = me.terevo.layout.NodeId(id.value.toString())

        controller.changeSearchFilter(me.terevo.ui.person.PersonSearchFilter(query = "Петров"))
        controller.selectSearchResult(found)
        assertEquals(node(found), controller.state.canvas.homePersonId)

        controller.changeSearchFilter(me.terevo.ui.person.PersonSearchFilter())
        assertEquals(node(first), controller.state.canvas.homePersonId)
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

    @Test
    fun `new spouse via full form is forced to opposite gender and gets linked`() {
        controller.create(locationOf("gender-check"))
        val ivan = createPerson("Иванов", "Иван")
        controller.selectPerson(ivan)
        val editForm = assertNotNull(controller.startEditingPerson().personForm)
        controller.updatePersonForm(editForm.copy(gender = me.terevo.domain.model.Gender.MALE))
        controller.savePerson()

        controller.selectPerson(ivan)
        controller.startAddingRelation(RelationMode.SPOUSE)
        val form = assertNotNull(controller.startCreatingRelative().personForm)
        assertEquals(me.terevo.domain.model.Gender.FEMALE, form.requiredGender)
        assertEquals(me.terevo.domain.model.Gender.FEMALE, form.gender)

        controller.updatePersonForm(form.copy(surname = "Иванова", givenName = "Мария"))
        controller.savePerson()

        val tree = assertNotNull(controller.commandBus?.tree?.value)
        assertEquals(1, tree.spousesOf(ivan).size)
    }

    @Test
    fun `linking an existing person as child can also link a second parent`() {
        controller.create(locationOf("second-parent-existing"))
        val father = createPerson("Иванов", "Пётр")
        val mother = createPerson("Иванова", "Мария")
        val child = createPerson("Иванов", "Алексей")
        controller.selectPerson(father)

        val dialog = assertNotNull(controller.startAddingRelation(RelationMode.CHILD).relationDialog)
        controller.updateRelationDialog(dialog.copy(selected = child, secondParent = mother))
        controller.saveRelation()

        val tree = assertNotNull(controller.commandBus?.tree?.value)
        assertEquals(setOf(father, mother), tree.parentsOf(child).toSet())
    }

    @Test
    fun `adding a child to someone with one spouse defaults the second parent to that spouse`() {
        // Reported: a child added to a married woman was recorded with her as the only parent,
        // because the second parent defaulted to "none", and was drawn off a separate line instead
        // of the couple's shared bracket.
        controller.create(locationOf("second-parent-default"))
        val father = createPerson("Иванов", "Пётр")
        val mother = createPerson("Иванова", "Мария")
        controller.selectPerson(father)
        val spouseDialog = assertNotNull(controller.startAddingRelation(RelationMode.SPOUSE).relationDialog)
        controller.updateRelationDialog(spouseDialog.copy(selected = mother))
        controller.saveRelation()
        controller.selectPerson(mother)

        val dialog = assertNotNull(controller.startAddingRelation(RelationMode.CHILD).relationDialog)

        assertEquals(father, dialog.secondParent)
    }

    @Test
    fun `creating a new child prefills the surname and can link a second parent`() {
        controller.create(locationOf("second-parent-new"))
        val father = createPerson("Иванов", "Пётр")
        val mother = createPerson("Иванова", "Мария")
        controller.selectPerson(father)

        val dialog = assertNotNull(controller.startAddingRelation(RelationMode.CHILD).relationDialog)
        controller.updateRelationDialog(dialog.copy(secondParent = mother))
        val form = assertNotNull(controller.startCreatingRelative().personForm)
        assertEquals("Иванов", form.surname)

        controller.updatePersonForm(form.copy(givenName = "Алексей"))
        val state = controller.savePerson()

        val child = assertNotNull(state.selectedChildren.singleOrNull())
        val tree = assertNotNull(controller.commandBus?.tree?.value)
        assertEquals(setOf(father, mother), tree.parentsOf(child.id).toSet())
    }

    @Test
    fun `adding an event with participants creates it and closes the form`() {
        controller.create(locationOf("add-event"))
        val groom = createPerson("Иванов", "Иван")
        val bride = createPerson("Иванова", "Мария")
        val form = assertNotNull(controller.startAddingEvent().eventForm)

        controller.updateEventForm(
            form.copy(
                type = "Свадьба",
                date = EventDateInput(EventDateMode.EXACT, "01.06.2020"),
                place = "Казань",
                participants = listOf(
                    EventParticipantInput(personId = groom, role = "Жених"),
                    EventParticipantInput(personId = bride, role = "Невеста"),
                ),
            ),
        )
        val saved = controller.saveEvent()

        assertEquals(null, saved.eventForm)
        val row = assertNotNull(saved.eventRows.singleOrNull { it.type == "Свадьба" })
        assertNotNull(row.id)
        assertTrue(row.participants.contains("Жених: Иванов Иван"))
        assertTrue(row.participants.contains("Невеста: Иванова Мария"))
    }

    @Test
    fun `editing an existing event prefills its data`() {
        controller.create(locationOf("edit-event"))
        val honoree = createPerson("Петров", "Пётр")
        val form = assertNotNull(controller.startAddingEvent().eventForm)
        controller.updateEventForm(
            form.copy(
                type = "Юбилей",
                date = EventDateInput(EventDateMode.EXACT, "25.12.1990"),
                participants = listOf(EventParticipantInput(personId = honoree, role = "Именинник")),
            ),
        )
        val saved = controller.saveEvent()
        val eventId = assertNotNull(saved.eventRows.singleOrNull { it.type == "Юбилей" }?.id)

        val editForm = assertNotNull(controller.startEditingEvent(eventId).eventForm)

        assertEquals("Юбилей", editForm.type)
        assertEquals("Именинник", editForm.participants.single().role)
        assertEquals(honoree, editForm.participants.single().personId)
    }

    @Test
    fun `deleting an event removes it from the event rows`() {
        controller.create(locationOf("delete-event"))
        val honoree = createPerson("Сидоров", "Сидор")
        val form = assertNotNull(controller.startAddingEvent().eventForm)
        controller.updateEventForm(
            form.copy(
                type = "Выпускной",
                date = EventDateInput(EventDateMode.EXACT, "01.07.2015"),
                participants = listOf(EventParticipantInput(personId = honoree, role = "Выпускник")),
            ),
        )
        val eventId = assertNotNull(controller.saveEvent().eventRows.singleOrNull { it.type == "Выпускной" }?.id)

        val state = controller.deleteEvent(eventId)

        assertTrue(state.eventRows.none { it.id == eventId })
    }

    @Test
    fun `cancelling an event form does not persist it`() {
        controller.create(locationOf("cancel-event"))
        val honoree = createPerson("Кузнецов", "Кузьма")
        val form = assertNotNull(controller.startAddingEvent().eventForm)
        controller.updateEventForm(
            form.copy(
                type = "Юбилей",
                date = EventDateInput(EventDateMode.EXACT, "01.01.2000"),
                participants = listOf(EventParticipantInput(personId = honoree, role = "Именинник")),
            ),
        )

        val state = controller.cancelEvent()

        assertEquals(null, state.eventForm)
        assertTrue(state.eventRows.none { it.type == "Юбилей" })
    }

    private fun createPerson(surname: String, givenName: String): me.terevo.domain.model.PersonId {
        val form = assertNotNull(controller.startAddingPerson().personForm)
        controller.updatePersonForm(form.copy(surname = surname, givenName = givenName))
        controller.savePerson()
        return form.id
    }

    // Mirrors the context-menu flow the tree canvas uses: open the child relation dialog on the
    // selected person, create a brand-new relative from it, then save.
    private fun addChild(
        parent: me.terevo.domain.model.PersonId,
        surname: String,
        givenName: String,
    ): me.terevo.domain.model.PersonId {
        controller.selectPerson(parent)
        assertNotNull(controller.startAddingRelation(RelationMode.CHILD).relationDialog)
        val form = assertNotNull(controller.startCreatingRelative().personForm)
        controller.updatePersonForm(form.copy(surname = surname, givenName = givenName))
        controller.savePerson()
        return form.id
    }

    private fun locationOf(name: String): ProjectLocation =
        ProjectLocation(workspace.resolve("$name.${ProjectLocation.EXTENSION}").toString())
}
