package me.terevo.server

import kotlinx.serialization.Serializable
import me.terevo.domain.model.DatePrecision
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.Gender
import me.terevo.domain.model.MarriageStatus
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.Person
import me.terevo.domain.port.ThemeMode
import me.terevo.layout.EdgeStyle
import me.terevo.layout.Layout
import me.terevo.layout.LayoutDirection
import me.terevo.layout.LayoutEdge
import me.terevo.layout.LayoutMode
import me.terevo.ui.AppState
import me.terevo.ui.LayoutDensity
import me.terevo.ui.MainTab
import me.terevo.ui.events.EventFormState
import me.terevo.ui.events.EventRow
import me.terevo.ui.kinship.KinshipDialogState
import me.terevo.ui.person.CustomFieldInput
import me.terevo.ui.person.EventDateInput
import me.terevo.ui.person.EventDateMode
import me.terevo.ui.person.PersonFormState
import me.terevo.ui.person.PersonSearchFilter
import me.terevo.ui.person.RelationDialogState
import me.terevo.ui.person.RelationMode
import me.terevo.ui.person.SpouseInfo
import me.terevo.ui.person.cardDates
import me.terevo.ui.person.displayText
import me.terevo.ui.person.label
import me.terevo.ui.persons.PersonRow
import me.terevo.ui.tree.TreeCanvasState
import me.terevo.ui.tree.TreeVisuals

@Serializable
data class PersonSummaryDto(
    val id: String,
    val name: String,
    val gender: Gender,
    val lifeYears: String,
)

@Serializable
data class PersonDetailsDto(
    val id: String,
    val name: String,
    val gender: Gender,
    val lifeSpan: String,
    val isAlive: Boolean,
    val maidenName: String,
    val birthPlace: String?,
    val deathPlace: String?,
    val residence: String?,
    val occupation: String,
    val notes: String,
    val customFields: List<CustomFieldDto>,
    val photoPath: String?,
)

@Serializable
data class CustomFieldDto(val key: String = "", val value: String = "")

@Serializable
data class SpouseDto(val person: PersonSummaryDto, val details: String)

@Serializable
data class RelatedPersonDto(val person: PersonSummaryDto, val role: String)

@Serializable
data class MediaDto(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val thumbnailPath: String?,
)

@Serializable
data class SearchFilterDto(
    val query: String = "",
    val gender: Gender? = null,
    val birthYearFrom: Int? = null,
    val birthYearTo: Int? = null,
    val deathYearFrom: Int? = null,
    val deathYearTo: Int? = null,
    val place: String = "",
    val hasParents: Boolean? = null,
    val hasDates: Boolean? = null,
)

@Serializable
data class EventDateInputDto(
    val mode: EventDateMode = EventDateMode.UNKNOWN,
    val value: String = "",
    val end: String = "",
    val precision: DatePrecision = DatePrecision.YEAR,
)

// The editable part of the person form; the frontend sends exactly this back.
@Serializable
data class PersonFormFieldsDto(
    val surname: String = "",
    val givenName: String = "",
    val patronymic: String = "",
    val maidenName: String = "",
    val gender: Gender = Gender.UNKNOWN,
    val birth: EventDateInputDto = EventDateInputDto(),
    val isAlive: Boolean = true,
    val death: EventDateInputDto = EventDateInputDto(),
    val birthPlace: String = "",
    val deathPlace: String = "",
    val residence: String = "",
    val occupation: String = "",
    val notes: String = "",
    val customFields: List<CustomFieldDto> = emptyList(),
)

@Serializable
data class PersonFormDto(
    val isNew: Boolean,
    val fields: PersonFormFieldsDto,
    val requiredGender: Gender?,
    val customFieldSuggestions: List<String>,
    val pendingMediaPaths: List<String>,
    val blockingError: String?,
    val warnings: List<String>,
    val isDiscardConfirmationVisible: Boolean,
    val canSave: Boolean,
)

