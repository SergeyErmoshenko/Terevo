package me.terevo.app

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.command.*
import me.terevo.domain.model.*
import me.terevo.domain.port.*
import me.terevo.export.PdfFiles
import me.terevo.gedcom.GedcomFiles
import me.terevo.kinship.KinshipCalculator
import me.terevo.kinship.KinshipResult
import me.terevo.layout.LayoutDirection
import me.terevo.layout.LayoutMode
import me.terevo.layout.LayoutOptions
import me.terevo.layout.Point
import me.terevo.persistence.JsonSettingsStore
import me.terevo.statistics.TreeStatistics
import me.terevo.ui.*
import me.terevo.ui.events.EventFormState
import me.terevo.ui.events.toEvent
import me.terevo.ui.events.validateEventForm
import me.terevo.ui.export.exportTreePng
import me.terevo.ui.kinship.KinshipDialogState
import me.terevo.ui.person.*
import me.terevo.ui.theme.LightColors
import me.terevo.ui.tree.*
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import me.terevo.ui.person.toRussianMessage as warningToRussianMessage

private val logger = KotlinLogging.logger {}

class AppController(
    private val projects: ProjectService,
    private val settings: SettingsStore = JsonSettingsStore(ProjectDirectories().root),
) : AutoCloseable {
    private var project: OpenProject? = null

    var state: AppState = AppState(themeMode = loadThemeMode())
        private set

    var commandBus: CommandBus? = null
        private set

    fun create(location: ProjectLocation): AppState = activate(projects.create(location))

    fun open(location: ProjectLocation): AppState = activate(projects.open(location))

    fun createFromGedcom(location: ProjectLocation, path: String): AppState {
        val result = projects.create(location)
        state = activate(result)
        return if (result is Outcome.Ok) previewGedcom(path) else state
    }

    fun startAddingPerson(): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(
            personForm = validatePersonForm(PersonFormState(customFieldSuggestions = customFieldSuggestions(tree))),
        )
        return state
    }

    fun startEditingPerson(): AppState {
        val person = state.selectedPerson ?: return state
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(
            personForm = validatePersonForm(PersonFormState.fromPerson(person, customFieldSuggestions(tree))),
            personViewOpen = false,
        )
        return state
    }

    fun viewPerson(id: PersonId): AppState {
        state = selectPerson(id)
        state = state.copy(personViewOpen = true)
        return state
    }

    fun closePersonView(): AppState {
        state = state.copy(personViewOpen = false)
        return state
    }

    fun updatePersonForm(form: PersonFormState): AppState {
        state = state.copy(personForm = validatePersonForm(form))
        return state
    }

    fun deleteSelectedPerson(): AppState {
        val person = state.selectedPerson ?: return state
        val opened = project ?: return state
        val bus = commandBus ?: return state
        state = when (PersonFormService(bus).delete(PersonFormState.fromPerson(person))) {
            is Outcome.Ok -> {
                person.mediaIds.forEach(opened.mediaRepository::deleteIfUnused)
                remapTree(bus.tree.value, selected = null).copy(status = "Человек удалён")
            }

            is Outcome.Err -> state.copy(status = "Не удалось удалить человека")
        }
        return state
    }

    fun selectPerson(id: PersonId?): AppState {
        val tree = commandBus?.tree?.value ?: return state
        val previous = state.selectedPerson?.id
        val selected = if (state.layoutMode == LayoutMode.WHOLE_FAMILY) {
            remapSelection(tree, id, center = id != null)
        } else {
            state = state.copy(selectedPerson = id?.let(tree::person))
            remapTree(tree, id)
        }
        state = selected.copy(
            selectionBackHistory = if (previous == null || previous == id) state.selectionBackHistory else state.selectionBackHistory + previous,
            selectionForwardHistory = if (previous == id) state.selectionForwardHistory else emptyList(),
        )
        return state
    }

    fun navigateBack(): AppState = navigateSelection(state.selectionBackHistory, isBack = true)

    fun navigateForward(): AppState = navigateSelection(state.selectionForwardHistory, isBack = false)

    fun changeLayoutMode(mode: LayoutMode): AppState {
        state = state.copy(layoutMode = mode)
        return refreshTree()
    }

    fun changeLayoutDepth(depth: Int): AppState {
        state = state.copy(layoutDepth = depth.coerceIn(1, Int.MAX_VALUE))
        return refreshTree()
    }

    fun changeLayoutDirection(direction: LayoutDirection): AppState {
        state = state.copy(layoutDirection = direction)
        return refreshTree()
    }

    fun changeLayoutDensity(density: LayoutDensity): AppState {
        state = state.copy(layoutDensity = density)
        return refreshTree()
    }

    fun changeSearchFilter(filter: PersonSearchFilter): AppState {
        val tree = commandBus?.tree?.value ?: return state
        val results = PersonSearch.find(tree, filter)
        state = state.copy(searchFilter = filter, searchResults = results)
        state = remapSelection(tree, state.selectedPerson?.id)
        return state
    }

    fun previewGedcom(path: String): AppState {
        state = when (val preview = GedcomFiles.preview(path)) {
            is Outcome.Ok -> {
                logger.info {
                    "GEDCOM preview ready: file=${Path.of(path).fileName}, people=${preview.value.people}, " +
                            "families=${preview.value.families}, skippedTags=${preview.value.skippedTags.sorted()}"
                }
                state.copy(
                    gedcomPreview = GedcomImportState(
                        people = preview.value.people,
                        families = preview.value.families,
                        skippedTags = preview.value.skippedTags,
                        command = preview.value.command,
                    ),
                    status = "GEDCOM готов к импорту",
                )
            }

            is Outcome.Err -> {
                logger.error { "GEDCOM preview failed: file=${Path.of(path).fileName}, error=${preview.error}" }
                state.copy(status = "Не удалось прочитать GEDCOM")
            }
        }
        return state
    }

    fun confirmGedcomImport(): AppState {
        val preview = state.gedcomPreview ?: return state
        val bus = commandBus ?: return state
        logger.info { "GEDCOM import requested: people=${preview.people}, families=${preview.families}" }
        state = when (val result = bus.execute(preview.command)) {
            is Outcome.Ok -> {
                logger.info { "GEDCOM import completed: people=${preview.people}, families=${preview.families}" }
                remapTree(bus.tree.value, null).copy(
                    gedcomPreview = null,
                    status = "GEDCOM импортирован: ${preview.people} человек",
                )
            }

            is Outcome.Err -> {
                logger.error {
                    "GEDCOM import failed: people=${preview.people}, families=${preview.families}, error=${result.error}"
                }
                state.copy(
                    gedcomPreview = preview.copy(error = result.error.toRelationMessage(bus.tree.value)),
                    status = "Не удалось импортировать GEDCOM",
                )
            }
        }
        return state
    }

    fun cancelGedcomImport(): AppState {
        state = state.copy(gedcomPreview = null)
        return state
    }

    fun exportGedcom(path: String): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = when (GedcomFiles.export(tree, path)) {
            is Outcome.Ok -> state.copy(status = "GEDCOM экспортирован")
            is Outcome.Err -> state.copy(status = "Не удалось экспортировать GEDCOM")
        }
        return state
    }

    fun undo(): AppState = applyHistory { it.undo() }

    fun redo(): AppState = applyHistory { it.redo() }

    fun importMedia(path: String): AppState {
        val opened = project ?: return state
        val person = state.selectedPerson ?: return state
        val bus = commandBus ?: return state
        val media = when (val imported = opened.mediaRepository.import(path)) {
            is Outcome.Ok -> imported.value
            is Outcome.Err -> {
                state = state.copy(status = "Не удалось добавить файл")
                return state
            }
        }
        val updated = when (val value = person.with(mediaIds = person.mediaIds + media.id)) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return state.copy(status = "Этот файл уже добавлен")
        }
        state = when (bus.execute(UpdatePerson(updated))) {
            is Outcome.Ok -> remapTree(bus.tree.value, person.id).copy(status = "Файл добавлен")
            is Outcome.Err -> {
                opened.mediaRepository.deleteIfUnused(media.id)
                state.copy(status = "Не удалось добавить файл")
            }
        }
        return state
    }

    fun addPendingPersonMedia(path: String): AppState {
        val form = state.personForm ?: return state
        state = state.copy(personForm = form.copy(pendingMediaPaths = form.pendingMediaPaths + path))
        return state
    }

    fun addPendingPersonMedia(paths: List<String>): AppState {
        val form = state.personForm ?: return state
        state = state.copy(personForm = form.copy(pendingMediaPaths = form.pendingMediaPaths + paths))
        return state
    }

    fun removePendingPersonMedia(path: String): AppState {
        val form = state.personForm ?: return state
        state = state.copy(personForm = form.copy(pendingMediaPaths = form.pendingMediaPaths.filterNot { it == path }))
        return state
    }

    fun openMedia(id: MediaId): AppState {
        val opened = project ?: return state
        val media = when (val found = opened.mediaRepository.find(id)) {
            is Outcome.Ok -> found.value
            is Outcome.Err -> return state.copy(status = "Не удалось открыть файл")
        }
        val path = when (val content = opened.mediaRepository.contentPath(id)) {
            is Outcome.Ok -> content.value
            is Outcome.Err -> return state.copy(status = "Не удалось открыть файл")
        }
        state = try {
            val content = when {
                media.mimeType.startsWith("image/") -> readBoundedImage(Path.of(path))
                media.mimeType == "application/pdf" -> readBoundedFile(Path.of(path))
                else -> byteArrayOf()
            }
            state.copy(mediaViewer = MediaViewerState(media, content))
        } catch (_: java.io.IOException) {
            state.copy(status = "Не удалось открыть файл")
        } catch (_: IllegalArgumentException) {
            state.copy(status = "Файл слишком большой или повреждён")
        }
        return state
    }

    fun changeMediaZoom(zoom: Float): AppState {
        state = state.copy(mediaViewer = state.mediaViewer?.copy(zoom = zoom.coerceIn(0.25f, 4f)))
        return state
    }

    fun changeMediaPage(delta: Int): AppState {
        state = state.copy(mediaViewer = state.mediaViewer?.let { it.copy(page = (it.page + delta).coerceAtLeast(0)) })
        return state
    }

    fun closeMedia(): AppState {
        state = state.copy(mediaViewer = null)
        return state
    }

    fun removeMedia(id: MediaId): AppState {
        val opened = project ?: return state
        val person = state.selectedPerson ?: return state
        val bus = commandBus ?: return state
        val updated = when (val value = person.with(mediaIds = person.mediaIds.filterNot { it == id })) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return state
        }
        state = when (bus.execute(UpdatePerson(updated))) {
            is Outcome.Ok -> {
                opened.mediaRepository.deleteIfUnused(id)
                remapTree(bus.tree.value, person.id).copy(status = "Файл удалён")
            }

            is Outcome.Err -> state.copy(status = "Не удалось удалить файл")
        }
        return state
    }

    fun startAddingRelation(mode: RelationMode): AppState {
        val source = state.selectedPerson ?: return state
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(
            relationDialog = RelationDialogState(
                mode = mode,
                source = source,
                people = eligibleRelationCandidates(tree, mode, source.id),
                secondParentCandidates = secondParentCandidates(tree, mode, source.id),
            ),
        )
        return state
    }

    private fun eligibleRelationCandidates(tree: FamilyTree, mode: RelationMode, source: PersonId): List<Person> =
        tree.persons.values
            .filter { it.id != source && canCreateRelation(tree, mode, source, it.id) }
            .sortedBy { it.name.sortKey }

    private fun secondParentCandidates(tree: FamilyTree, mode: RelationMode, source: PersonId): List<Person> =
        if (mode == RelationMode.CHILD) {
            tree.persons.values.filter { it.id != source }.sortedBy { it.name.sortKey }
        } else {
            emptyList()
        }

    fun updateRelationDialog(dialog: RelationDialogState): AppState {
        state = state.copy(relationDialog = dialog)
        return state
    }

    fun startCreatingRelative(): AppState {
        val relation = state.relationDialog ?: return state
        val tree = commandBus?.tree?.value ?: return state
        val requiredGender = if (relation.mode == RelationMode.SPOUSE) relation.source.gender.opposite() else null
        val surname = if (relation.mode == RelationMode.CHILD) relation.source.name.surname else ""
        state = state.copy(
            personForm = validatePersonForm(
                PersonFormState(
                    surname = surname,
                    gender = requiredGender ?: Gender.UNKNOWN,
                    requiredGender = requiredGender,
                    customFieldSuggestions = customFieldSuggestions(tree),
                ),
            ),
            relationDialog = null,
            pendingRelation = relation,
        )
        return state
    }

    fun saveRelation(): AppState {
        val dialog = state.relationDialog ?: return state
        val target = dialog.selected ?: return state
        val editor = commandBus?.let(::RelationEditor) ?: return state
        val result = when (dialog.mode) {
            RelationMode.PARENT -> editor.addParent(target, dialog.source.id, dialog.parentKind)
            RelationMode.CHILD -> editor.addChild(dialog.source.id, target, dialog.parentKind, dialog.secondParent)
            RelationMode.SPOUSE -> when (val details = dialog.marriageDetails()) {
                is Outcome.Ok -> editor.addSpouse(
                    dialog.source.id,
                    target,
                    dialog.marriageStatus,
                    details.value.first,
                    details.value.second,
                )

                is Outcome.Err -> RelationResult.Error(details.error.toRussianMessage())
            }
        }
        state = when (result) {
            is RelationResult.Success -> remapTree(commandBus?.tree?.value ?: return state, dialog.source.id).copy(
                relationDialog = null,
                status = result.warnings.firstOrNull()?.warningToRussianMessage() ?: "Связь сохранена",
            )

            is RelationResult.Error -> state.copy(relationDialog = dialog.copy(error = result.message))
        }
        return state
    }

    fun cancelRelation(): AppState {
        state = state.copy(relationDialog = null)
        return state
    }

    private fun startRelationFromDrag(source: PersonId, target: PersonId, screenPosition: Point): AppState {
        val tree = commandBus?.tree?.value ?: return state
        val validity = RelationMode.entries.associateWith { mode -> canCreateRelation(tree, mode, source, target) }
        val sourceGender = tree.person(source)?.gender ?: Gender.UNKNOWN
        state = state.copy(
            canvas = state.canvas.copy(nodeDrag = null),
            dragRelationMenu = DragRelationMenuState(source, target, screenPosition, validity, sourceGender),
        )
        return state
    }

    fun chooseDragRelationMode(mode: RelationMode): AppState {
        val menu = state.dragRelationMenu ?: return state
        val tree = commandBus?.tree?.value ?: return state
        val source = tree.person(menu.source) ?: return state
        state = state.copy(
            relationDialog = RelationDialogState(
                mode = mode,
                source = source,
                people = eligibleRelationCandidates(tree, mode, source.id),
                secondParentCandidates = secondParentCandidates(tree, mode, source.id),
                selected = menu.target,
            ),
            dragRelationMenu = null,
        )
        return state
    }

    fun cancelDragRelationMenu(): AppState {
        state = state.copy(dragRelationMenu = null)
        return state
    }

    fun startResolvingKinship(): AppState {
        val source = state.selectedPerson ?: return state
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(
            kinshipDialog = KinshipDialogState(
                source = source,
                people = tree.persons.values.filter { it.id != source.id }.sortedBy { it.name.sortKey },
            ),
        )
        return state
    }

    fun updateKinshipDialog(dialog: KinshipDialogState): AppState {
        val tree = commandBus?.tree?.value ?: return state
        val term =
            dialog.target?.let { target -> kinshipTermLabel(KinshipCalculator.resolve(tree, dialog.source.id, target)) }
        state = state.copy(kinshipDialog = dialog.copy(term = term))
        return state
    }

    fun closeKinshipDialog(): AppState {
        state = state.copy(kinshipDialog = null)
        return state
    }

    fun openStatistics(): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(statistics = TreeStatistics.compute(tree))
        return state
    }

    fun closeStatistics(): AppState {
        state = state.copy(statistics = null)
        return state
    }

    fun changeMainTab(tab: MainTab): AppState {
        state = state.copy(mainTab = tab)
        return state
    }

    fun toggleSidebar(): AppState {
        state = state.copy(sidebarCollapsed = !state.sidebarCollapsed)
        return state
    }

    fun changeThemeMode(mode: ThemeMode): AppState {
        settings.save((settings.load() as? Outcome.Ok)?.value?.copy(themeMode = mode) ?: UserSettings(themeMode = mode))
        state = state.copy(themeMode = mode)
        return state
    }

    fun exportPng(path: String): AppState {
        val canvas = state.canvas
        state = when (
            exportTreePng(canvas.layout, canvas.visuals, LightColors, canvas.layout.bounds, DEFAULT_PNG_SCALE, path)
        ) {
            is Outcome.Ok -> state.copy(status = "PNG экспортирован")
            is Outcome.Err -> state.copy(status = "Не удалось экспортировать PNG")
        }
        return state
    }

    fun exportPdf(path: String): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = when (PdfFiles.export(tree, path)) {
            is Outcome.Ok -> state.copy(status = "PDF экспортирован")
            is Outcome.Err -> state.copy(status = "Не удалось экспортировать PDF")
        }
        return state
    }

    private fun kinshipTermLabel(result: KinshipResult): String = when (result) {
        KinshipResult.SamePerson -> "Тот же человек"
        is KinshipResult.Blood -> result.term
        is KinshipResult.InLaw -> result.term
        KinshipResult.Unrelated -> "Родственная связь не найдена"
    }

    fun savePerson(): AppState {
        val form = state.personForm ?: return state
        val bus = commandBus ?: return state
        val pending = state.pendingRelation
        state = if (pending == null) {
            when (val result = PersonFormService(bus).save(form)) {
                is Outcome.Ok -> {
                    if (form.pendingMediaPaths.isNotEmpty()) attachPendingMedia(form.id, form.pendingMediaPaths, bus)
                    val warnings = result.value.map { it.warningToRussianMessage() }
                    remapTree(bus.tree.value, selected = form.id).copy(
                        personForm = null,
                        status = warnings.firstOrNull() ?: "Человек сохранён",
                    )
                }

                is Outcome.Err -> state.copy(personForm = form.copy(blockingError = "Не удалось сохранить человека"))
            }
        } else {
            saveRelative(form, pending, bus)
        }
        return state
    }

    fun updateCanvas(intent: TreeCanvasIntent): AppState {
        val canvas = reduceTreeCanvas(state.canvas, intent)
        state = when (intent) {
            is TreeCanvasIntent.SelectAt -> {
                val selected = canvas.selected?.toPersonId()
                val previous = state.selectedPerson?.id
                remapSelection(
                    commandBus?.tree?.value ?: return state,
                    selected,
                    source = state.copy(canvas = canvas)
                ).copy(
                    selectionBackHistory = if (previous == null || previous == selected) state.selectionBackHistory else state.selectionBackHistory + previous,
                    selectionForwardHistory = if (previous == selected) state.selectionForwardHistory else emptyList(),
                )
            }

            is TreeCanvasIntent.EditAt -> {
                val selected = canvas.spatialIndex.hitTest(canvas.camera.screenToWorld(intent.position))?.toPersonId()
                remapSelection(commandBus?.tree?.value ?: return state, selected).also { state = it }
                startEditingPerson()
            }

            is TreeCanvasIntent.AddPersonAt -> {
                state = state.copy(canvas = canvas)
                startAddingPerson()
            }

            is TreeCanvasIntent.AddRelativeAt -> {
                val selected = intent.nodeId.toPersonId()
                remapSelection(commandBus?.tree?.value ?: return state, selected).also { state = it }
                startAddingRelation(intent.mode)
            }

            TreeCanvasIntent.DragNodeEnd -> {
                val drag = state.canvas.nodeDrag
                val target = drag?.hoverTarget
                when {
                    drag == null -> state.copy(canvas = canvas)
                    target == null -> state.copy(canvas = canvas.copy(nodeDrag = null))
                    else -> startRelationFromDrag(
                        drag.nodeId.toPersonId(),
                        target.toPersonId(),
                        state.canvas.camera.worldToScreen(drag.currentWorld),
                    )
                }
            }

            else -> state.copy(canvas = canvas)
        }
        return state
    }

    fun refreshTree(): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = remapTree(tree, state.selectedPerson?.id)
        return state
    }

    fun cancelPerson(): AppState {
        val form = state.personForm ?: return state
        val requested = commandBus?.let { PersonFormService(it).requestCancel(form) } ?: form
        state = if (requested.isDiscardConfirmationVisible) {
            state.copy(personForm = requested)
        } else {
            state.copy(personForm = null, pendingRelation = null)
        }
        return state
    }

    fun confirmDiscardPerson(): AppState {
        state = state.copy(personForm = null, pendingRelation = null)
        return state
    }

    fun keepEditingPerson(): AppState {
        state = state.copy(personForm = state.personForm?.copy(isDiscardConfirmationVisible = false))
        return state
    }

    fun startAddingEvent(): AppState {
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(eventForm = EventFormState(people = tree.persons.values.toList()))
        return state
    }

    fun startEditingEvent(id: EventId): AppState {
        val tree = commandBus?.tree?.value ?: return state
        val event = tree.event(id) ?: return state
        state = state.copy(eventForm = EventFormState.fromEvent(event, tree.persons.values.toList()))
        return state
    }

    fun updateEventForm(form: EventFormState): AppState {
        state = state.copy(eventForm = validateEventForm(form))
        return state
    }

    fun cancelEvent(): AppState {
        state = state.copy(eventForm = null)
        return state
    }

    fun saveEvent(): AppState {
        val form = state.eventForm ?: return state
        val bus = commandBus ?: return state
        val event = when (val result = form.toEvent()) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> return state.copy(eventForm = form.copy(blockingError = "Не удалось сохранить событие"))
        }
        val command = if (form.original == null) AddEvent(event) else UpdateEvent(event)
        state = when (bus.execute(command)) {
            is Outcome.Ok -> remapTree(bus.tree.value, state.selectedPerson?.id).copy(
                eventForm = null,
                status = "Событие сохранено",
            )

            is Outcome.Err -> state.copy(eventForm = form.copy(blockingError = "Не удалось сохранить событие"))
        }
        return state
    }

    fun deleteEvent(id: EventId): AppState {
        val bus = commandBus ?: return state
        state = when (bus.execute(RemoveEvent(id))) {
            is Outcome.Ok -> remapTree(bus.tree.value, state.selectedPerson?.id).copy(status = "Событие удалено")
            is Outcome.Err -> state.copy(status = "Не удалось удалить событие")
        }
        return state
    }

    private fun saveRelative(form: PersonFormState, relation: RelationDialogState, bus: CommandBus): AppState {
        val person = when (val value = form.toPerson()) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return state.copy(personForm = form.copy(blockingError = "Не удалось сохранить человека"))
        }
        val result = when (relation.mode) {
            RelationMode.PARENT -> RelationEditor(bus).addParentWithPerson(
                person,
                relation.source.id,
                relation.parentKind
            )

            RelationMode.CHILD -> RelationEditor(bus).addChildWithPerson(
                person,
                relation.source.id,
                relation.parentKind,
                relation.secondParent,
            )

            RelationMode.SPOUSE -> when (val details = relation.marriageDetails()) {
                is Outcome.Ok -> RelationEditor(bus).addSpouseWithPerson(
                    person,
                    relation.source.id,
                    relation.marriageStatus,
                    details.value.first,
                    details.value.second,
                )

                is Outcome.Err -> return state.copy(personForm = form.copy(blockingError = details.error.toRussianMessage()))
            }
        }
        return when (result) {
            is RelationResult.Success -> {
                if (form.pendingMediaPaths.isNotEmpty()) attachPendingMedia(form.id, form.pendingMediaPaths, bus)
                remapTree(bus.tree.value, relation.source.id).copy(
                    personForm = null,
                    pendingRelation = null,
                    status = result.warnings.firstOrNull()?.warningToRussianMessage() ?: "Человек и связь сохранены",
                )
            }

            is RelationResult.Error -> state.copy(personForm = form.copy(blockingError = result.message))
        }
    }

    private fun attachPendingMedia(personId: PersonId, paths: List<String>, bus: CommandBus): AppState {
        val opened = project ?: return state
        val person = bus.tree.value.person(personId) ?: return state
        val importedIds = paths.mapNotNull { path ->
            when (val imported = opened.mediaRepository.import(path)) {
                is Outcome.Ok -> imported.value.id
                is Outcome.Err -> null
            }
        }
        if (importedIds.isEmpty()) return state
        val updated = when (val value = person.with(mediaIds = person.mediaIds + importedIds)) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return state
        }
        state = when (bus.execute(UpdatePerson(updated))) {
            is Outcome.Ok -> state
            is Outcome.Err -> {
                importedIds.forEach(opened.mediaRepository::deleteIfUnused)
                state
            }
        }
        return state
    }

    private fun mappedCanvas(tree: FamilyTree): TreeCanvasState {
        val root = state.selectedPerson?.id?.takeIf { tree.person(it) != null }?.toNodeId()
        val mode = if (root == null) LayoutMode.WHOLE_FAMILY else state.layoutMode
        val density = state.layoutDensity
        val options = LayoutOptions(
            root = root,
            mode = mode,
            depth = state.layoutDepth,
            siblingSpacing = density.siblingSpacing,
            subtreeSpacing = density.subtreeSpacing,
            generationSpacing = density.generationSpacing,
            spouseSpacing = density.spouseSpacing,
            direction = state.layoutDirection,
        )
        val mediaRepository = project?.mediaRepository ?: me.terevo.domain.port.MediaRepository.NONE
        val mapped =
            TreeCanvasMapper.map(tree, options, mediaRepository).copy(viewport = state.canvas.viewport)
        return if (mapped.viewport.width > 0.0 && mapped.viewport.height > 0.0 && mapped.layout.nodes.isNotEmpty()) {
            reduceTreeCanvas(mapped, TreeCanvasIntent.FitToScreen)
        } else {
            mapped
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun remapTree(tree: FamilyTree, selected: PersonId?): AppState {
        val results = if (state.searchFilter.isEmpty()) emptyList() else PersonSearch.find(tree, state.searchFilter)
        val mediaRepository = project?.mediaRepository ?: MediaRepository.NONE
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val source = state.copy(
            personCount = tree.size,
            canvas = mappedCanvas(tree),
            searchResults = results,
            personRows = me.terevo.ui.persons.mapPersonRows(tree, mediaRepository, today),
            eventRows = me.terevo.ui.events.mapEventRows(tree, mediaRepository, today),
        )
        return remapSelection(tree, selected, source).withHistory()
    }

    private fun remapSelection(
        tree: FamilyTree,
        selected: PersonId?,
        source: AppState = state,
        center: Boolean = false,
    ): AppState {
        val person = selected?.let(tree::person)
        val relations = selected?.let(tree::relationsOf).orEmpty()
        val parents = relations.filterIsInstance<ParentChild>().filter { it.child == selected }
            .mapNotNull { tree.person(it.parent) }
        val children = relations.filterIsInstance<ParentChild>().filter { it.parent == selected }
            .mapNotNull { tree.person(it.child) }
        val spouses = relations.filterIsInstance<Marriage>().mapNotNull { relation ->
            relation.spouseOf(selected ?: return@mapNotNull null)?.let(tree::person)
        }
        val spouseMarriages = relations.filterIsInstance<Marriage>().mapNotNull { relation ->
            val spouseId = relation.spouseOf(selected ?: return@mapNotNull null) ?: return@mapNotNull null
            tree.person(spouseId)?.let { SpouseInfo(it, relation) }
        }
        val relatedPeople = selected?.let { KinshipRoles.resolve(tree, it) }.orEmpty()
        val mediaRepository = project?.mediaRepository ?: MediaRepository.NONE
        val selectedMedia = person?.mediaIds.orEmpty().mapNotNull { id ->
            when (val media = mediaRepository.find(id)) {
                is Outcome.Ok -> media.value
                is Outcome.Err -> null
            }
        }
        val selectedPersonPhotoPath = person?.mainPhotoPath(mediaRepository)
        val selectedMediaThumbnails = selectedMedia.associate { it.id to mediaRepository.imagePathOf(it) }
        val highlight = TreeHighlight(
            selected = person?.id?.toNodeId(),
            roles = relatedPeople.associate { it.person.id.toNodeId() to it.role },
            searchResults = source.searchResults.mapTo(mutableSetOf()) { it.id.toNodeId() },
        )
        val highlightedCanvas = source.canvas.copy(highlight = highlight)
        val canvas = if (center && person != null) {
            reduceTreeCanvas(highlightedCanvas, TreeCanvasIntent.CenterSelected)
        } else {
            highlightedCanvas
        }
        return source.copy(
            canvas = canvas,
            selectedPerson = person,
            selectedParents = parents,
            selectedChildren = children,
            selectedSpouses = spouses,
            selectedSpouseMarriages = spouseMarriages,
            selectedMedia = selectedMedia,
            selectedPersonPhotoPath = selectedPersonPhotoPath,
            selectedMediaThumbnails = selectedMediaThumbnails,
            relatedPeople = relatedPeople,
        )
    }

    private fun navigateSelection(history: List<PersonId>, isBack: Boolean): AppState {
        val target = history.lastOrNull() ?: return state
        val current = state.selectedPerson?.id
        val tree = commandBus?.tree?.value ?: return state
        state = remapSelection(tree, target, center = true).copy(
            selectionBackHistory = if (isBack) history.dropLast(1) else state.selectionBackHistory + listOfNotNull(
                current
            ),
            selectionForwardHistory = if (isBack) state.selectionForwardHistory + listOfNotNull(current) else history.dropLast(
                1
            ),
        )
        return state
    }

    private fun applyHistory(action: (HistoryController) -> me.terevo.ui.tree.HistoryState): AppState {
        val bus = commandBus ?: return state
        val history = action(HistoryController(bus))
        val selected = history.centerOn?.toPersonId() ?: state.selectedPerson?.id
        state = remapTree(bus.tree.value, selected)
        if (history.centerOn != null) {
            state = state.copy(canvas = reduceTreeCanvas(state.canvas, TreeCanvasIntent.CenterSelected))
        }
        return state
    }

    private fun AppState.withHistory(): AppState {
        val bus = commandBus ?: return copy(canUndo = false, canRedo = false)
        val history = HistoryController(bus).state()
        return copy(
            canUndo = history.canUndo,
            canRedo = history.canRedo,
            undoLabel = history.undoLabel,
            redoLabel = history.redoLabel,
        )
    }

    private fun customFieldSuggestions(tree: FamilyTree): List<String> = tree.persons.values
        .flatMap { it.customFields.keys }
        .distinct()
        .sorted()

    override fun close() {
        project?.close()
        project = null
        commandBus = null
        state = AppState(themeMode = state.themeMode)
    }

    private fun loadThemeMode(): ThemeMode = (settings.load() as? Outcome.Ok)?.value?.themeMode ?: ThemeMode.SYSTEM

    @OptIn(ExperimentalTime::class)
    private fun activate(result: Outcome<OpenProject>): AppState {
        val opened = when (result) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> {
                logger.error { "Project activation failed: error=${result.error}" }
                state = state.copy(status = result.error.toRussianMessage())
                return state
            }
        }
        val tree = when (val loaded = opened.repository.load()) {
            is Outcome.Ok -> loaded.value
            is Outcome.Err -> {
                logger.error { "Project load failed: error=${loaded.error}" }
                opened.close()
                state = state.copy(status = loaded.error.toRussianMessage())
                return state
            }
        }
        project?.close()
        project = opened
        commandBus = CommandBus(initial = tree, repository = opened.repository)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        state = AppState(
            isProjectOpen = true,
            projectName = opened.location.displayName,
            personCount = tree.size,
            canvas = mappedCanvas(tree),
            status = "",
            themeMode = state.themeMode,
            personRows = me.terevo.ui.persons.mapPersonRows(tree, opened.mediaRepository, today),
            eventRows = me.terevo.ui.events.mapEventRows(tree, opened.mediaRepository, today),
        )
        return state
    }
}

