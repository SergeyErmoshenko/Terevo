package me.terevo.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import me.terevo.domain.command.Batch
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.model.Person
import me.terevo.layout.LayoutMode
import me.terevo.statistics.Statistics
import me.terevo.ui.kinship.KinshipDialog
import me.terevo.ui.kinship.KinshipDialogState
import me.terevo.ui.person.PersonFormDialog
import me.terevo.ui.person.PersonFormState
import me.terevo.ui.person.PersonSearchFilter
import me.terevo.ui.person.RelatedPerson
import me.terevo.ui.person.RelationDialog
import me.terevo.ui.person.RelationDialogState
import me.terevo.ui.person.displayText
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.statistics.StatisticsScreen
import me.terevo.ui.tree.TreeCanvas
import me.terevo.ui.tree.TreeCanvasIntent
import me.terevo.ui.tree.TreeCanvasState
import org.jetbrains.skia.Image as SkiaImage

data class GedcomImportState(
    val people: Int,
    val families: Int,
    val skippedTags: Set<String>,
    val command: Batch,
)

data class MediaViewerState(
    val media: Media,
    val content: ByteArray,
    val zoom: Float = 1f,
)

data class AppState(
    val isProjectOpen: Boolean = false,
    val projectName: String = "",
    val personCount: Int = 0,
    val canvas: TreeCanvasState = TreeCanvasState(),
    val layoutMode: LayoutMode = LayoutMode.WHOLE_FAMILY,
    val layoutDepth: Int = Int.MAX_VALUE,
    val searchFilter: PersonSearchFilter = PersonSearchFilter(),
    val searchResults: List<Person> = emptyList(),
    val selectedPerson: Person? = null,
    val selectedParents: List<Person> = emptyList(),
    val selectedChildren: List<Person> = emptyList(),
    val selectedSpouses: List<Person> = emptyList(),
    val selectedMedia: List<Media> = emptyList(),
    val relatedPeople: List<RelatedPerson> = emptyList(),
    val selectionBackHistory: List<me.terevo.domain.model.PersonId> = emptyList(),
    val selectionForwardHistory: List<me.terevo.domain.model.PersonId> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoLabel: String = Strings.UNDO,
    val redoLabel: String = Strings.REDO,
    val personForm: PersonFormState? = null,
    val relationDialog: RelationDialogState? = null,
    val pendingRelation: RelationDialogState? = null,
    val kinshipDialog: KinshipDialogState? = null,
    val statistics: Statistics? = null,
    val gedcomPreview: GedcomImportState? = null,
    val mediaViewer: MediaViewerState? = null,
    val status: String = Strings.NO_PROJECT,
)

sealed interface AppAction {
    data object NewProject : AppAction
    data object OpenProject : AppAction
    data object ImportGedcom : AppAction
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
    data class ChangeSearchFilter(val filter: PersonSearchFilter) : AppAction
    data object AddPerson : AppAction
    data object EditPerson : AppAction
    data object DeletePerson : AppAction
    data object AddParent : AppAction
    data object AddChild : AppAction
    data object AddSpouse : AppAction
    data object ChooseMedia : AppAction
    data class OpenMedia(val id: MediaId) : AppAction
    data class RemoveMedia(val id: MediaId) : AppAction
    data class ChangeMediaZoom(val zoom: Float) : AppAction
    data object CloseMedia : AppAction
    data class SelectPerson(val id: me.terevo.domain.model.PersonId) : AppAction
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
    data object OpenKinshipDialog : AppAction
    data class UpdateKinshipDialog(val dialog: KinshipDialogState) : AppAction
    data object CloseKinshipDialog : AppAction
    data object OpenStatistics : AppAction
    data object CloseStatistics : AppAction
    data object ExportPng : AppAction
    data object ExportPdf : AppAction
}

data class EmptyProjectAction(
    val label: String,
    val action: AppAction,
)

val emptyProjectActions: List<EmptyProjectAction> = listOf(
    EmptyProjectAction(Strings.NEW_PROJECT, AppAction.NewProject),
    EmptyProjectAction(Strings.OPEN_PROJECT, AppAction.OpenProject),
)