@Serializable
data class RelationFieldsDto(
    val query: String = "",
    val selected: String? = null,
    val parentKind: ParentKind = ParentKind.BIOLOGICAL,
    val marriageStatus: MarriageStatus = MarriageStatus.MARRIED,
    val marriageSince: EventDateInputDto = EventDateInputDto(),
    val marriagePlace: String = "",
    val secondParent: String? = null,
    val secondParentQuery: String = "",
)

@Serializable
data class RelationDialogDto(
    val mode: RelationMode,
    val source: PersonSummaryDto,
    val fields: RelationFieldsDto,
    val people: List<PersonSummaryDto>,
    val secondParentCandidates: List<PersonSummaryDto>,
    val error: String?,
)

@Serializable
data class KinshipDialogDto(
    val source: PersonSummaryDto,
    val query: String,
    val target: String?,
    val term: String?,
    val people: List<PersonSummaryDto>,
)

@Serializable
data class ParticipantDto(val personId: String? = null, val query: String = "", val role: String = "")

@Serializable
data class EventFormFieldsDto(
    val type: String = "",
    val date: EventDateInputDto = EventDateInputDto(),
    val place: String = "",
    val notes: String = "",
    val participants: List<ParticipantDto> = emptyList(),
)

@Serializable
data class EventFormDto(
    val isNew: Boolean,
    val fields: EventFormFieldsDto,
    val people: List<PersonSummaryDto>,
    val blockingError: String?,
    val canSave: Boolean,
)

@Serializable
data class DragRelationMenuDto(
    val source: String,
    val target: String,
    val x: Double,
    val y: Double,
    val validity: Map<RelationMode, Boolean>,
    val sourceGender: Gender,
)

@Serializable
data class GedcomPreviewDto(val people: Int, val families: Int, val skippedTags: List<String>, val error: String?)

@Serializable
data class MediaViewerDto(val media: MediaDto, val imagePath: String?, val pageCount: Int, val page: Int, val zoom: Float)

@Serializable
data class NamedCountDto(val name: String, val count: Int)

@Serializable
data class StatisticsDto(
    val totalPersons: Int,
    val livingCount: Int,
    val deceasedCount: Int,
    val averageLifespanYears: Double?,
    val lifespanSampleSize: Int,
    val generationDistribution: Map<Int, Int>,
    val topSurnames: List<NamedCountDto>,
    val topPlaces: List<NamedCountDto>,
    val missingBirthDateCount: Int,
    val missingParentsCount: Int,
)

@Serializable
data class PersonRowDto(
    val id: String,
    val thumbnailPath: String?,
    val fullName: String,
    val gender: Gender,
    val birthDate: String,
    val birthDateSortKey: String?,
    val residence: String,
    val age: String,
    val ageSortKey: Int?,
    val occupation: String,
    val comment: String,
    val alive: Boolean,
)

@Serializable
data class EventRowDto(
    val id: String?,
    val type: String,
    val participants: String,
    val date: String,
    val dateSortKey: String?,
    val place: String,
    val daysUntilAnniversary: Int?,
    val yearsPassed: Int?,
    val thumbnailPath: String?,
)

@Serializable
data class NodeDto(val id: String, val x: Double, val y: Double, val width: Double, val height: Double)

@Serializable
data class EdgeDto(
    val from: String,
    val to: String,
    val union: Boolean,
    val style: EdgeStyle,
    // Flat x, y pairs of the connector polyline.
    val points: List<Double>,
)

@Serializable
data class VisualDto(
    val nameLines: List<String>,
    val lifeYears: String,
    val gender: Gender,
    val thumbnailPath: String?,
)

@Serializable
data class LayoutDto(
    val nodes: List<NodeDto>,
    val edges: List<EdgeDto>,
    val generations: Map<String, Int>,
    val visuals: Map<String, VisualDto>,
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
)

@Serializable
data class CanvasDto(
    val layoutVersion: Long,
    // Present only when the layout changed since the previous message.
    val layout: LayoutDto?,
    val selected: String?,
    val roles: Map<String, String>,
    val searchResults: List<String>,
    val homePersonId: String?,
    val centerOn: String?,
    val centerRequest: Long,
)