private fun me.terevo.layout.NodeId.toPersonId(): PersonId = PersonId.parse(value)

private fun PersonId.toNodeId(): me.terevo.layout.NodeId = me.terevo.layout.NodeId(value.toString())

private fun readBoundedImage(path: Path): ByteArray {
    require(Files.size(path) <= MAX_PREVIEW_FILE_BYTES)
    ImageIO.createImageInputStream(path.toFile()).use { input ->
        requireNotNull(input)
        val readers = ImageIO.getImageReaders(input)
        require(readers.hasNext())
        val reader = readers.next()
        try {
            reader.input = input
            require(reader.getWidth(0) <= MAX_IMAGE_DIMENSION && reader.getHeight(0) <= MAX_IMAGE_DIMENSION)
        } finally {
            reader.dispose()
        }
    }
    return Files.readAllBytes(path)
}

private fun readBoundedFile(path: Path): ByteArray {
    require(Files.size(path) <= MAX_PREVIEW_FILE_BYTES)
    return Files.readAllBytes(path)
}

fun DomainError.toRussianMessage(): String = when (this) {
    is DomainError.Project.AlreadyExists -> "Файл проекта уже существует"
    is DomainError.Project.NotFound -> "Файл проекта не найден"
    is DomainError.Project.NotAProject -> "Выбранный файл не является проектом Terevo"
    is DomainError.Project.Corrupted -> "Файл проекта повреждён"
    is DomainError.Project.SchemaTooNew -> "Проект создан в более новой версии Terevo"
    is DomainError.Project.Locked -> "Проект уже открыт в другом окне"
    is DomainError.Storage.Failure -> "Не удалось выполнить операцию с файлом"
    is DomainError.Storage.CorruptedRecord -> "В проекте обнаружены повреждённые данные"
    else -> "Не удалось открыть проект"
}

private const val MAX_PREVIEW_FILE_BYTES: Long = 25L * 1024L * 1024L
private const val MAX_IMAGE_DIMENSION: Int = 10_000
private const val DEFAULT_PNG_SCALE: Double = 1.0
