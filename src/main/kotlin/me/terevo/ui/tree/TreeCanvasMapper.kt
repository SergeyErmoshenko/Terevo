package me.terevo.ui.tree

import me.terevo.domain.model.*
import me.terevo.domain.port.MediaRepository
import me.terevo.layout.*
import me.terevo.ui.person.cardDates
import me.terevo.ui.person.mainPhotoPath

object TreeCanvasMapper {
    private val engine = DirectionalLayoutEngine(CachedLayoutEngine())
    private val minCardSize = Size(width = 240.0, height = cardHeightFor(nameLineCount = MAX_NAME_LINES))

    fun map(
        tree: FamilyTree,
        options: LayoutOptions = LayoutOptions(),
        mediaRepository: MediaRepository = MediaRepository.NONE,
        pinnedPositions: Map<NodeId, Point> = emptyMap(),
    ): TreeCanvasState {
        val nodes = tree.persons.values.map { person ->
            LayoutNode(person.id.toNodeId(), person.name.sortKey)
        }
        val edges = tree.relations.values.map { relation ->
            when (relation) {
                is ParentChild -> LayoutEdge.Parentage(
                    parent = relation.parent.toNodeId(),
                    child = relation.child.toNodeId(),
                    biological = relation.isBiological,
                )

                is Marriage -> LayoutEdge.Union(
                    first = relation.spouseA.toNodeId(),
                    second = relation.spouseB.toNodeId(),
                    dissolved = relation.status == me.terevo.domain.model.MarriageStatus.DIVORCED,
                )
            }
        }
        val graph = TreeGraph.of(nodes, edges)
        val sizes = tree.persons.values.associate { person ->
            person.id.toNodeId() to cardSizeFor(person, mediaRepository)
        }
        val metrics = NodeMetrics(sizes, minCardSize)
        val layout = engine.layout(LayoutRequest(graph, metrics, options)).withPinned(pinnedPositions, graph)
        val visuals = TreeVisuals(
            tree.persons.values.associate { person -> person.id.toNodeId() to person.toVisual(mediaRepository) },
        )
        return TreeCanvasState(
            layout = layout,
            spatialIndex = SpatialIndex.build(layout),
            visuals = visuals,
            tree = tree,
        )
    }

    private fun cardSizeFor(person: Person, mediaRepository: MediaRepository): Size {
        val hasThumbnail = person.mainPhotoPath(mediaRepository) != null
        val textLeft = if (hasThumbnail) TEXT_LEFT_WITH_THUMBNAIL else TEXT_LEFT_BASE
        val nameLines = person.name.nameLines()
        val longestNameWidth = (nameLines.maxOfOrNull { it.length } ?: 0) * CHAR_WIDTH_ESTIMATE
        val yearsWidth = person.lifeSpan.cardDates().length * YEARS_CHAR_WIDTH_ESTIMATE
        val neededWidth = textLeft + maxOf(longestNameWidth, yearsWidth) + TEXT_RIGHT_MARGIN
        val neededHeight = cardHeightFor(nameLines.size)
        return Size(
            width = neededWidth.coerceAtLeast(minCardSize.width),
            height = neededHeight.coerceAtLeast(minCardSize.height),
        )
    }
}

private fun me.terevo.domain.model.PersonId.toNodeId(): NodeId = NodeId(value.toString())

internal fun me.terevo.domain.model.PersonName.nameLines(): List<String> {
    val surnameLine = if (maidenName.isNotEmpty()) "$surname ($maidenName)" else surname
    return listOf(surnameLine, givenName, patronymic).filter { it.isNotEmpty() }
}

private fun cardHeightFor(nameLineCount: Int): Double =
    CONTENT_TOP + nameLineCount * NAME_LINE_HEIGHT_ESTIMATE + YEARS_LINE_HEIGHT_ESTIMATE + CONTENT_BOTTOM_PADDING

private fun Person.toVisual(mediaRepository: MediaRepository): PersonVisual = PersonVisual(
    id = id.toNodeId(),
    nameLines = name.nameLines(),
    lifeYears = lifeSpan.cardDates(),
    gender = when (gender) {
        Gender.MALE -> PersonVisualGender.MALE
        Gender.FEMALE -> PersonVisualGender.FEMALE
        Gender.UNKNOWN -> PersonVisualGender.UNKNOWN
    },
    version = hashCode().toLong(),
    thumbnailPath = mainPhotoPath(mediaRepository),
)

private const val TEXT_LEFT_BASE: Double = 14.0
private const val TEXT_LEFT_WITH_THUMBNAIL: Double = 62.0
private const val TEXT_RIGHT_MARGIN: Double = 10.0
private const val CHAR_WIDTH_ESTIMATE: Double = 13.0
private const val YEARS_CHAR_WIDTH_ESTIMATE: Double = 10.0
private const val MAX_NAME_LINES: Int = 3
private const val CONTENT_TOP: Double = 8.0
private const val NAME_LINE_HEIGHT_ESTIMATE: Double = 26.0
private const val YEARS_LINE_HEIGHT_ESTIMATE: Double = 20.0
private const val CONTENT_BOTTOM_PADDING: Double = 8.0
