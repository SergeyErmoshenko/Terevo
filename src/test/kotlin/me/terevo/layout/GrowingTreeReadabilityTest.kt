package me.terevo.layout

import me.terevo.testing.*
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

// Grows a family tree one person at a time and re-checks the whole drawing after EVERY addition.
//
// Every layout defect reported so far was found by a human noticing a bad picture after adding
// someone, and each hand-written regression test only pinned the one shape that happened to be
// reported. The defects were never in the shape itself - they appeared once a tree got wide enough
// for two unrelated families to compete for the same horizontal band. So instead of guessing shapes,
// this builds many trees by applying the same operations the UI offers (add child, add spouse, add
// parent) and asserts the readability invariants at every step.
//
// Seeds are fixed, so a failure is always reproducible and reports the exact step and operation that
// broke the drawing.
class GrowingTreeReadabilityTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    // Seed count is a deliberate trade-off: the whole point of this test is breadth, but it re-lays
    // out and fully re-checks the tree on every single step, so cost grows quickly. 40 seeds x 30
    // steps (~1200 tree states) runs in a few seconds and is what caught both union-through-card
    // defects. Widen SEEDS locally when hunting a new layout bug - 120 x 45 was run while fixing
    // these and passes, it is just too slow to keep in the normal suite.
    private val seeds = 1..40
    private val steps = 30

    @Test
    fun `a tree stays readable after every single person added`() {
        for (seed in seeds) {
            growAndCheck(seed, steps)
        }
    }

    @Test
    fun `a deep narrow lineage stays readable as generations are appended`() {
        // Only-child chains are the shape that produces long multi-generation edges routed through
        // dummy waypoints, which is where connectors have the most room to go wrong.
        var nodes = listOf("root")
        var edges = emptyList<LayoutEdge>()
        for (generation in 1..12) {
            val child = "gen$generation"
            val parent = if (generation == 1) "root" else "gen${generation - 1}"
            nodes = nodes + child
            edges = edges + parentage(parent, child)
            val graph = graphOf(nodes, edges)

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertReadableAt(layout, graph, "depth $generation")
        }
    }

    @Test
    fun `a single couple stays readable up to twenty children`() {
        // The directly reported case: children added to one couple one after another. The picture
        // broke at the fifth.
        val base = listOf("father", "mother")
        val baseEdges = listOf<LayoutEdge>(union("father", "mother"))
        for (count in 1..20) {
            val kids = (1..count).map { "kid$it" }
            val graph = graphOf(
                base + kids,
                baseEdges + kids.flatMap { listOf(parentage("father", it), parentage("mother", it)) },
            )

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertReadableAt(layout, graph, "$count children")
            assertSiblingsContiguous(layout, graph)
        }
    }

    @Test
    fun `several unrelated families on one generation stay readable as each grows`() {
        // Cross-family bus overlap only appears once two or more families compete for the same
        // horizontal band, so this grows several sibships side by side.
        val families = listOf("a", "b", "c", "d")
        var nodes = families.flatMap { listOf("${it}Father", "${it}Mother") }
        var edges = families.map<String, LayoutEdge> { union("${it}Father", "${it}Mother") }

        for (round in 1..6) {
            for (family in families) {
                val child = "$family$round"
                nodes = nodes + child
                edges = edges + parentage("${family}Father", child) + parentage("${family}Mother", child)
                val graph = graphOf(nodes, edges)

                val layout = engine.layout(LayoutRequest(graph, metrics))

                assertReadableAt(layout, graph, "family $family round $round")
            }
        }
    }

    @Test
    fun `a bridging couple's child tracks its parents as unrelated siblings pile up`() {
        // Reported case: a couple bridging two lineages (one partner married in from a different
        // family) has a child of their own. As unrelated siblings are added to the FIRST partner's
        // side, that whole row gets pushed sideways to make room - and the couple's own child, one
        // generation down, must follow. It used to be left behind: the push was applied to the
        // couple's row without ever reaching its child, who had no room of its own to catch up
        // (its row was three unrelated grandchildren already packed at minimum spacing). The gap
        // grew roughly linearly with each added sibling, from ~96px to over 1300px at fifteen.
        val base = listOf(
            "father", "mother", "first", "firstSpouse", "firstChild",
            "spouseFather", "spouseMother", "last", "lastChild",
            "rightUncle", "rightUncleChild",
        )
        val baseEdges = listOf<LayoutEdge>(
            union("father", "mother"),
            union("spouseFather", "spouseMother"),
            union("first", "firstSpouse"),
            parentage("father", "first"), parentage("mother", "first"),
            parentage("father", "last"), parentage("mother", "last"),
            parentage("spouseFather", "firstSpouse"), parentage("spouseMother", "firstSpouse"),
            parentage("spouseFather", "rightUncle"), parentage("spouseMother", "rightUncle"),
            parentage("first", "firstChild"), parentage("firstSpouse", "firstChild"),
            parentage("last", "lastChild"),
            parentage("rightUncle", "rightUncleChild"),
        )
        for (count in 0..15) {
            val middles = (1..count).map { "mid$it" }
            val graph = graphOf(
                base + middles,
                baseEdges + middles.flatMap { listOf(parentage("father", it), parentage("mother", it)) },
            )

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertReadableAt(layout, graph, "$count unrelated siblings")
            val first = layout.nodes.getValue(nodeId("first"))
            val firstChild = layout.nodes.getValue(nodeId("firstChild"))
            val gap = abs(firstChild.centerX - first.centerX)
            assertTrue(
                gap < 200.0,
                "firstChild drifted ${gap}px from its parents' couple box after $count unrelated siblings",
            )
        }
    }

    @Test
    fun `an unrelated narrow family packed next to a widening one stays free of crossings`() {
        // Real reported case: two entirely unrelated single-parent families - no marriage, no
        // common ancestor anywhere in the graph - end up sharing a generation with nothing to
        // keep them apart beyond the ordinary spacing floor. A parent centered above many children
        // fans its bracket out far past its own card's edges; a neighbor's card and connector
        // packed right up against that same edge sat inside the bracket's swing and the two
        // connectors crossed one generation down from either party's own card, even though
        // neither party's card itself overlapped anything.
        val wideParent = "wideParent"
        val narrowParent = "narrowParent"
        val narrowChildren = listOf("narrowChild1", "narrowChild2", "narrowChild3")
        for (wideChildCount in 1..12) {
            val wideChildren = (1..wideChildCount).map { "wideChild$it" }
            val nodes = listOf(wideParent, narrowParent) + wideChildren + narrowChildren
            val edges = wideChildren.map { parentage(wideParent, it) } +
                narrowChildren.map { parentage(narrowParent, it) }
            val graph = graphOf(nodes, edges)

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertReadableAt(layout, graph, "$wideChildCount wide children")
            // assertNoEdgeCrossings is deliberately not part of the shared assertReadableAt bundle -
            // several other, separately reported crossing shapes still fail it and are not what
            // this test is pinning down - so it's asserted directly here, scoped to this one shape.
            try {
                assertNoEdgeCrossings(layout)
            } catch (failure: AssertionError) {
                throw AssertionError("connectors cross at $wideChildCount wide children\n${failure.message}", failure)
            }
        }
    }

    @Test
    fun `a full sibling's wide subtree stays clear of its plain sibling's connector`() {
        // Newest reported case: two FULL siblings under the same parents - not merely two
        // unrelated families sharing a generation - where one sibling's own subtree fans out wide
        // and the other has a single child. The siblings' own cards sit at ordinary sibling
        // spacing, so nothing about their own row looks wrong, but the wide sibling's fanned-out
        // children reached far enough to cross the plain sibling's descender to its own child, one
        // generation down (reported as "person 5 needs to move right, along with its whole
        // branch").
        val father = "father"
        val mother = "mother"
        val narrowChild = "narrowChild"
        for (wideChildCount in 1..12) {
            val wideKids = (1..wideChildCount).map { "wideKid$it" }
            val nodes = listOf(father, mother, "wide", "narrow") + wideKids + listOf(narrowChild)
            val edges = listOf<LayoutEdge>(
                union(father, mother),
                parentage(father, "wide"), parentage(mother, "wide"),
                parentage(father, "narrow"), parentage(mother, "narrow"),
                parentage("narrow", narrowChild),
            ) + wideKids.map { parentage("wide", it) }
            val graph = graphOf(nodes, edges)

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertReadableAt(layout, graph, "$wideChildCount wide children")
            try {
                assertNoEdgeCrossings(layout)
            } catch (failure: AssertionError) {
                throw AssertionError("connectors cross at $wideChildCount wide children\n${failure.message}", failure)
            }
        }
    }

    // Applies random but valid tree-building operations, mirroring what the UI can do.
    private fun growAndCheck(seed: Int, steps: Int) {
        val random = Random(seed)
        var nodes = listOf("p0")
        var edges = emptyList<LayoutEdge>()
        val log = mutableListOf<String>()

        for (step in 1..steps) {
            val graph = graphOf(nodes, edges)
            val anchor = nodes.random(random)
            val fresh = "p$step"
            // Only operations that keep the graph a valid family tree are attempted; an operation
            // that would not apply to the chosen anchor is skipped rather than forced.
            val anchorId = nodeId(anchor)
            val operation = when (random.nextInt(3)) {
                0 -> "child of $anchor"
                1 -> if (graph.partners(anchorId).isEmpty()) "spouse of $anchor" else "child of $anchor"
                else -> if (graph.parents(anchorId).size < 2) "parent of $anchor" else "child of $anchor"
            }
            val newEdge = when {
                operation.startsWith("child") -> parentage(anchor, fresh)
                operation.startsWith("spouse") -> union(anchor, fresh)
                else -> parentage(fresh, anchor)
            }
            nodes = nodes + fresh
            edges = edges + newEdge
            log += "step $step: $operation"

            val grown = graphOf(nodes, edges)
            val layout = engine.layout(LayoutRequest(grown, metrics))

            assertReadableAt(
                layout,
                grown,
                "seed $seed, ${log.last()}\n  history:\n${log.joinToString("\n").prependIndent("    ")}",
            )
        }
    }

    private fun assertReadableAt(layout: Layout, graph: TreeGraph, context: String) {
        try {
            assertNoCardOverlaps(layout)
            // Not merely "not overlapping": a row squeezed below the spacing floor is the visual
            // "everything piles up" complaint even when no two cards strictly intersect.
            assertRowSpacing(layout)
            assertOrthogonal(layout)
            assertNoMergedBusLines(layout, graph)
            assertNoEdgeThroughCard(layout)
            // Layout must be deterministic, otherwise a "fixed" drawing can silently come back.
            assertTrue(
                layout == engine.layout(LayoutRequest(graph, metrics)),
                "layout is not deterministic",
            )
        } catch (failure: AssertionError) {
            throw AssertionError("unreadable layout at $context\n${failure.message}", failure)
        }
    }
}
