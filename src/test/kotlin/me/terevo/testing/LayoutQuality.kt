package me.terevo.testing

import me.terevo.layout.EdgePath
import me.terevo.layout.Layout
import me.terevo.layout.LayoutEdge
import me.terevo.layout.LayoutOptions
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.TreeGraph
import me.terevo.layout.endpoints
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Readability checks for a rendered layout.
//
// These exist because "no card overlaps" - the only structural property the layout suite used to
// assert - says nothing about the CONNECTORS, and connectors are what a user actually reads a family
// tree by. A layout can pass every card-overlap check while drawing two unrelated couples' bus lines
// along the same horizontal line, which renders as one thick merged stroke and is indistinguishable
// from "the lines are crossing". A strict segment-intersection test does not catch it either,
// because collinear segments never technically intersect. Measured on the real reported 45-person
// tree: 0 strict crossings, yet 6 genuine cross-family bus overlaps of up to 1107px.

private data class Segment(val edge: LayoutEdge, val from: Point, val to: Point) {
    val horizontal: Boolean get() = from.y == to.y
    val vertical: Boolean get() = from.x == to.x
    val loX: Double get() = minOf(from.x, to.x)
    val hiX: Double get() = maxOf(from.x, to.x)
    val loY: Double get() = minOf(from.y, to.y)
    val hiY: Double get() = maxOf(from.y, to.y)
}

private fun segmentsOf(edges: List<EdgePath>): List<Segment> =
    edges.flatMap { path -> path.segments.zipWithNext { a, b -> Segment(path.edge, a, b) } }

// Whether two connectors are parts of ONE bracket, and so are meant to share ink.
//
// EdgeRouter draws a couple's children as a single bracket: every parent's stem drops to one shared
// horizontal bar, which then drops into each child. Two connectors legitimately coincide only when
// they are strokes of that same bracket - i.e. they run between the same parent couple and the same
// child row.
//
// The earlier rule here exempted any two edges sharing even ONE parent, which was far too generous:
// two co-parents' connectors to two DIFFERENT children share a parent, so they were waved through
// while actually being distinct lines drawn on top of each other. That blind spot hid 45 stacked
// pairs - merging by up to 1210px - in the reported tree.
private fun sharesBracket(graph: TreeGraph, a: LayoutEdge, b: LayoutEdge): Boolean {
    if (a !is LayoutEdge.Parentage || b !is LayoutEdge.Parentage) return false
    // Two stems into the same child from that child's own parents: one bracket by construction.
    if (a.child == b.child) return true
    // Children of the exact same parent set hang off that couple's single shared bar. Note this
    // must NOT also require a.parent == b.parent: the two strokes of one bracket routinely start at
    // DIFFERENT co-parents of the same couple (mother->childA and father->childB), and treating
    // those as separate lines reports the intended bracket as 45 defects.
    return graph.parents(a.child).toSet() == graph.parents(b.child).toSet()
}

