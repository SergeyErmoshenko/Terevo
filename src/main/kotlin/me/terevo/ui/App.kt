package me.terevo.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import compose.icons.TablerIcons
import compose.icons.tablericons.*
import me.terevo.domain.command.Batch
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.model.Person
import me.terevo.layout.LayoutDirection
import me.terevo.layout.LayoutMode
import me.terevo.layout.LayoutOptions
import me.terevo.statistics.Statistics
import me.terevo.ui.components.SegmentedControl
import me.terevo.ui.components.TerevoCard
import me.terevo.ui.kinship.KinshipDialog
import me.terevo.ui.kinship.KinshipDialogState
import me.terevo.ui.person.*
import me.terevo.ui.statistics.StatisticsScreen
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.*
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.datatransfer.DataFlavor
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.Image as SkiaImage

data class GedcomImportState(
    val people: Int,
    val families: Int,
    val skippedTags: Set<String>,
    val command: Batch,
    val error: String? = null,
)

data class MediaViewerState(
    val media: Media,
    val content: ByteArray,
    val zoom: Float = 1f,
    val page: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaViewerState

        if (zoom != other.zoom) return false
        if (page != other.page) return false
        if (media != other.media) return false
        if (!content.contentEquals(other.content)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = zoom.hashCode()
        result = 31 * result + page
        result = 31 * result + media.hashCode()
        result = 31 * result + content.contentHashCode()
        return result
    }
}

private fun ByteArray.pdfPageCount(): Int = Loader.loadPDF(this).use { it.numberOfPages }

private fun ByteArray.renderPdfPreview(page: Int): ImageBitmap {
    Loader.loadPDF(this).use { document ->
        val rendered = PDFRenderer(document).renderImageWithDPI(page, 150f)
        val output = ByteArrayOutputStream()
        ImageIO.write(rendered, "png", output)
        return SkiaImage.makeFromEncoded(output.toByteArray()).toComposeImageBitmap()
    }
}

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
    val selectedSpouseMarriages: List<me.terevo.ui.person.SpouseInfo> = emptyList(),
    val selectedMedia: List<Media> = emptyList(),
    val selectedPersonPhotoPath: String? = null,
    val selectedMediaThumbnails: Map<me.terevo.domain.model.MediaId, String?> = emptyMap(),
    val relatedPeople: List<RelatedPerson> = emptyList(),
    val selectionBackHistory: List<me.terevo.domain.model.PersonId> = emptyList(),
    val selectionForwardHistory: List<me.terevo.domain.model.PersonId> = emptyList(),
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
    val themeMode: me.terevo.domain.port.ThemeMode = me.terevo.domain.port.ThemeMode.SYSTEM,
    val mainTab: MainTab = MainTab.TREE,
    val sidebarCollapsed: Boolean = false,
    val personRows: List<me.terevo.ui.persons.PersonRow> = emptyList(),
    val eventRows: List<me.terevo.ui.events.EventRow> = emptyList(),
    val eventForm: me.terevo.ui.events.EventFormState? = null,
    val personViewOpen: Boolean = false,
)

sealed interface AppAction {
    data object NewProject : AppAction
    data object OpenProject : AppAction
    data object NewProjectFromGedcom : AppAction
    data object ExportGedcom : AppAction
    data object ConfirmGedcomImport : AppAction
    data object CancelGedcomImport : AppAction
    data object Undo : AppAction
    data object Redo : AppAction
    data object FitToScreen : AppAction
    data object ActualSize : AppAction
    data object CenterSelected : AppAction
    data object ClearSelection : AppAction
    data object NavigateBack : AppAction
    data object NavigateForward : AppAction
    data class ChangeLayoutMode(val mode: LayoutMode) : AppAction
    data class ChangeLayoutDepth(val depth: Int) : AppAction
    data class ChangeLayoutDirection(val direction: LayoutDirection) : AppAction
    data class ChangeLayoutDensity(val density: LayoutDensity) : AppAction
    data class ChangeSearchFilter(val filter: PersonSearchFilter) : AppAction
    data object AddPerson : AppAction
    data object EditPerson : AppAction
    data object DeletePerson : AppAction