@Serializable
data class AppStateDto(
    val isProjectOpen: Boolean,
    val projectName: String,
    val projectDirectory: String?,
    val personCount: Int,
    val canvas: CanvasDto,
    val layoutMode: LayoutMode,
    val layoutDepth: Int?,
    val layoutDirection: LayoutDirection,
    val layoutDensity: LayoutDensity,
    val searchFilter: SearchFilterDto,
    val searchResults: List<PersonSummaryDto>,
    val selectedPerson: PersonDetailsDto?,
    val selectedParents: List<PersonSummaryDto>,
    val selectedChildren: List<PersonSummaryDto>,
    val selectedSpouses: List<SpouseDto>,
    val selectedMedia: List<MediaDto>,
    val relatedPeople: List<RelatedPersonDto>,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoLabel: String,
    val redoLabel: String,
    val personForm: PersonFormDto?,
    val relationDialog: RelationDialogDto?,
    val dragRelationMenu: DragRelationMenuDto?,
    val kinshipDialog: KinshipDialogDto?,
    val statistics: StatisticsDto?,
    val gedcomPreview: GedcomPreviewDto?,
    val mediaViewer: MediaViewerDto?,
    val status: String,
    val themeMode: ThemeMode,
    val mainTab: MainTab,
    val sidebarCollapsed: Boolean,
    val personRows: List<PersonRowDto>,
    val eventRows: List<EventRowDto>,
    val eventForm: EventFormDto?,
    val personViewOpen: Boolean,
    val dragSource: String?,
    val dragTargets: List<String>,
)

fun Person.toSummary(): PersonSummaryDto =
    PersonSummaryDto(id.value.toString(), name.display, gender, lifeSpan.cardDates())

fun Person.toDetails(photoPath: String?): PersonDetailsDto = PersonDetailsDto(
    id = id.value.toString(),
    name = name.display,
    gender = gender,
    lifeSpan = lifeSpan.displayText(),
    isAlive = isAlive,
    maidenName = name.maidenName,
    birthPlace = birthPlace?.title,
    deathPlace = deathPlace?.title,
    residence = residence?.title,
    occupation = occupation,
    notes = notes,
    customFields = customFields.entries.sortedBy { it.key }.map { CustomFieldDto(it.key, it.value) },
    photoPath = photoPath,
)

fun EventDateInput.toDto() = EventDateInputDto(mode, value, end, precision)

fun EventDateInputDto.toInput() = EventDateInput(mode, value, end, precision)

fun PersonSearchFilter.toDto() = SearchFilterDto(
    query, gender, birthYearFrom, birthYearTo, deathYearFrom, deathYearTo, place, hasParents, hasDates,
)

fun SearchFilterDto.toFilter() = PersonSearchFilter(
    query, gender, birthYearFrom, birthYearTo, deathYearFrom, deathYearTo, place, hasParents, hasDates,
)

fun PersonFormState.toDto() = PersonFormDto(
    isNew = original == null,
    fields = PersonFormFieldsDto(
        surname, givenName, patronymic, maidenName, gender, birth.toDto(), isAlive, death.toDto(), birthPlace,
        deathPlace, residence, occupation, notes, customFields.map { CustomFieldDto(it.key, it.value) },
    ),
    requiredGender = requiredGender,
    customFieldSuggestions = customFieldSuggestions,
    pendingMediaPaths = pendingMediaPaths,
    blockingError = blockingError,
    warnings = warnings,
    isDiscardConfirmationVisible = isDiscardConfirmationVisible,
    canSave = canSave,
)

fun PersonFormState.withFields(fields: PersonFormFieldsDto) = copy(
    surname = fields.surname,
    givenName = fields.givenName,
    patronymic = fields.patronymic,
    maidenName = fields.maidenName,
    gender = fields.gender,
    birth = fields.birth.toInput(),
    isAlive = fields.isAlive,
    death = fields.death.toInput(),
    birthPlace = fields.birthPlace,
    deathPlace = fields.deathPlace,
    residence = fields.residence,
    occupation = fields.occupation,
    notes = fields.notes,
    customFields = fields.customFields.map { CustomFieldInput(it.key, it.value) },
)

