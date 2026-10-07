package me.terevo.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import me.terevo.app.AppController
import me.terevo.app.ProjectDirectories
import me.terevo.domain.model.EventId
import me.terevo.domain.port.ProjectLocation
import me.terevo.domain.port.ThemeMode
import me.terevo.layout.LayoutDirection
import me.terevo.layout.LayoutMode
import me.terevo.layout.Point
import me.terevo.ui.AppState
import me.terevo.ui.LayoutDensity
import me.terevo.ui.MainTab
import me.terevo.ui.person.RelationMode

@Serializable
sealed interface Action {
    @Serializable @SerialName("createProject")
    data class CreateProject(val name: String) : Action

    @Serializable @SerialName("openProject")
    data class OpenProject(val path: String) : Action

    @Serializable @SerialName("createProjectFromGedcom")
    data class CreateProjectFromGedcom(val name: String, val gedcomPath: String) : Action

    @Serializable @SerialName("exportGedcom")
    data class ExportGedcom(val path: String) : Action

    @Serializable @SerialName("exportPdf")
    data class ExportPdf(val path: String) : Action

    @Serializable @SerialName("reportPngExport")
    data class ReportPngExport(val succeeded: Boolean) : Action

    @Serializable @SerialName("confirmGedcomImport")
    data object ConfirmGedcomImport : Action

    @Serializable @SerialName("cancelGedcomImport")
    data object CancelGedcomImport : Action

    @Serializable @SerialName("undo")
    data object Undo : Action

    @Serializable @SerialName("redo")
    data object Redo : Action

    @Serializable @SerialName("navigateBack")
    data object NavigateBack : Action

    @Serializable @SerialName("navigateForward")
    data object NavigateForward : Action

    @Serializable @SerialName("changeLayoutMode")
    data class ChangeLayoutMode(val mode: LayoutMode) : Action

    // null means unlimited depth.
    @Serializable @SerialName("changeLayoutDepth")
    data class ChangeLayoutDepth(val depth: Int?) : Action

    @Serializable @SerialName("changeLayoutDirection")
    data class ChangeLayoutDirection(val direction: LayoutDirection) : Action

    @Serializable @SerialName("changeLayoutDensity")
    data class ChangeLayoutDensity(val density: LayoutDensity) : Action

    @Serializable @SerialName("changeSearchFilter")
    data class ChangeSearchFilter(val filter: SearchFilterDto) : Action

    @Serializable @SerialName("addPerson")
    data object AddPerson : Action

    @Serializable @SerialName("editPerson")
    data class EditPerson(val id: String? = null) : Action

    @Serializable @SerialName("deletePerson")
    data class DeletePerson(val id: String? = null) : Action

    @Serializable @SerialName("addRelative")
    data class AddRelative(val mode: RelationMode, val id: String? = null) : Action

    @Serializable @SerialName("addMedia")
    data class AddMedia(val paths: List<String>) : Action

    @Serializable @SerialName("openMedia")
    data class OpenMedia(val id: String) : Action

    @Serializable @SerialName("removeMedia")
    data class RemoveMedia(val id: String) : Action

    @Serializable @SerialName("changeMediaZoom")
    data class ChangeMediaZoom(val zoom: Float) : Action

    @Serializable @SerialName("changeMediaPage")
    data class ChangeMediaPage(val delta: Int) : Action

    @Serializable @SerialName("closeMedia")
    data object CloseMedia : Action

    @Serializable @SerialName("selectPerson")
    data class SelectPerson(val id: String?) : Action

    @Serializable @SerialName("selectOnCanvas")
    data class SelectOnCanvas(val id: String?) : Action

    @Serializable @SerialName("selectSearchResult")
    data class SelectSearchResult(val id: String) : Action

    @Serializable @SerialName("clearHighlight")
    data object ClearHighlight : Action

    @Serializable @SerialName("viewPerson")
    data class ViewPerson(val id: String) : Action

    @Serializable @SerialName("closePersonView")
    data object ClosePersonView : Action

