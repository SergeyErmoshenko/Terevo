package me.terevo.testing

import me.terevo.layout.EdgePath
import me.terevo.layout.Layout
import me.terevo.layout.LayoutOptions
import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.layout.TreeGraph
import me.terevo.layout.endpoints
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Readability checks for a rendered layout. Connectors are sampled curves, so every check works on
// arbitrary straight segments rather than assuming horizontal/vertical routing.

private data class Segment(val path: EdgePath, val from: Point, val to: Point)

private fun segmentsOf(edges: List<EdgePath>): List<Segment> =
    edges.flatMap { path -> path.segments.zipWithNext { a, b -> Segment(path, a, b) } }

private fun cross(o: Point, a: Point, b: Point): Double = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

private fun properlyIntersect(p: Point, q: Point, r: Point, s: Point): Boolean {
    val d1 = cross(r, s, p)
    val d2 = cross(r, s, q)
    val d3 = cross(p, q, r)
    val d4 = cross(p, q, s)
    return (d1 * d2 < -EPSILON) && (d3 * d4 < -EPSILON)
}

// Liang-Barsky clipping: whether any part of the segment lies strictly inside the rect.
private fun segmentEntersRect(from: Point, to: Point, rect: Rect): Boolean {
    var t0 = 0.0
    var t1 = 1.0
    val dx = to.x - from.x
    val dy = to.y - from.y
    val sides = listOf(-dx to from.x - rect.left, dx to rect.right - from.x, -dy to from.y - rect.top, dy to rect.bottom - from.y)
    for ((p, q) in sides) {
        if (p == 0.0) {
            if (q <= 0) return false
            continue
        }
        val t = q / p
        if (p < 0) {
            if (t > t1) return false
            if (t > t0) t0 = t
        } else {
            if (t < t0) return false
            if (t < t1) t1 = t
        }
    }
    return t0 < t1
}

private const val EPSILON = 1e-6

// A connector must not run through a card it does not belong to.
fun assertNoEdgeThroughCard(layout: Layout, tolerance: Double = 1.0) {
    val failures = mutableListOf<String>()
    for (segment in segmentsOf(layout.edges)) {
        val (from, to) = segment.path.edge.endpoints
        for ((id, rect) in layout.nodes) {
            if (id == from || id == to) continue
            val inner = Rect(rect.left + tolerance, rect.top + tolerance, rect.width - 2 * tolerance, rect.height - 2 * tolerance)
            if (segmentEntersRect(segment.from, segment.to, inner)) failures += "${segment.path.edge} passes through ${id.value}"
        }
    }
    assertTrue(
        failures.isEmpty(),
        "connectors run through unrelated cards:\n" + failures.distinct().joinToString("\n").prependIndent("  "),
    )
}

fun countEdgeCrossings(layout: Layout): Int = edgeCrossings(layout).size