fun RelationDialogState.toDto() = RelationDialogDto(
    mode = mode,
    source = source.toSummary(),
    fields = RelationFieldsDto(
        query, selected?.value?.toString(), parentKind, marriageStatus, marriageSince.toDto(), marriagePlace,
        secondParent?.value?.toString(), secondParentQuery,
    ),
    people = filteredPeople.map { it.toSummary() },
    secondParentCandidates = filteredSecondParentCandidates.map { it.toSummary() },
    error = error,
)

fun RelationDialogState.withFields(fields: RelationFieldsDto) = copy(
    query = fields.query,
    selected = fields.selected?.let(::personIdOf),
    parentKind = fields.parentKind,
    marriageStatus = fields.marriageStatus,
    marriageSince = fields.marriageSince.toInput(),
    marriagePlace = fields.marriagePlace,
    secondParent = fields.secondParent?.let(::personIdOf),
    secondParentQuery = fields.secondParentQuery,
    error = null,
)

fun KinshipDialogState.toDto() = KinshipDialogDto(
    source.toSummary(), query, target?.value?.toString(), term, filteredPeople.map { it.toSummary() },
)

fun EventFormState.toDto() = EventFormDto(
    isNew = original == null,
    fields = EventFormFieldsDto(
        type, date.toDto(), place, notes,
        participants.map { ParticipantDto(it.personId?.value?.toString(), it.query, it.role) },
    ),
    people = people.sortedBy { it.name.sortKey }.map { it.toSummary() },
    blockingError = blockingError,
    canSave = canSave,
)

fun EventFormState.withFields(fields: EventFormFieldsDto) = copy(
    type = fields.type,
    date = fields.date.toInput(),
    place = fields.place,
    notes = fields.notes,
    participants = fields.participants.map {
        me.terevo.ui.events.EventParticipantInput(it.personId?.let(::personIdOf), it.query, it.role)
    },
)

fun Media.toDto(thumbnailPath: String?) = MediaDto(id.value.toString(), fileName, mimeType, sizeBytes, thumbnailPath)

fun SpouseInfo.toDto() = SpouseDto(
    person.toSummary(),
    listOfNotNull(
        marriage.status.label(),
        marriage.since.displayText()?.let { "Дата брака: $it" },
        marriage.place?.title?.let { "Место брака: $it" },
    ).joinToString(" · "),
)

fun PersonRow.toDto() = PersonRowDto(
    id.value.toString(), thumbnailPath, fullName, gender, birthDate, birthDateSortKey?.toString(), residence, age,
    ageSortKey, occupation, comment, alive,
)

fun EventRow.toDto() = EventRowDto(
    id?.value?.toString(), type, participants, date.displayText().orEmpty(), date.sortKey(), place,
    daysUntilAnniversary, yearsPassed, thumbnailPath,
)

private fun EventDate.sortKey(): String? = when (this) {
    is EventDate.Exact -> date.toString()
    is EventDate.Approximate -> around.toString()
    is EventDate.Range -> from.toString()
    EventDate.Unknown -> null
}

fun Layout.toDto(visuals: TreeVisuals): LayoutDto = LayoutDto(
    nodes = nodes.map { (id, rect) -> NodeDto(id.value, rect.left, rect.top, rect.width, rect.height) },
    edges = edges.map { path ->
        val (from, to) = when (val edge = path.edge) {
            is LayoutEdge.Parentage -> edge.parent to edge.child
            is LayoutEdge.Union -> edge.first to edge.second
        }
        EdgeDto(from.value, to.value, path.edge is LayoutEdge.Union, path.style, path.segments.flatMap { listOf(it.x, it.y) })
    },
    generations = generations.mapKeys { it.key.value },
    visuals = visuals.persons.filterKeys { it in nodes }.map { (id, visual) ->
        id.value to VisualDto(
            visual.nameLines,
            visual.lifeYears,
            when (visual.gender) {
                me.terevo.ui.tree.PersonVisualGender.MALE -> Gender.MALE
                me.terevo.ui.tree.PersonVisualGender.FEMALE -> Gender.FEMALE
                me.terevo.ui.tree.PersonVisualGender.UNKNOWN -> Gender.UNKNOWN
            },
            visual.thumbnailPath,
        )
    }.toMap(),
    minX = bounds.left,
    minY = bounds.top,
    maxX = bounds.right,
    maxY = bounds.bottom,
)