    @Serializable @SerialName("updatePersonForm")
    data class UpdatePersonForm(val fields: PersonFormFieldsDto) : Action

    @Serializable @SerialName("addPersonFormMedia")
    data class AddPersonFormMedia(val paths: List<String>) : Action

    @Serializable @SerialName("removePendingPersonMedia")
    data class RemovePendingPersonMedia(val path: String) : Action

    @Serializable @SerialName("savePerson")
    data object SavePerson : Action

    @Serializable @SerialName("cancelPerson")
    data object CancelPerson : Action

    @Serializable @SerialName("confirmDiscardPerson")
    data object ConfirmDiscardPerson : Action

    @Serializable @SerialName("keepEditingPerson")
    data object KeepEditingPerson : Action

    @Serializable @SerialName("updateRelationDialog")
    data class UpdateRelationDialog(val fields: RelationFieldsDto) : Action

    @Serializable @SerialName("createRelative")
    data object CreateRelative : Action

    @Serializable @SerialName("saveRelation")
    data object SaveRelation : Action

    @Serializable @SerialName("cancelRelation")
    data object CancelRelation : Action

    @Serializable @SerialName("startDrag")
    data class StartDrag(val source: String) : Action

    // x, y: where the card was dropped, in window coordinates, for placing the relation menu.
    @Serializable @SerialName("dropDrag")
    data class DropDrag(val target: String?, val x: Double, val y: Double) : Action

    @Serializable @SerialName("cancelDrag")
    data object CancelDrag : Action

    @Serializable @SerialName("chooseDragRelationMode")
    data class ChooseDragRelationMode(val mode: RelationMode) : Action

    @Serializable @SerialName("cancelDragRelationMenu")
    data object CancelDragRelationMenu : Action

    @Serializable @SerialName("openKinshipDialog")
    data object OpenKinshipDialog : Action

    @Serializable @SerialName("updateKinshipDialog")
    data class UpdateKinshipDialog(val query: String, val target: String?) : Action

    @Serializable @SerialName("closeKinshipDialog")
    data object CloseKinshipDialog : Action

    @Serializable @SerialName("openStatistics")
    data object OpenStatistics : Action

    @Serializable @SerialName("closeStatistics")
    data object CloseStatistics : Action

    @Serializable @SerialName("changeThemeMode")
    data class ChangeThemeMode(val mode: ThemeMode) : Action

    @Serializable @SerialName("changeMainTab")
    data class ChangeMainTab(val tab: MainTab) : Action

    @Serializable @SerialName("toggleSidebar")
    data object ToggleSidebar : Action

    @Serializable @SerialName("addEvent")
    data object AddEvent : Action

    @Serializable @SerialName("editEvent")
    data class EditEvent(val id: String) : Action

    @Serializable @SerialName("deleteEvent")
    data class DeleteEvent(val id: String) : Action

    @Serializable @SerialName("updateEventForm")
    data class UpdateEventForm(val fields: EventFormFieldsDto) : Action

    @Serializable @SerialName("saveEvent")
    data object SaveEvent : Action

    @Serializable @SerialName("cancelEvent")
    data object CancelEvent : Action
}