    // Carries the id explicitly because the tree's context menu opens on right-click without
    // selecting the card, so there is no "selected person" to act on.
    data class DeletePersonById(val id: me.terevo.domain.model.PersonId) : AppAction
    data object AddParent : AppAction
    data object AddChild : AppAction
    data object AddSpouse : AppAction
    data object ChooseMedia : AppAction
    data class DropMedia(val paths: List<String>) : AppAction
    data class OpenMedia(val id: MediaId) : AppAction
    data class RemoveMedia(val id: MediaId) : AppAction
    data class ChangeMediaZoom(val zoom: Float) : AppAction
    data class ChangeMediaPage(val delta: Int) : AppAction
    data object CloseMedia : AppAction
    data class SelectPerson(val id: me.terevo.domain.model.PersonId) : AppAction
    data class SelectSearchResult(val id: me.terevo.domain.model.PersonId) : AppAction
    data class UpdatePersonForm(val form: PersonFormState) : AppAction
    data class UpdateRelationDialog(val dialog: RelationDialogState) : AppAction
    data class Canvas(val intent: TreeCanvasIntent) : AppAction
    data object SavePerson : AppAction
    data object CancelPerson : AppAction
    data object ConfirmDiscardPerson : AppAction
    data object KeepEditingPerson : AppAction
    data object CreateRelative : AppAction
    data object SaveRelation : AppAction
    data object CancelRelation : AppAction
    data class ChooseDragRelationMode(val mode: RelationMode) : AppAction
    data object CancelDragRelationMenu : AppAction
    data object OpenKinshipDialog : AppAction
    data class UpdateKinshipDialog(val dialog: KinshipDialogState) : AppAction
    data object CloseKinshipDialog : AppAction
    data object OpenStatistics : AppAction
    data object CloseStatistics : AppAction
    data object ExportPng : AppAction
    data object ExportPdf : AppAction
    data class ChangeThemeMode(val mode: me.terevo.domain.port.ThemeMode) : AppAction
    data class ChangeMainTab(val tab: MainTab) : AppAction
    data object ToggleSidebar : AppAction
    data object ChoosePersonFormMedia : AppAction
    data class DropPersonFormMedia(val paths: List<String>) : AppAction
    data class RemovePendingPersonMedia(val path: String) : AppAction
    data class ViewPerson(val id: me.terevo.domain.model.PersonId) : AppAction
    data object ClosePersonView : AppAction
    data object AddEvent : AppAction
    data class EditEvent(val id: me.terevo.domain.model.EventId) : AppAction
    data class DeleteEvent(val id: me.terevo.domain.model.EventId) : AppAction
    data class UpdateEventForm(val form: me.terevo.ui.events.EventFormState) : AppAction
    data object SaveEvent : AppAction
    data object CancelEvent : AppAction
}

data class EmptyProjectAction(
    val label: String,
    val action: AppAction,
)

val emptyProjectActions: List<EmptyProjectAction> = listOf(
    EmptyProjectAction(Strings.NEW_PROJECT, AppAction.NewProject),
    EmptyProjectAction(Strings.OPEN_PROJECT, AppAction.OpenProject),
    EmptyProjectAction(Strings.IMPORT_GEDCOM, AppAction.NewProjectFromGedcom),
)