@Composable
fun App(
    state: AppState,
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(modifier.fillMaxSize()) {
        val statistics = state.statistics
        if (statistics != null) {
            StatisticsScreen(
                statistics = statistics,
                onClose = { onAction(AppAction.CloseStatistics) },
                modifier = Modifier.weight(1f),
            )
        } else {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Box(
                    Modifier
                        .width(spacing.sidebarWidth)
                        .fillMaxSize()
                        .background(colors.sidebar)
                        .padding(spacing.medium),
                ) {
                    if (state.isProjectOpen) {
                        ProjectSidebar(state, onAction)
                    }
                }
                VerticalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .background(colors.canvas),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.medium),
                    ) {
                        if (!state.isProjectOpen) {
                            Text(Strings.EMPTY_PROJECT)
                            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                                Button(onClick = { onAction(emptyProjectActions[0].action) }) {
                                    Text(emptyProjectActions[0].label)
                                }
                                OutlinedButton(onClick = { onAction(emptyProjectActions[1].action) }) {
                                    Text(emptyProjectActions[1].label)
                                }
                            }
                        } else if (state.personCount == 0) {
                            Text(Strings.EMPTY_TREE)
                            Button(onClick = { onAction(AppAction.AddPerson) }) {
                                Text(Strings.ADD_PERSON)
                            }
                        }
                    }
                    if (state.isProjectOpen && state.personCount > 0) {
                        TreeCanvas(
                            state = state.canvas,
                            onIntent = { onAction(AppAction.Canvas(it)) },
                        )
                    }
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
    state.personForm?.let { form ->
        PersonFormDialog(
            state = form,
            onChange = { onAction(AppAction.UpdatePersonForm(it)) },
            onSave = { onAction(AppAction.SavePerson) },
            onCancel = { onAction(AppAction.CancelPerson) },
            onConfirmDiscard = { onAction(AppAction.ConfirmDiscardPerson) },
            onKeepEditing = { onAction(AppAction.KeepEditingPerson) },
        )
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
        val image = remember(viewer.content) {
            if (viewer.media.mimeType.startsWith("image/")) {
                runCatching { SkiaImage.makeFromEncoded(viewer.content).toComposeImageBitmap() }.getOrNull()
            } else {
                null
            }
        }
        AlertDialog(
            onDismissRequest = { onAction(AppAction.CloseMedia) },
            title = { Text(viewer.media.fileName) },
            text = {
                if (image == null) {
                    Text(viewer.media.mimeType)
                } else {
                    Image(
                        bitmap = image,
                        contentDescription = viewer.media.fileName,
                        modifier = Modifier.size((500 * viewer.zoom).dp),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    OutlinedButton(
                        onClick = { onAction(AppAction.ChangeMediaZoom(viewer.zoom / 1.25f)) },
                    ) { Text(Strings.ZOOM_OUT) }
                    OutlinedButton(
                        onClick = { onAction(AppAction.ChangeMediaZoom(viewer.zoom * 1.25f)) },
                    ) { Text(Strings.ZOOM_IN) }
                    Button(onClick = { onAction(AppAction.CloseMedia) }) { Text(Strings.CLOSE) }
                }
            },
        )
    }
}