fun AppController.dispatch(action: Action, directories: ProjectDirectories): AppState = when (action) {
    is Action.CreateProject -> create(directories.location(action.name))
    is Action.OpenProject -> open(ProjectLocation(action.path))
    is Action.CreateProjectFromGedcom -> createFromGedcom(directories.location(action.name), action.gedcomPath)
    is Action.ExportGedcom -> exportGedcom(action.path)
    is Action.ExportPdf -> exportPdf(action.path)
    is Action.ReportPngExport -> reportPngExport(action.succeeded)
    Action.ConfirmGedcomImport -> confirmGedcomImport()
    Action.CancelGedcomImport -> cancelGedcomImport()
    Action.Undo -> undo()
    Action.Redo -> redo()
    Action.NavigateBack -> navigateBack()
    Action.NavigateForward -> navigateForward()
    is Action.ChangeLayoutMode -> changeLayoutMode(action.mode)
    is Action.ChangeLayoutDepth -> changeLayoutDepth(action.depth ?: Int.MAX_VALUE)
    is Action.ChangeLayoutDirection -> changeLayoutDirection(action.direction)
    is Action.ChangeLayoutDensity -> changeLayoutDensity(action.density)
    is Action.ChangeSearchFilter -> changeSearchFilter(action.filter.toFilter())
    Action.AddPerson -> startAddingPerson()
    is Action.EditPerson -> action.id?.let { editPerson(personIdOf(it)) } ?: startEditingPerson()
    is Action.DeletePerson -> action.id?.let { deletePerson(personIdOf(it)) } ?: deleteSelectedPerson()
    is Action.AddRelative ->
        action.id?.let { startAddingRelationFor(personIdOf(it), action.mode) } ?: startAddingRelation(action.mode)
    is Action.AddMedia -> action.paths.fold(state) { _, path -> importMedia(path) }
    is Action.OpenMedia -> openMedia(mediaIdOf(action.id))
    is Action.RemoveMedia -> removeMedia(mediaIdOf(action.id))
    is Action.ChangeMediaZoom -> changeMediaZoom(action.zoom)
    is Action.ChangeMediaPage -> changeMediaPage(action.delta)
    Action.CloseMedia -> closeMedia()
    is Action.SelectPerson -> selectPerson(action.id?.let(::personIdOf))
    is Action.SelectOnCanvas -> selectOnCanvas(action.id?.let(::personIdOf))
    is Action.SelectSearchResult -> selectSearchResult(personIdOf(action.id))
    Action.ClearHighlight -> clearHighlight()
    is Action.ViewPerson -> viewPerson(personIdOf(action.id))
    Action.ClosePersonView -> closePersonView()
    is Action.UpdatePersonForm -> state.personForm?.let { updatePersonForm(it.withFields(action.fields)) } ?: state
    is Action.AddPersonFormMedia -> addPendingPersonMedia(action.paths)
    is Action.RemovePendingPersonMedia -> removePendingPersonMedia(action.path)
    Action.SavePerson -> savePerson()
    Action.CancelPerson -> cancelPerson()
    Action.ConfirmDiscardPerson -> confirmDiscardPerson()
    Action.KeepEditingPerson -> keepEditingPerson()
    is Action.UpdateRelationDialog ->
        state.relationDialog?.let { updateRelationDialog(it.withFields(action.fields)) } ?: state
    Action.CreateRelative -> startCreatingRelative()
    Action.SaveRelation -> saveRelation()
    Action.CancelRelation -> cancelRelation()
    is Action.StartDrag -> startDrag(personIdOf(action.source))
    is Action.DropDrag -> dropDrag(action.target?.let(::personIdOf), Point(action.x, action.y))
    Action.CancelDrag -> cancelDrag()
    is Action.ChooseDragRelationMode -> chooseDragRelationMode(action.mode)
    Action.CancelDragRelationMenu -> cancelDragRelationMenu()
    Action.OpenKinshipDialog -> startResolvingKinship()
    is Action.UpdateKinshipDialog -> state.kinshipDialog?.let {
        updateKinshipDialog(it.copy(query = action.query, target = action.target?.let(::personIdOf)))
    } ?: state
    Action.CloseKinshipDialog -> closeKinshipDialog()
    Action.OpenStatistics -> openStatistics()
    Action.CloseStatistics -> closeStatistics()
    is Action.ChangeThemeMode -> changeThemeMode(action.mode)
    is Action.ChangeMainTab -> changeMainTab(action.tab)
    Action.ToggleSidebar -> toggleSidebar()
    Action.AddEvent -> startAddingEvent()
    is Action.EditEvent -> startEditingEvent(EventId.parse(action.id))
    is Action.DeleteEvent -> deleteEvent(EventId.parse(action.id))
    is Action.UpdateEventForm -> state.eventForm?.let { updateEventForm(it.withFields(action.fields)) } ?: state
    Action.SaveEvent -> saveEvent()
    Action.CancelEvent -> cancelEvent()
}