@Composable
fun App(
    state: AppState,
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Surface(modifier = modifier.fillMaxSize(), color = colors.canvas, contentColor = colors.textPrimary) {
        Column(Modifier.fillMaxSize()) {
            if (state.isProjectOpen) {
                SegmentedControl(
                    options = MainTab.entries,
                    selected = state.mainTab,
                    label = { it.label },
                    onSelect = { onAction(AppAction.ChangeMainTab(it)) },
                    modifier = Modifier.padding(spacing.small),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (!state.isProjectOpen) {
                    MainTreeTab(state, onAction)
                } else {
                    when (state.mainTab) {
                        MainTab.TREE -> MainTreeTab(state, onAction)
                        MainTab.PERSONS -> me.terevo.ui.persons.PersonsScreen(
                            rows = state.personRows,
                            onSelect = { onAction(AppAction.ViewPerson(it)) },
                            onEdit = {
                                onAction(AppAction.SelectPerson(it))
                                onAction(AppAction.EditPerson)
                            },
                            onAddPerson = { onAction(AppAction.AddPerson) },
                        )

                        MainTab.EVENTS -> me.terevo.ui.events.EventsScreen(
                            rows = state.eventRows,
                            onAddEvent = { onAction(AppAction.AddEvent) },
                            onEditEvent = { onAction(AppAction.EditEvent(it)) },
                            onDeleteEvent = { onAction(AppAction.DeleteEvent(it)) },
                        )

                        MainTab.DOCUMENTS -> me.terevo.ui.documents.DocumentsScreen(state, onAction)
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(spacing.statusBarHeight)
                    .background(colors.statusBar)
                    .padding(horizontal = spacing.small),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(state.status)
            }
        }
    }
    state.personForm?.let { form ->
        PersonFormDialog(
            state = form,
            onChange = { onAction(AppAction.UpdatePersonForm(it)) },
            onSave = { onAction(AppAction.SavePerson) },
            onCancel = { onAction(AppAction.CancelPerson) },
            onConfirmDiscard = { onAction(AppAction.ConfirmDiscardPerson) },
            onKeepEditing = { onAction(AppAction.KeepEditingPerson) },
            onChooseMedia = { onAction(AppAction.ChoosePersonFormMedia) },
            onDropMedia = { onAction(AppAction.DropPersonFormMedia(it)) },
            onRemovePendingMedia = { onAction(AppAction.RemovePendingPersonMedia(it)) },
        )
    }
    state.eventForm?.let { form ->
        me.terevo.ui.events.EventFormDialog(
            state = form,
            onChange = { onAction(AppAction.UpdateEventForm(it)) },
            onSave = { onAction(AppAction.SaveEvent) },
            onCancel = { onAction(AppAction.CancelEvent) },
        )
    }
    if (state.personViewOpen) {
        state.selectedPerson?.let { person ->
            PersonViewDialog(
                person = person,
                photoPath = state.selectedPersonPhotoPath,
                parents = state.selectedParents,
                children = state.selectedChildren,
                spouses = state.selectedSpouseMarriages,
                media = state.selectedMedia,
                mediaThumbnails = state.selectedMediaThumbnails,
                onEdit = { onAction(AppAction.EditPerson) },
                onClose = { onAction(AppAction.ClosePersonView) },
                onOpenMedia = { onAction(AppAction.OpenMedia(it)) },
            )
        }
    }
    state.relationDialog?.let { dialog ->
        RelationDialog(
            state = dialog,
            onChange = { onAction(AppAction.UpdateRelationDialog(it)) },
            onCreatePerson = { onAction(AppAction.CreateRelative) },
            onSave = { onAction(AppAction.SaveRelation) },
            onCancel = { onAction(AppAction.CancelRelation) },
        )
    }
    state.kinshipDialog?.let { dialog ->
        KinshipDialog(
            state = dialog,
            onChange = { onAction(AppAction.UpdateKinshipDialog(it)) },
            onClose = { onAction(AppAction.CloseKinshipDialog) },
        )
    }
    state.gedcomPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { onAction(AppAction.CancelGedcomImport) },
            title = { Text(Strings.GEDCOM_PREVIEW) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Text("${Strings.GEDCOM_PEOPLE}: ${preview.people}")
                    Text("${Strings.GEDCOM_FAMILIES}: ${preview.families}")
                    if (preview.skippedTags.isNotEmpty()) {
                        Text("${Strings.GEDCOM_SKIPPED}: ${preview.skippedTags.sorted().joinToString()}")
                    }
                    preview.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = { onAction(AppAction.ConfirmGedcomImport) }) { Text(Strings.IMPORT) }
            },
            dismissButton = {
                OutlinedButton(onClick = { onAction(AppAction.CancelGedcomImport) }) { Text(Strings.CANCEL) }
            },
        )
    }
    state.mediaViewer?.let { viewer ->
        val isPdf = viewer.media.mimeType == "application/pdf"
        val pageCount = remember(viewer.content) {
            if (isPdf) runCatching { viewer.content.pdfPageCount() }.getOrDefault(1) else 1
        }
        val page = viewer.page.coerceIn(0, pageCount - 1)
        val image = remember(viewer.content, page) {
            runCatching {
                when {
                    viewer.media.mimeType.startsWith("image/") ->
                        SkiaImage.makeFromEncoded(viewer.content).toComposeImageBitmap()

                    isPdf -> viewer.content.renderPdfPreview(page)
                    else -> null
                }
            }.getOrNull()
        }
        Dialog(
            onDismissRequest = { onAction(AppAction.CloseMedia) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(modifier = Modifier.fillMaxSize(), tonalElevation = spacing.small) {
                Column(modifier = Modifier.fillMaxSize().padding(spacing.large)) {
                    Text(viewer.media.fileName, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(spacing.small))
                    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val viewportWidth = maxWidth
                        val viewportHeight = maxHeight
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (image == null) {
                                Text(viewer.media.mimeType)
                            } else {
                                val aspect = image.width.toFloat() / image.height.toFloat()
                                val fitsByHeight = viewportWidth.value / viewportHeight.value > aspect
                                val fitWidth = if (fitsByHeight) viewportHeight * aspect else viewportWidth
                                val fitHeight = if (fitsByHeight) viewportHeight else viewportWidth / aspect
                                Image(
                                    bitmap = image,
                                    contentDescription = viewer.media.fileName,
                                    modifier = Modifier.size(fitWidth * viewer.zoom, fitHeight * viewer.zoom),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(spacing.small))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isPdf && pageCount > 1) {
                            OutlinedButton(
                                onClick = { onAction(AppAction.ChangeMediaPage(-1)) },
                                enabled = page > 0,
                            ) {
                                Icon(
                                    TablerIcons.ChevronLeft,
                                    contentDescription = Strings.PREVIOUS_PAGE
                                )
                            }
                            Text("${page + 1} / $pageCount")
                            OutlinedButton(
                                onClick = { onAction(AppAction.ChangeMediaPage(1)) },
                                enabled = page < pageCount - 1,
                            ) {
                                Icon(
                                    TablerIcons.ChevronRight,
                                    contentDescription = Strings.NEXT_PAGE
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(
                            onClick = { onAction(AppAction.ChangeMediaZoom(viewer.zoom / 1.25f)) },
                        ) { Text(Strings.ZOOM_OUT) }
                        OutlinedButton(
                            onClick = { onAction(AppAction.ChangeMediaZoom(viewer.zoom * 1.25f)) },
                        ) { Text(Strings.ZOOM_IN) }
                        Button(onClick = { onAction(AppAction.CloseMedia) }) { Text(Strings.CLOSE) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTreeTab(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    val statistics = state.statistics
    if (statistics != null) {
        StatisticsScreen(
            statistics = statistics,
            onClose = { onAction(AppAction.CloseStatistics) },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Box(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.canvas)
                    .padding(start = if (state.isProjectOpen) 0.dp else spacing.sidebarWidth),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    if (!state.isProjectOpen) {
                        Icon(
                            TablerIcons.Folder,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(EMPTY_STATE_ICON_SIZE.dp),
                        )
                        Text(Strings.EMPTY_PROJECT, color = colors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            Button(onClick = { onAction(emptyProjectActions[0].action) }) {
                                Text(emptyProjectActions[0].label)
                            }
                            OutlinedButton(onClick = { onAction(emptyProjectActions[1].action) }) {
                                Text(emptyProjectActions[1].label)
                            }
                            OutlinedButton(onClick = { onAction(emptyProjectActions[2].action) }) {
                                Text(emptyProjectActions[2].label)
                            }
                        }
                    } else if (state.personCount == 0) {
                        Icon(
                            TablerIcons.Sitemap,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(EMPTY_STATE_ICON_SIZE.dp),
                        )
                        Text(Strings.EMPTY_TREE, color = colors.textSecondary)
                        Button(onClick = { onAction(AppAction.AddPerson) }) {
                            Icon(TablerIcons.UserPlus, contentDescription = null)
                            Text(Strings.ADD_PERSON, modifier = Modifier.padding(start = spacing.small))
                        }
                    }
                }
                if (state.isProjectOpen && state.personCount > 0) {
                    TreeCanvas(
                        state = state.canvas,
                        onIntent = { onAction(AppAction.Canvas(it)) },
                        onViewPerson = { onAction(AppAction.ViewPerson(it)) },
                        onDeletePerson = { onAction(AppAction.DeletePersonById(it)) },
                    )
                    FloatingActionButton(
                        onClick = { onAction(AppAction.FitToScreen) },
                        containerColor = colors.accent,
                        contentColor = colors.cardSurface,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(spacing.medium),
                    ) {
                        Icon(TablerIcons.Crosshair, contentDescription = Strings.FIT_TO_SCREEN)
                    }
                    state.dragRelationMenu?.let { menu ->
                        DragRelationMenu(
                            state = menu,
                            onChoose = { onAction(AppAction.ChooseDragRelationMode(it)) },
                            onDismiss = { onAction(AppAction.CancelDragRelationMenu) },
                        )
                    }
                }
            }

            if (state.isProjectOpen) {
                val sidebarWidth by animateDpAsState(
                    targetValue = if (state.sidebarCollapsed) 0.dp else spacing.sidebarWidth,
                    animationSpec = tween(SIDEBAR_ANIMATION_MS),
                )
                Box(
                    Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .background(colors.sidebar),
                ) {
                    if (sidebarWidth > 0.dp) {
                        Box(
                            Modifier
                                .width(spacing.sidebarWidth)
                                .fillMaxHeight()
                                .padding(spacing.medium),
                        ) {
                            ProjectSidebar(state, onAction)
                        }
                    }
                }
                IconButton(
                    onClick = { onAction(AppAction.ToggleSidebar) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = sidebarWidth + spacing.small, top = spacing.small),
                ) {
                    Icon(
                        if (state.sidebarCollapsed) {
                            TablerIcons.ChevronRight
                        } else {
                            TablerIcons.ChevronLeft
                        },
                        contentDescription = if (state.sidebarCollapsed) Strings.EXPAND_SIDEBAR else Strings.COLLAPSE_SIDEBAR,
                    )
                }
            } else {
                Box(
                    Modifier
                        .width(spacing.sidebarWidth)
                        .fillMaxHeight()
                        .background(colors.sidebar),
                )
            }
        }
    }
}

@Composable
private fun ProjectSidebar(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    var searchFiltersCollapsed by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text("${Strings.PROJECT}: ${state.projectName}")
        Text("${Strings.PEOPLE}: ${state.personCount}")
        Button(onClick = { onAction(AppAction.AddPerson) }, modifier = Modifier.fillMaxWidth()) {
            Icon(TablerIcons.UserPlus, contentDescription = null)
            Text(Strings.ADD_PERSON, modifier = Modifier.padding(start = spacing.small))
        }
        OutlinedButton(onClick = { onAction(AppAction.ExportGedcom) }, modifier = Modifier.fillMaxWidth()) {
            Icon(TablerIcons.FileExport, contentDescription = null)
            Text(Strings.EXPORT_GEDCOM, modifier = Modifier.padding(start = spacing.small))
        }
        TerevoCard {
            Column(
                modifier = Modifier.padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { searchFiltersCollapsed = !searchFiltersCollapsed },
                ) {
                    Icon(TablerIcons.Filter, contentDescription = null, tint = colors.accent)
                    Text(
                        Strings.SEARCH_FILTERS,
                        modifier = Modifier.padding(start = spacing.small).weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Icon(
                        if (searchFiltersCollapsed) TablerIcons.ChevronDown else TablerIcons.ChevronUp,
                        contentDescription = if (searchFiltersCollapsed) {
                            Strings.EXPAND_SEARCH_FILTERS
                        } else {
                            Strings.COLLAPSE_SEARCH_FILTERS
                        },
                    )
                }
                if (searchFiltersCollapsed) return@Column
                OutlinedTextField(
                    value = state.searchFilter.query,
                    onValueChange = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(query = it))) },
                    label = { Text(Strings.SEARCH) },
                    leadingIcon = { Icon(TablerIcons.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.searchFilter.place,
                    onValueChange = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(place = it))) },
                    label = { Text(Strings.SEARCH_PLACE) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    Strings.BIRTH_YEAR_RANGE,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedTextField(
                        value = state.searchFilter.birthYearFrom?.toString().orEmpty(),
                        onValueChange = {
                            onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(birthYearFrom = it.toIntOrNull())))
                        },
                        label = { Text(Strings.YEAR_FROM) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.searchFilter.birthYearTo?.toString().orEmpty(),
                        onValueChange = {
                            onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(birthYearTo = it.toIntOrNull())))
                        },
                        label = { Text(Strings.YEAR_TO) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    Strings.DEATH_YEAR_RANGE,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedTextField(
                        value = state.searchFilter.deathYearFrom?.toString().orEmpty(),
                        onValueChange = {
                            onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(deathYearFrom = it.toIntOrNull())))
                        },
                        label = { Text(Strings.YEAR_FROM) },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.searchFilter.deathYearTo?.toString().orEmpty(),
                        onValueChange = {
                            onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(deathYearTo = it.toIntOrNull())))
                        },
                        label = { Text(Strings.YEAR_TO) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(Strings.FILTER_GENDER, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                SegmentedControl(
                    options = listOf<Gender?>(null).plus(Gender.entries),
                    selected = state.searchFilter.gender,
                    label = { it?.searchLabel ?: Strings.FILTER_ANY },
                    onSelect = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(gender = it))) },
                )
                Text(
                    Strings.FILTER_HAS_PARENTS,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                SegmentedControl(
                    options = listOf(null, true, false),
                    selected = state.searchFilter.hasParents,
                    label = {
                        when (it) {
                            true -> Strings.FILTER_HAS_PARENTS
                            false -> Strings.FILTER_NO_PARENTS
                            null -> Strings.FILTER_ANY
                        }
                    },
                    onSelect = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(hasParents = it))) },
                )
                Text(
                    Strings.FILTER_HAS_DATES,
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary
                )
                SegmentedControl(
                    options = listOf(null, true, false),
                    selected = state.searchFilter.hasDates,
                    label = {
                        when (it) {
                            true -> Strings.FILTER_HAS_DATES
                            false -> Strings.FILTER_NO_DATES
                            null -> Strings.FILTER_ANY
                        }
                    },
                    onSelect = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(hasDates = it))) },
                )
                if (!state.searchFilter.isEmpty()) {
                    OutlinedButton(
                        onClick = { onAction(AppAction.ChangeSearchFilter(PersonSearchFilter())) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(TablerIcons.FilterOff, contentDescription = null)
                        Text(Strings.CLEAR_FILTERS, modifier = Modifier.padding(start = spacing.small))
                    }
                }
                if (state.searchResults.isNotEmpty()) {
                    Text("${Strings.SEARCH_RESULTS}: ${state.searchResults.size}")
                    state.searchResults.forEach { result ->
                        OutlinedButton(
                            onClick = { onAction(AppAction.SelectSearchResult(result.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(result.name.display) }
                    }
                }
            }
        }
        TerevoCard {
            Column(
                modifier = Modifier.padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Sitemap, contentDescription = null, tint = colors.accent)
                    Text(
                        Strings.TREE_MODE,
                        modifier = Modifier.padding(start = spacing.small),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                SegmentedControl(
                    options = LayoutMode.entries,
                    selected = state.layoutMode,
                    label = { it.label },
                    onSelect = { onAction(AppAction.ChangeLayoutMode(it)) },
                )
                Text("${Strings.TREE_DEPTH}: ${state.layoutDepthLabel}")
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedButton(
                        onClick = { onAction(AppAction.ChangeLayoutDepth(state.previousLayoutDepth)) },
                        enabled = state.layoutDepth == Int.MAX_VALUE || state.layoutDepth > 1,
                    ) { Text(Strings.DECREASE) }
                    OutlinedButton(
                        onClick = { onAction(AppAction.ChangeLayoutDepth(state.nextLayoutDepth)) },
                        enabled = state.layoutDepth != Int.MAX_VALUE && state.layoutDepth < MAX_LAYOUT_DEPTH,
                    ) { Text(Strings.INCREASE) }
                }
                OutlinedButton(
                    onClick = { onAction(AppAction.ChangeLayoutDepth(Int.MAX_VALUE)) },
                    enabled = state.layoutDepth != Int.MAX_VALUE,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(Strings.TREE_UNLIMITED, maxLines = 1) }
                Text(Strings.LAYOUT_DENSITY, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
                SegmentedControl(
                    options = LayoutDensity.entries,
                    selected = state.layoutDensity,
                    label = { it.label },
                    onSelect = { onAction(AppAction.ChangeLayoutDensity(it)) },
                )
            }
        }
        val person = state.selectedPerson ?: return@Column
        HorizontalDivider()
        TerevoCard {
            Column(
                modifier = Modifier.padding(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(person.name.display)
                Text(person.lifeSpan.displayText(), color = colors.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedButton(onClick = { onAction(AppAction.EditPerson) }) {
                        Icon(TablerIcons.Edit, contentDescription = null)
                        Text(Strings.EDIT_PERSON, modifier = Modifier.padding(start = spacing.small))
                    }
                    OutlinedButton(onClick = { onAction(AppAction.DeletePerson) }) {
                        Icon(TablerIcons.Trash, contentDescription = null, tint = colors.error)
                        Text(Strings.DELETE_PERSON, modifier = Modifier.padding(start = spacing.small))
                    }
                }
                Button(onClick = { onAction(AppAction.AddParent) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(TablerIcons.ArrowUp, contentDescription = null)
                    Text(Strings.ADD_PARENT, modifier = Modifier.padding(start = spacing.small))
                }
                Button(onClick = { onAction(AppAction.AddChild) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(TablerIcons.ArrowDown, contentDescription = null)
                    Text(Strings.ADD_CHILD, modifier = Modifier.padding(start = spacing.small))
                }
                Button(onClick = { onAction(AppAction.AddSpouse) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(TablerIcons.Heart, contentDescription = null)
                    Text(person.gender.spouseActionLabel(), modifier = Modifier.padding(start = spacing.small))
                }
                OutlinedButton(
                    onClick = { onAction(AppAction.OpenKinshipDialog) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(TablerIcons.Sitemap, contentDescription = null)
                    Text(Strings.DETERMINE_KINSHIP, modifier = Modifier.padding(start = spacing.small))
                }
            }
        }
        HorizontalDivider()
        TerevoCard {
            val mediaDropTarget = object : DragAndDropTarget {
                @OptIn(ExperimentalComposeUiApi::class)
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val transferable = event.awtTransferable
                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                    @Suppress("UNCHECKED_CAST")
                    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                        ?: return false
                    onAction(AppAction.DropMedia(files.map { it.absolutePath }))
                    return true
                }
            }
            Column(
                modifier = Modifier
                    .padding(spacing.small)
                    .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = mediaDropTarget),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(TablerIcons.Paperclip, contentDescription = null, tint = colors.accent)
                    Text(
                        Strings.MEDIA,
                        modifier = Modifier.padding(start = spacing.small),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedButton(onClick = { onAction(AppAction.ChooseMedia) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(TablerIcons.Paperclip, contentDescription = null)
                    Text(Strings.ADD_MEDIA, modifier = Modifier.padding(start = spacing.small))
                }
                state.selectedMedia.forEach { media ->
                    Text(media.fileName)
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                        OutlinedButton(onClick = { onAction(AppAction.OpenMedia(media.id)) }) {
                            Icon(TablerIcons.ExternalLink, contentDescription = null)
                            Text(Strings.OPEN_MEDIA, modifier = Modifier.padding(start = spacing.small))
                        }
                        OutlinedButton(onClick = { onAction(AppAction.RemoveMedia(media.id)) }) {
                            Icon(TablerIcons.Trash, contentDescription = null, tint = colors.error)
                            Text(Strings.REMOVE_MEDIA, modifier = Modifier.padding(start = spacing.small))
                        }
                    }
                }
            }
        }
        RelatedPeople(Strings.PARENTS, state.selectedParents, state, onAction)
        RelatedPeople(Strings.CHILDREN, state.selectedChildren, state, onAction)
        RelatedPeople(Strings.SPOUSES, state.selectedSpouses, state, onAction)
    }
}

private val AppState.layoutDepthLabel: String
    get() = if (layoutDepth == Int.MAX_VALUE) Strings.TREE_UNLIMITED else layoutDepth.toString()

private val AppState.previousLayoutDepth: Int
    get() = (layoutDepth.takeIf { it != Int.MAX_VALUE } ?: 2) - 1

private val AppState.nextLayoutDepth: Int
    get() = (layoutDepth + 1).coerceAtMost(MAX_LAYOUT_DEPTH)

private val LayoutMode.label: String
    get() = when (this) {
        LayoutMode.ANCESTORS -> Strings.TREE_ANCESTORS
        LayoutMode.DESCENDANTS -> Strings.TREE_DESCENDANTS
        LayoutMode.BOTH -> Strings.TREE_BOTH
        LayoutMode.WHOLE_FAMILY -> Strings.TREE_WHOLE_FAMILY
    }

private val LayoutDensity.label: String
    get() = when (this) {
        LayoutDensity.COMPACT -> Strings.DENSITY_COMPACT
        LayoutDensity.SPACIOUS -> Strings.DENSITY_SPACIOUS
    }

private val Gender.searchLabel: String
    get() = when (this) {
        Gender.MALE -> Strings.GENDER_MALE
        Gender.FEMALE -> Strings.GENDER_FEMALE
        Gender.UNKNOWN -> Strings.GENDER_UNKNOWN
    }

@Composable
private fun RelatedPeople(
    title: String,
    people: List<Person>,
    state: AppState,
    onAction: (AppAction) -> Unit,
) {
    if (people.isEmpty()) return
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    HorizontalDivider()
    TerevoCard {
        Column(
            modifier = Modifier.padding(spacing.small),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            people.forEach { person ->
                val role = state.relatedPeople.firstOrNull { it.person.id == person.id }?.role
                OutlinedButton(
                    onClick = { onAction(AppAction.SelectPerson(person.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(listOfNotNull(role, person.name.display).joinToString(": "))
                }
            }
        }
    }
}

private const val MAX_LAYOUT_DEPTH: Int = 20
private const val EMPTY_STATE_ICON_SIZE: Int = 48
private const val SIDEBAR_ANIMATION_MS: Int = 220
