package me.terevo.ui.tree

import me.terevo.domain.model.*
import me.terevo.domain.port.MediaRepository
import me.terevo.layout.*
import me.terevo.ui.person.cardDates
import me.terevo.ui.person.mainPhotoPath

object TreeCanvasMapper {
    private val engine = DirectionalLayoutEngine(CachedLayoutEngine())
    private val minCardSize = Size(width = 240.0, height = 96.0)

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
        val longestPartLength = person.name.nameLines().maxOfOrNull { it.length } ?: 0
        val neededWidth = textLeft + longestPartLength * CHAR_WIDTH_ESTIMATE + TEXT_RIGHT_MARGIN
        return Size(width = neededWidth.coerceAtLeast(minCardSize.width), height = minCardSize.height)
    }
}

private fun me.terevo.domain.model.PersonId.toNodeId(): NodeId = NodeId(value.toString())

internal fun me.terevo.domain.model.PersonName.nameLines(): List<String> =
    listOf(surname, givenName, patronymic).filter { it.isNotEmpty() }

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
private const val CHAR_WIDTH_ESTIMATE: Double = 9.0
