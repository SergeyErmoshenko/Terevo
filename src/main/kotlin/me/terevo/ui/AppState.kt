package me.terevo.ui

import me.terevo.domain.command.Batch
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.domain.port.ThemeMode
import me.terevo.layout.LayoutDirection
import me.terevo.layout.LayoutMode
import me.terevo.layout.LayoutOptions
import me.terevo.statistics.Statistics
import me.terevo.ui.events.EventFormState
import me.terevo.ui.events.EventRow
import me.terevo.ui.kinship.KinshipDialogState
import me.terevo.ui.person.PersonFormState
import me.terevo.ui.person.PersonSearchFilter
import me.terevo.ui.person.RelatedPerson
import me.terevo.ui.person.RelationDialogState
import me.terevo.ui.person.SpouseInfo
import me.terevo.ui.persons.PersonRow
import me.terevo.ui.tree.DragRelationMenuState
import me.terevo.ui.tree.TreeCanvasState

data class GedcomImportState(
    val people: Int,
    val families: Int,
    val skippedTags: Set<String>,
    val command: Batch,
    val error: String? = null,
)

// imagePath is the file to show: the image itself, or the current page of a PDF rendered to PNG.
data class MediaViewerState(
    val media: Media,
    val imagePath: String?,
    val pageCount: Int = 1,
    val zoom: Float = 1f,
    val page: Int = 0,
)

enum class LayoutDensity {
    COMPACT,
    SPACIOUS,
}

val LayoutDensity.siblingSpacing: Double
    get() = when (this) {
        LayoutDensity.COMPACT -> LayoutOptions.DEFAULT_SIBLING_SPACING / 2
        LayoutDensity.SPACIOUS -> LayoutOptions.DEFAULT_SIBLING_SPACING
    }

val LayoutDensity.subtreeSpacing: Double
    get() = when (this) {
        LayoutDensity.COMPACT -> LayoutOptions.DEFAULT_SUBTREE_SPACING / 2
        LayoutDensity.SPACIOUS -> LayoutOptions.DEFAULT_SUBTREE_SPACING
    }

val LayoutDensity.generationSpacing: Double
    get() = when (this) {
        LayoutDensity.COMPACT -> LayoutOptions.DEFAULT_GENERATION_SPACING / 2
        LayoutDensity.SPACIOUS -> LayoutOptions.DEFAULT_GENERATION_SPACING
    }

val LayoutDensity.spouseSpacing: Double
    get() = when (this) {
        LayoutDensity.COMPACT -> LayoutOptions.DEFAULT_SPOUSE_SPACING / 2
        LayoutDensity.SPACIOUS -> LayoutOptions.DEFAULT_SPOUSE_SPACING
    }

data class AppState(
    val isProjectOpen: Boolean = false,
    val projectName: String = "",
    val personCount: Int = 0,
    val canvas: TreeCanvasState = TreeCanvasState(),
    val layoutMode: LayoutMode = LayoutMode.WHOLE_FAMILY,
    val layoutDepth: Int = Int.MAX_VALUE,
    val layoutDirection: LayoutDirection = LayoutDirection.TOP_DOWN,
    val layoutDensity: LayoutDensity = LayoutDensity.SPACIOUS,
    val searchFilter: PersonSearchFilter = PersonSearchFilter(),
    val searchResults: List<Person> = emptyList(),
    val selectedPerson: Person? = null,
    val selectedParents: List<Person> = emptyList(),
    val selectedChildren: List<Person> = emptyList(),
    val selectedSpouses: List<Person> = emptyList(),
    val selectedSpouseMarriages: List<SpouseInfo> = emptyList(),
    val selectedMedia: List<Media> = emptyList(),
    val selectedPersonPhotoPath: String? = null,
    val selectedMediaThumbnails: Map<MediaId, String?> = emptyMap(),
    val relatedPeople: List<RelatedPerson> = emptyList(),
    val selectionBackHistory: List<PersonId> = emptyList(),
    val selectionForwardHistory: List<PersonId> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String = Strings.UNDO,
    val redoLabel: String = Strings.REDO,
    val personForm: PersonFormState? = null,
    val relationDialog: RelationDialogState? = null,
    val dragRelationMenu: DragRelationMenuState? = null,
    val pendingRelation: RelationDialogState? = null,
    val kinshipDialog: KinshipDialogState? = null,
    val statistics: Statistics? = null,
    val gedcomPreview: GedcomImportState? = null,
    val mediaViewer: MediaViewerState? = null,
    val status: String = Strings.NO_PROJECT,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val mainTab: MainTab = MainTab.TREE,
    val sidebarCollapsed: Boolean = false,
    val personRows: List<PersonRow> = emptyList(),
    val eventRows: List<EventRow> = emptyList(),
    val eventForm: EventFormState? = null,
    val personViewOpen: Boolean = false,
    // People a drag from dragSource may be dropped on; computed when a card drag starts.
    val dragSource: PersonId? = null,
    val dragTargets: Set<PersonId> = emptySet(),
)