fun TreeCanvasState.toDto(layoutVersion: Long, includeLayout: Boolean) = CanvasDto(
    layoutVersion = layoutVersion,
    layout = if (includeLayout) layout.toDto(visuals) else null,
    selected = highlight.selected?.value,
    roles = if (highlight.isActive) highlight.roles.mapKeys { it.key.value } else emptyMap(),
    searchResults = highlight.searchResults.map { it.value },
    homePersonId = homePersonId?.value,
    centerOn = centerOn?.value,
    centerRequest = centerRequest,
)

fun AppState.toDto(
    projectDirectory: String?,
    layoutVersion: Long,
    includeLayout: Boolean,
): AppStateDto = AppStateDto(
    isProjectOpen = isProjectOpen,
    projectName = projectName,
    projectDirectory = projectDirectory,
    personCount = personCount,
    canvas = canvas.toDto(layoutVersion, includeLayout),
    layoutMode = layoutMode,
    layoutDepth = layoutDepth.takeIf { it != Int.MAX_VALUE },
    layoutDirection = layoutDirection,
    layoutDensity = layoutDensity,
    searchFilter = searchFilter.toDto(),
    searchResults = searchResults.map { it.toSummary() },
    selectedPerson = selectedPerson?.toDetails(selectedPersonPhotoPath),
    selectedParents = selectedParents.map { it.toSummary() },
    selectedChildren = selectedChildren.map { it.toSummary() },
    selectedSpouses = selectedSpouseMarriages.map { it.toDto() },
    selectedMedia = selectedMedia.map { it.toDto(selectedMediaThumbnails[it.id]) },
    relatedPeople = relatedPeople.map { RelatedPersonDto(it.person.toSummary(), it.role) },
    canGoBack = selectionBackHistory.isNotEmpty(),
    canGoForward = selectionForwardHistory.isNotEmpty(),
    canUndo = canUndo,
    canRedo = canRedo,
    undoLabel = undoLabel,
    redoLabel = redoLabel,
    personForm = personForm?.toDto(),
    relationDialog = relationDialog?.toDto(),
    dragRelationMenu = dragRelationMenu?.let {
        DragRelationMenuDto(
            it.source.value.toString(), it.target.value.toString(), it.position.x, it.position.y, it.validity,
            it.sourceGender,
        )
    },
    kinshipDialog = kinshipDialog?.toDto(),
    statistics = statistics?.let { stats ->
        StatisticsDto(
            stats.totalPersons, stats.livingCount, stats.deceasedCount, stats.averageLifespanYears,
            stats.lifespanSampleSize, stats.generationDistribution,
            stats.topSurnames.map { NamedCountDto(it.name, it.count) },
            stats.topPlaces.map { NamedCountDto(it.name, it.count) },
            stats.missingBirthDateCount, stats.missingParentsCount,
        )
    },
    gedcomPreview = gedcomPreview?.let { GedcomPreviewDto(it.people, it.families, it.skippedTags.sorted(), it.error) },
    mediaViewer = mediaViewer?.let {
        MediaViewerDto(it.media.toDto(null), it.imagePath, it.pageCount, it.page, it.zoom)
    },
    status = status,
    themeMode = themeMode,
    mainTab = mainTab,
    sidebarCollapsed = sidebarCollapsed,
    personRows = personRows.map { it.toDto() },
    eventRows = eventRows.map { it.toDto() },
    eventForm = eventForm?.toDto(),
    personViewOpen = personViewOpen,
    dragSource = dragSource?.value?.toString(),
    dragTargets = dragTargets.map { it.value.toString() },
)

fun personIdOf(value: String) = me.terevo.domain.model.PersonId.parse(value)

fun mediaIdOf(value: String) = MediaId.parse(value)