// Two horizontal runs that are not strokes of the same bracket must never share a Y and overlap in
// X: on screen they merge into one thicker line, which is indistinguishable from lines crossing.
fun assertNoMergedBusLines(layout: Layout, graph: TreeGraph, tolerance: Double = 1.0) {
    val horizontal = segmentsOf(layout.edges).filter { it.horizontal }
    val failures = mutableListOf<String>()
    for (i in horizontal.indices) {
        for (j in i + 1 until horizontal.size) {
            val a = horizontal[i]
            val b = horizontal[j]
            if (a.edge == b.edge || a.from.y != b.from.y) continue
            val shared = minOf(a.hiX, b.hiX) - maxOf(a.loX, b.loX)
            if (shared <= tolerance) continue
            if (sharesBracket(graph, a.edge, b.edge)) continue
            failures += "${shared}px at y=${a.from.y}: ${a.edge} || ${b.edge}"
        }
    }
    assertTrue(
        failures.isEmpty(),
        "connectors from unrelated families merge into one line:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

// A connector must not run through a card it does not belong to.
fun assertNoEdgeThroughCard(layout: Layout, tolerance: Double = 1.0) {
    val failures = mutableListOf<String>()
    for (segment in segmentsOf(layout.edges)) {
        val (from, to) = segment.edge.endpoints
        for ((id, rect) in layout.nodes) {
            if (id == from || id == to) continue
            val insideX = rect.left < segment.hiX - tolerance && rect.right > segment.loX + tolerance
            val insideY = rect.top < segment.hiY - tolerance && rect.bottom > segment.loY + tolerance
            if (insideX && insideY) failures += "${segment.edge} passes through ${id.value}"
        }
    }
    assertTrue(
        failures.isEmpty(),
        "connectors run through unrelated cards:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

// Strict perpendicular crossings between connectors of different edges.
fun assertNoEdgeCrossings(layout: Layout) {
    val segments = segmentsOf(layout.edges)
    val failures = mutableListOf<String>()
    for (i in segments.indices) {
        for (j in i + 1 until segments.size) {
            val a = segments[i]
            val b = segments[j]
            if (a.edge == b.edge) continue
            if (a.vertical == b.vertical) continue
            val vertical = if (a.vertical) a else b
            val horiz = if (a.vertical) b else a
            val x = vertical.from.x
            val y = horiz.from.y
            if (x > horiz.loX && x < horiz.hiX && y > vertical.loY && y < vertical.hiY) {
                failures += "${a.edge} crosses ${b.edge} at ($x, $y)"
            }
        }
    }
    assertTrue(
        failures.isEmpty(),
        "connectors cross:\n" + failures.joinToString("\n").prependIndent("  "),
    )
}

// Children of one couple must occupy a contiguous span of their row: no card from an unrelated
// family may sit between two of them. Interleaved siblings are what forces connectors to reach
// across the row.
//
// A sibling's own spouse counts as part of that sibling's slot - spouses are merged into one couple
// box by design, so a person who married in legitimately sits between two blood siblings and is not
// an intruder. Only a card belonging to neither the sibship nor any sibling's marriage counts.
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
// is what "everything piles up when you add a lot of people" looks like. Cards legitimately sit
// closest inside a couple box (spouseSpacing), so that is the tightest gap anything may have; the
// straightening passes shift groups around afterwards and must not close a row below it.
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

// NOTE: there is deliberately NO "unrelated branches must sit clusterSpacing apart" assertion here.
// Two attempts at one both produced false positives, because "unrelated" cannot be reconstructed
// from the graph the way the layout means it:
//   - Separate trees are positioned by a different mechanism entirely: whole components are laid
//     out independently and then abutted with subtreeSpacing (48px). That is correct and much
//     tighter than clusterSpacing, so demanding the cluster gap across a component boundary flags
//     perfectly healthy layouts (measured: four separate families, all reported as defects).
//   - Within one tree, "same cluster" means sharing a primary-parent ROOT (LayerOrdering.cluster),
//     which is not any fixed number of relationship hops. A person whose partner's grandparent is
//     also the neighbour's grandparent is the same family and legitimately sits close; a two-hop
//     neighbour test called exactly that a defect at 92px.
// Reconstructing the real cluster map in a test would just restate the production logic and assert
// nothing. assertRowSpacing above covers the part that actually matters visually - that no row is
// ever packed below the spacing floor.

// Every routed connector must be strictly orthogonal; a diagonal means a routing bug.
fun assertOrthogonal(layout: Layout) {
    for (path in layout.edges) {
        for ((a, b) in path.segments.zipWithNext()) {
            assertTrue(a.x == b.x || a.y == b.y, "diagonal segment in ${path.edge}: $a -> $b")
        }
    }
}

// A node connected to exactly one node in an adjacent generation, with room to move, should sit
// directly above/below it so the connector is one straight vertical line.
//
// Relaxation averages every neighbour a group has, which leaves a lone node near - but not on - its
// single relative's center, and the leftover offset renders as a long sideways dog-leg. A root whose
// only relation is one child was missed entirely, because straightening only ever aligned a group to
// its PARENTS: the reported case sat 482px left of its only child with open space alongside.
//
// Only checked where the row genuinely has slack: a node wedged between neighbours at the spacing
// floor legitimately cannot line up, and forcing it would break the no-overlap guarantee.
fun assertLoneRelativesAligned(layout: Layout, graph: TreeGraph, tolerance: Double = 2.0) {
    val failures = mutableListOf<String>()
    for ((id, rect) in layout.nodes) {
        val generation = layout.generations[id] ?: continue
        if (graph.partners(id).isNotEmpty()) continue

        val parents = graph.parents(id).filter { layout.generations[it] != generation }
        val children = graph.children(id).filter { layout.generations[it] != generation }
        // Only the unambiguous case: no parents and exactly one child, or vice versa. Anything with
        // relatives on both sides is a genuine compromise between two competing pulls.
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
    assertOrthogonal(layout)
    assertSiblingsContiguous(layout, graph)
    assertNoMergedBusLines(layout, graph)
    assertNoEdgeThroughCard(layout)
    assertLoneRelativesAligned(layout, graph)
}

fun countMergedBusPairs(layout: Layout, graph: TreeGraph, tolerance: Double = 1.0): Int {
    val horizontal = segmentsOf(layout.edges).filter { it.horizontal }
    var count = 0
    for (i in horizontal.indices) {
        for (j in i + 1 until horizontal.size) {
            val a = horizontal[i]
            val b = horizontal[j]
            if (a.edge == b.edge || a.from.y != b.from.y) continue
            if (minOf(a.hiX, b.hiX) - maxOf(a.loX, b.loX) <= tolerance) continue
            if (sharesBracket(graph, a.edge, b.edge)) continue
            count++
        }
    }
    return count
}

fun assertGenerationRows(layout: Layout, expected: Int) {
    assertEquals(expected, layout.generations.values.distinct().size)
}
