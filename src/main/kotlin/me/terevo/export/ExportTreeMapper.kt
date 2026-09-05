package me.terevo.export

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.LifeSpan
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.MarriageStatus
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.domain.model.interval
import me.terevo.layout.CachedLayoutEngine
import me.terevo.layout.Layout
import me.terevo.layout.LayoutEdge
import me.terevo.layout.LayoutNode
import me.terevo.layout.LayoutOptions
import me.terevo.layout.LayoutRequest
import me.terevo.layout.NodeId
import me.terevo.layout.NodeMetrics
import me.terevo.layout.Size
import me.terevo.layout.TreeGraph

data class ExportTree(
    val layout: Layout,
    val labels: Map<NodeId, ExportPersonLabel>,
)

object ExportTreeMapper {
    private val engine = CachedLayoutEngine()
    private val cardSize = Size(width = 240.0, height = 72.0)

    fun map(tree: FamilyTree, options: LayoutOptions = LayoutOptions()): ExportTree {
        val nodes = tree.persons.values.map { person -> LayoutNode(person.id.toNodeId(), person.name.sortKey) }
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
