package me.terevo.export

import me.terevo.domain.model.*
import me.terevo.layout.*

data class ExportTree(
    val layout: Layout,
    val labels: Map<NodeId, ExportPersonLabel>,
)

object ExportTreeMapper {
    private val engine = CachedLayoutEngine()
    private val cardSize = Size(width = 240.0, height = 72.0)

    fun map(tree: FamilyTree, options: LayoutOptions = LayoutOptions()): ExportTree {
        // See TreeCanvasMapper.map: sortKey must be a per-person key stable under other people's
        // edits, not an alphabetical name key. tree.persons preserves load/insertion order.
        val nodes = tree.persons.values.mapIndexed { index, person ->
            LayoutNode(person.id.toNodeId(), index.toString().padStart(10, '0'))
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
                    dissolved = relation.status == MarriageStatus.DIVORCED,
                )
            }
        }
        val graph = TreeGraph.of(nodes, edges)
        val metrics = NodeMetrics(nodes.associate { it.id to cardSize }, cardSize)
        val layout = engine.layout(LayoutRequest(graph, metrics, options))
        val labels = tree.persons.values.associate { person -> person.id.toNodeId() to person.toLabel() }
        return ExportTree(layout, labels)
    }
}

private fun PersonId.toNodeId(): NodeId = NodeId(value.toString())

private fun Person.toLabel(): ExportPersonLabel = ExportPersonLabel(
    name = name.display,
    lifeYears = lifeSpan.exportDates(),
    gender = when (gender) {
        Gender.MALE -> ExportGender.MALE
        Gender.FEMALE -> ExportGender.FEMALE
        Gender.UNKNOWN -> ExportGender.UNKNOWN
    },
)

private fun LifeSpan.exportDates(): String {
    val birthYear = birth.interval?.from?.year
    val deathYear = death.interval?.from?.year
    return when {
        birthYear == null && deathYear == null -> ""
        deathYear == null -> "р. $birthYear"
        birthYear == null -> "ум. $deathYear"
        else -> "$birthYear – $deathYear"
    }
}
