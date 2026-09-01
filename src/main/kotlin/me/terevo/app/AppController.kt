package me.terevo.app

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.command.CommandBus
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.PersonId
import me.terevo.domain.port.OpenProject
import me.terevo.domain.port.ProjectLocation
import me.terevo.domain.port.ProjectService
import me.terevo.ui.AppState
import me.terevo.ui.person.KinshipRoles
import me.terevo.ui.person.PersonFormService
import me.terevo.ui.person.PersonFormState
import me.terevo.ui.person.RelationDialogState
import me.terevo.ui.person.RelationEditor
import me.terevo.ui.person.RelationMode
import me.terevo.ui.person.RelationResult
import me.terevo.ui.person.toPerson
import me.terevo.ui.person.validatePersonForm
import me.terevo.ui.tree.TreeCanvasIntent
import me.terevo.ui.tree.TreeCanvasMapper
import me.terevo.ui.tree.TreeCanvasState
import me.terevo.ui.tree.TreeHighlight
import me.terevo.ui.tree.reduceTreeCanvas

class AppController(
    private val projects: ProjectService,
) : AutoCloseable {
    private var project: OpenProject? = null

    var state: AppState = AppState()
        private set

    var commandBus: CommandBus? = null
        private set

    fun create(location: ProjectLocation): AppState = activate(projects.create(location))

    fun open(location: ProjectLocation): AppState = activate(projects.open(location))

    fun startAddingPerson(): AppState {
        if (commandBus == null) return state
        state = state.copy(personForm = validatePersonForm(PersonFormState()))
        return state
    }

    fun startEditingPerson(): AppState {
        val person = state.selectedPerson ?: return state
        state = state.copy(personForm = validatePersonForm(PersonFormState.fromPerson(person)))
        return state
    }

    fun updatePersonForm(form: PersonFormState): AppState {
        state = state.copy(personForm = validatePersonForm(form))
        return state
    }

    fun deleteSelectedPerson(): AppState {
        val person = state.selectedPerson ?: return state
        val bus = commandBus ?: return state
        state = when (PersonFormService(bus).delete(PersonFormState.fromPerson(person))) {
            is Outcome.Ok -> remapTree(bus.tree.value, selected = null).copy(status = "Человек удалён")
            is Outcome.Err -> state.copy(status = "Не удалось удалить человека")
        }
        return state
    }

    fun selectPerson(id: PersonId?): AppState {
        state = remapSelection(commandBus?.tree?.value ?: return state, id)
        return state
    }

    fun startAddingRelation(mode: RelationMode): AppState {
        val source = state.selectedPerson ?: return state
        val tree = commandBus?.tree?.value ?: return state
        state = state.copy(
            relationDialog = RelationDialogState(
                mode = mode,
                source = source,
                people = tree.persons.values.filter { it.id != source.id }.sortedBy { it.name.sortKey },
            ),
        )
        return state
    }

    fun updateRelationDialog(dialog: RelationDialogState): AppState {
        state = state.copy(relationDialog = dialog)
        return state
    }

    fun startCreatingRelative(): AppState {
        val relation = state.relationDialog ?: return state
        state = state.copy(
            personForm = validatePersonForm(PersonFormState()),
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
            RelationMode.CHILD -> editor.addChild(dialog.source.id, target, dialog.parentKind)
            RelationMode.SPOUSE -> editor.addSpouse(dialog.source.id, target, dialog.marriageStatus)
        }
        state = when (result) {
            is RelationResult.Success -> remapTree(commandBus?.tree?.value ?: return state, dialog.source.id).copy(
                relationDialog = null,
                status = "Связь сохранена",
            )
            is RelationResult.Error -> state.copy(relationDialog = dialog.copy(error = result.message))
        }
        return state
    }

    fun cancelRelation(): AppState {
        state = state.copy(relationDialog = null)
        return state
    }

    fun savePerson(): AppState {
        val form = state.personForm ?: return state
        val bus = commandBus ?: return state
        val pending = state.pendingRelation
        state = if (pending == null) {
            when (PersonFormService(bus).save(form)) {
                is Outcome.Ok -> remapTree(bus.tree.value, selected = form.id).copy(
                    personForm = null,
                    status = "Человек сохранён",
                )
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
            is TreeCanvasIntent.SelectAt -> remapSelection(commandBus?.tree?.value ?: return state, canvas.selected?.toPersonId())
            is TreeCanvasIntent.EditAt -> {
                val selected = canvas.spatialIndex.hitTest(canvas.camera.screenToWorld(intent.position))?.toPersonId()
                remapSelection(commandBus?.tree?.value ?: return state, selected).also { state = it }
                startEditingPerson()
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
        state = state.copy(personForm = null, pendingRelation = null)
        return state
    }

    private fun saveRelative(form: PersonFormState, relation: RelationDialogState, bus: CommandBus): AppState {
        val person = when (val value = form.toPerson()) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return state.copy(personForm = form.copy(blockingError = "Не удалось сохранить человека"))
        }
        val result = when (relation.mode) {
            RelationMode.PARENT -> RelationEditor(bus).addParentWithPerson(person, relation.source.id, relation.parentKind)
            RelationMode.CHILD -> RelationEditor(bus).addChildWithPerson(person, relation.source.id, relation.parentKind)
            RelationMode.SPOUSE -> RelationEditor(bus).addSpouseWithPerson(person, relation.source.id, relation.marriageStatus)
        }
        return when (result) {
            is RelationResult.Success -> remapTree(bus.tree.value, relation.source.id).copy(
                personForm = null,
                pendingRelation = null,
                status = "Человек и связь сохранены",
            )
            is RelationResult.Error -> state.copy(personForm = form.copy(blockingError = result.message))
        }
    }

    private fun mappedCanvas(tree: FamilyTree): TreeCanvasState {
        val mapped = TreeCanvasMapper.map(tree).copy(viewport = state.canvas.viewport)
        return if (mapped.viewport.width > 0.0 && mapped.viewport.height > 0.0 && mapped.layout.nodes.isNotEmpty()) {
            reduceTreeCanvas(mapped, TreeCanvasIntent.FitToScreen)
        } else {
            mapped
        }
    }

    private fun remapTree(tree: FamilyTree, selected: PersonId?): AppState {
        val source = state.copy(personCount = tree.size, canvas = mappedCanvas(tree))
        return remapSelection(tree, selected, source)
    }

    private fun remapSelection(tree: FamilyTree, selected: PersonId?, source: AppState = state): AppState {
        val person = selected?.let(tree::person)
        val relations = selected?.let(tree::relationsOf).orEmpty()
        val parents = relations.filterIsInstance<ParentChild>().filter { it.child == selected }.mapNotNull { tree.person(it.parent) }
        val children = relations.filterIsInstance<ParentChild>().filter { it.parent == selected }.mapNotNull { tree.person(it.child) }
        val spouses = relations.filterIsInstance<Marriage>().mapNotNull { relation ->
            relation.spouseOf(selected ?: return@mapNotNull null)?.let(tree::person)
        }
        val relatedPeople = selected?.let { KinshipRoles.resolve(tree, it) }.orEmpty()
        val highlight = TreeHighlight(
            selected = person?.id?.toNodeId(),
            roles = relatedPeople.associate { it.person.id.toNodeId() to it.role },
        )
        return source.copy(
            canvas = source.canvas.copy(highlight = highlight),
            selectedPerson = person,
            selectedParents = parents,
            selectedChildren = children,
            selectedSpouses = spouses,
            relatedPeople = relatedPeople,
        )
    }

    override fun close() {
        project?.close()
        project = null
        commandBus = null
        state = AppState()
    }

    private fun activate(result: Outcome<OpenProject>): AppState {
        val opened = when (result) {
            is Outcome.Ok -> result.value
            is Outcome.Err -> {
                state = state.copy(status = result.error.toRussianMessage())
                return state
            }
        }
        val tree = when (val loaded = opened.repository.load()) {
            is Outcome.Ok -> loaded.value
            is Outcome.Err -> {
                opened.close()
                state = state.copy(status = loaded.error.toRussianMessage())
                return state
            }
        }
        project?.close()
        project = opened
        commandBus = CommandBus(initial = tree, repository = opened.repository)
        state = AppState(
            isProjectOpen = true,
            projectName = opened.location.displayName,
            personCount = tree.size,
            canvas = mappedCanvas(tree),
            status = "Проект «${opened.location.displayName}» открыт",
        )
        return state
    }
}

private fun me.terevo.layout.NodeId.toPersonId(): PersonId = PersonId.parse(value)

private fun PersonId.toNodeId(): me.terevo.layout.NodeId = me.terevo.layout.NodeId(value.toString())

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
