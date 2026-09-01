package me.terevo.ui.tree

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.layout.CachedLayoutEngine
import me.terevo.layout.LayoutEdge
import me.terevo.layout.LayoutNode
import me.terevo.layout.LayoutRequest
import me.terevo.layout.NodeId
import me.terevo.layout.NodeMetrics
import me.terevo.layout.Size
import me.terevo.layout.TreeGraph
import me.terevo.ui.person.cardDates

object TreeCanvasMapper {
    private val engine = CachedLayoutEngine()
    private val cardSize = Size(width = 200.0, height = 72.0)

    fun map(tree: FamilyTree): TreeCanvasState {
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
        val metrics = NodeMetrics(nodes.associate { it.id to cardSize }, cardSize)
        val layout = engine.layout(LayoutRequest(graph, metrics))
        val visuals = TreeVisuals(tree.persons.values.associate { person -> person.id.toNodeId() to person.toVisual() })
        return TreeCanvasState(
            layout = layout,
            spatialIndex = SpatialIndex.build(layout),
            visuals = visuals,
        )
    }
}

private fun me.terevo.domain.model.PersonId.toNodeId(): NodeId = NodeId(value.toString())

private fun Person.toVisual(): PersonVisual = PersonVisual(
    id = id.toNodeId(),
    name = name.display,
    lifeYears = lifeSpan.cardDates(),
    gender = when (gender) {
        Gender.MALE -> PersonVisualGender.MALE
        Gender.FEMALE -> PersonVisualGender.FEMALE
        Gender.UNKNOWN -> PersonVisualGender.UNKNOWN
    },
    version = hashCode().toLong(),
)