// Proper crossings between connectors of different edges. Connectors that share a person meet at
// that person's card or family point by design, and two parentages drawn along the same family line
// share their geometry, so neither counts.
fun assertNoEdgeCrossings(layout: Layout) {
    val failures = edgeCrossings(layout)
    assertTrue(
        failures.isEmpty(),
        "connectors cross:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

private fun edgeCrossings(layout: Layout): List<String> {
    val paths = layout.edges.distinctBy { it.segments }
    val failures = mutableListOf<String>()
    for (i in paths.indices) {
        for (j in i + 1 until paths.size) {
            val a = paths[i]
            val b = paths[j]
            if (a.edge.endpoints.toList().any { it in b.edge.endpoints.toList() }) continue
            for ((p, q) in a.segments.zipWithNext()) {
                for ((r, s) in b.segments.zipWithNext()) {
                    if (properlyIntersect(p, q, r, s)) failures += "${a.edge} crosses ${b.edge} near $p"
                }
            }
        }
    }
    return failures
}

// Children of one couple must occupy a contiguous span of their row: no card from an unrelated
// family may sit between two of them. Interleaved siblings are what forces connectors to reach
// across the row.
//
// A sibling's own spouse counts as part of that sibling's slot - spouses are kept side by side by
// design, so a person who married in legitimately sits between two blood siblings and is not an
// intruder. Only a card belonging to neither the sibship nor any sibling's marriage counts.
fun assertSiblingsContiguous(layout: Layout, graph: TreeGraph) {
    val byGeneration = layout.nodes.keys.groupBy { layout.generations[it] }
    val failures = mutableListOf<String>()
    for ((_, ids) in byGeneration) {
        val row = ids.sortedBy { layout.nodes.getValue(it).left }
        val position = row.withIndex().associate { (index, id) -> id to index }
        val sibships = row.mapNotNull { id -> graph.parents(id).takeIf { it.isNotEmpty() }?.toSet() }.distinct()
        for (parents in sibships) {
            val members = row.filter { graph.parents(it).toSet() == parents }
            if (members.size < 2) continue
            val allowed = members.toSet() + members.flatMap(graph::partners)
            val indices = members.mapNotNull(position::get).sorted()
            val intruders = row.subList(indices.first(), indices.last() + 1)
                .filter { it !in allowed }
                .map { it.value }
            if (intruders.isNotEmpty()) {
                failures += "children of ${parents.map { it.value }} are split by $intruders"
            }
        }
    }
    assertTrue(
        failures.isEmpty(),
        "sibling blocks are not contiguous:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

fun assertNoCardOverlaps(layout: Layout) {
    val rects = layout.nodes.entries.toList()
    for (i in rects.indices) {
        for (j in i + 1 until rects.size) {
            assertTrue(
                !rects[i].value.intersects(rects[j].value),
                "${rects[i].key.value} overlaps ${rects[j].key.value}",
            )
        }
    }
}

// Adjacent cards in a row must keep at least the spouse gap between them.
//
// "No overlap" is a weak floor: it permits two cards sitting 1px apart, which reads as one blob and
// is what "everything piles up when you add a lot of people" looks like.
fun assertRowSpacing(
    layout: Layout,
    minimumGap: Double = LayoutOptions.DEFAULT_SPOUSE_SPACING,
    tolerance: Double = 0.5,
) {
    val failures = mutableListOf<String>()
    for ((_, ids) in layout.nodes.keys.groupBy { layout.generations[it] }) {
        val row = ids.map { it to layout.nodes.getValue(it) }.sortedBy { it.second.left }
        for ((a, b) in row.zipWithNext()) {
            val gap = b.second.left - a.second.right
            if (gap >= minimumGap - tolerance) continue
            failures += "${a.first.value} -> ${b.first.value} only ${gap}px apart (need $minimumGap)"
        }
    }
    assertTrue(
        failures.isEmpty(),
        "cards are packed tighter than the spacing floor:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

// Every married couple sits side by side in its row, with no card between the spouses.
fun assertCouplesAdjacent(layout: Layout, graph: TreeGraph) {
    val rows = layout.nodes.keys.groupBy { layout.generations[it] }
        .mapValues { (_, ids) -> ids.sortedBy { layout.nodes.getValue(it).left } }
    val failures = graph.unions.filter { union ->
        if (graph.partners(union.first).size > 1 || graph.partners(union.second).size > 1) return@filter false
        val row = rows[layout.generations[union.first]] ?: return@filter false
        val first = row.indexOf(union.first)
        val second = row.indexOf(union.second)
        first >= 0 && second >= 0 && kotlin.math.abs(first - second) > 1
    }
    assertTrue(failures.isEmpty(), "spouses separated by other cards: $failures")
}

// A node connected to exactly one node in an adjacent generation, with room to move, should sit
// directly above/below it so the connector is one straight vertical line. Only checked where the
// row genuinely has slack: a node wedged between neighbours legitimately cannot line up.
fun assertLoneRelativesAligned(layout: Layout, graph: TreeGraph, tolerance: Double = 2.0) {
    val failures = mutableListOf<String>()
    for ((id, rect) in layout.nodes) {
        val generation = layout.generations[id] ?: continue
        if (graph.partners(id).isNotEmpty()) continue

        val parents = graph.parents(id).filter { layout.generations[it] != generation }
        val children = graph.children(id).filter { layout.generations[it] != generation }
        val relatives = when {
            parents.isEmpty() && children.size == 1 -> children
            children.isEmpty() && parents.size == 1 -> parents
            else -> continue
        }
        val target = relatives.mapNotNull { layout.nodes[it]?.centerX }.singleOrNull() ?: continue
        val delta = target - rect.centerX
        if (kotlin.math.abs(delta) <= tolerance) continue

        val row = layout.nodes.entries
            .filter { layout.generations[it.key] == generation }
            .sortedBy { it.value.left }
        val index = row.indexOfFirst { it.key == id }
        val blocker = if (delta > 0) row.getOrNull(index + 1) else row.getOrNull(index - 1)
        val slack = when {
            blocker == null -> Double.POSITIVE_INFINITY
            delta > 0 -> blocker.value.left - rect.right
            else -> rect.left - blocker.value.right
        }
        if (slack <= tolerance) continue

        failures += "${id.value} is ${delta}px from its only relative " +
            "${relatives.single().value}, with ${slack}px of room to close it"
    }
    assertTrue(
        failures.isEmpty(),
        "nodes drift away from their only relative:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

fun assertReadable(layout: Layout, graph: TreeGraph) {
    assertNoCardOverlaps(layout)
    assertRowSpacing(layout)
    assertSiblingsContiguous(layout, graph)
    assertCouplesAdjacent(layout, graph)
    assertNoEdgeThroughCard(layout)
}

fun assertGenerationRows(layout: Layout, expected: Int) {
    assertEquals(expected, layout.generations.values.distinct().size)
}