@Composable
private fun ProjectSidebar(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text("${Strings.PROJECT}: ${state.projectName}")
        Text("${Strings.PEOPLE}: ${state.personCount}")
        Button(onClick = { onAction(AppAction.AddPerson) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_PERSON)
        }
        OutlinedTextField(
            value = state.searchFilter.query,
            onValueChange = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(query = it))) },
            label = { Text(Strings.SEARCH) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.searchFilter.place,
            onValueChange = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(place = it))) },
            label = { Text(Strings.SEARCH_PLACE) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedTextField(
                value = state.searchFilter.birthYearFrom?.toString().orEmpty(),
                onValueChange = {
                    onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(birthYearFrom = it.toIntOrNull())))
                },
                label = { Text(Strings.SEARCH_BIRTH_FROM) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.searchFilter.birthYearTo?.toString().orEmpty(),
                onValueChange = {
                    onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(birthYearTo = it.toIntOrNull())))
                },
                label = { Text(Strings.SEARCH_BIRTH_TO) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedTextField(
                value = state.searchFilter.deathYearFrom?.toString().orEmpty(),
                onValueChange = {
                    onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(deathYearFrom = it.toIntOrNull())))
                },
                label = { Text(Strings.SEARCH_DEATH_FROM) },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.searchFilter.deathYearTo?.toString().orEmpty(),
                onValueChange = {
                    onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(deathYearTo = it.toIntOrNull())))
                },
                label = { Text(Strings.SEARCH_DEATH_TO) },
                modifier = Modifier.weight(1f),
            )
        }
        Text(Strings.FILTER_GENDER)
        listOf<Gender?>(null).plus(Gender.entries).forEach { gender ->
            OutlinedButton(
                onClick = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(gender = gender))) },
                enabled = state.searchFilter.gender != gender,
            ) { Text(gender?.searchLabel ?: Strings.FILTER_ANY) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            listOf(null, true, false).forEach { hasParents ->
                OutlinedButton(
                    onClick = {
                        onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(hasParents = hasParents)))
                    },
                    enabled = state.searchFilter.hasParents != hasParents,
                ) {
                    Text(
                        when (hasParents) {
                            true -> Strings.FILTER_HAS_PARENTS
                            false -> Strings.FILTER_NO_PARENTS
                            null -> Strings.FILTER_ANY
                        },
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            listOf(null, true, false).forEach { hasDates ->
                OutlinedButton(
                    onClick = { onAction(AppAction.ChangeSearchFilter(state.searchFilter.copy(hasDates = hasDates))) },
                    enabled = state.searchFilter.hasDates != hasDates,
                ) {
                    Text(
                        when (hasDates) {
                            true -> Strings.FILTER_HAS_DATES
                            false -> Strings.FILTER_NO_DATES
                            null -> Strings.FILTER_ANY
                        },
                    )
                }
            }
        }
        if (state.searchResults.isNotEmpty()) {
            Text("${Strings.SEARCH_RESULTS}: ${state.searchResults.size}")
            state.searchResults.forEach { result ->
                OutlinedButton(
                    onClick = { onAction(AppAction.SelectPerson(result.id)) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(result.name.display) }
            }
        }
        Text(Strings.TREE_MODE)
        LayoutMode.entries.forEach { mode ->
            OutlinedButton(
                onClick = { onAction(AppAction.ChangeLayoutMode(mode)) },
                enabled = state.layoutMode != mode,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(mode.label) }
        }
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
            OutlinedButton(
                onClick = { onAction(AppAction.ChangeLayoutDepth(Int.MAX_VALUE)) },
                enabled = state.layoutDepth != Int.MAX_VALUE,
            ) { Text(Strings.TREE_UNLIMITED) }
        }
        val person = state.selectedPerson ?: return@Column
        HorizontalDivider()
        Text(person.name.display)
        Text(person.lifeSpan.displayText())
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedButton(onClick = { onAction(AppAction.EditPerson) }) { Text(Strings.EDIT_PERSON) }
            OutlinedButton(onClick = { onAction(AppAction.DeletePerson) }) { Text(Strings.DELETE_PERSON) }
        }
        Button(onClick = { onAction(AppAction.AddParent) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_PARENT)
        }
        Button(onClick = { onAction(AppAction.AddChild) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_CHILD)
        }
        Button(onClick = { onAction(AppAction.AddSpouse) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_SPOUSE)
        }
        OutlinedButton(onClick = { onAction(AppAction.OpenKinshipDialog) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.DETERMINE_KINSHIP)
        }
        HorizontalDivider()
        Text(Strings.MEDIA)
        OutlinedButton(onClick = { onAction(AppAction.ChooseMedia) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_MEDIA)
        }
        state.selectedMedia.forEach { media ->
            Text(media.fileName)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                OutlinedButton(onClick = { onAction(AppAction.OpenMedia(media.id)) }) {
                    Text(Strings.OPEN_MEDIA)
                }
                OutlinedButton(onClick = { onAction(AppAction.RemoveMedia(media.id)) }) {
                    Text(Strings.REMOVE_MEDIA)
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
    HorizontalDivider()
    Text(title)
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

private const val MAX_LAYOUT_DEPTH: Int = 20
