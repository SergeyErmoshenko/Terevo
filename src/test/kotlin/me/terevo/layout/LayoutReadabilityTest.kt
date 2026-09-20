package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test

// Connector-level readability, as opposed to card placement. The layout suite historically only
// asserted that cards do not overlap, which a layout can satisfy while still drawing two unrelated
// couples' connectors along one shared horizontal line - visually identical to "the lines cross".
class LayoutReadabilityTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    // Reproduction of the reported tree: three couples in one generation, each with children in the
    // next. Their bus lines all sat at the same y and overlapped by up to 1107px, merging into a
    // single thick stroke across the canvas.
    private val threeCouplesGraph = graphOf(
        nodes = listOf(
            "aFather", "aMother", "bFather", "bMother", "cFather", "cMother",
            "a1", "a2", "a3", "b1", "b2", "c1",
        ),
        edges = listOf(
            union("aFather", "aMother"),
            union("bFather", "bMother"),
            union("cFather", "cMother"),
            parentage("aFather", "a1"), parentage("aMother", "a1"),
            parentage("aFather", "a2"), parentage("aMother", "a2"),
            parentage("aFather", "a3"), parentage("aMother", "a3"),
            parentage("bFather", "b1"), parentage("bMother", "b1"),
            parentage("bFather", "b2"), parentage("bMother", "b2"),
            parentage("cFather", "c1"), parentage("cMother", "c1"),
        ),
    )

    @Test
    fun `connectors of unrelated couples never merge into one line`() {
        val layout = engine.layout(LayoutRequest(threeCouplesGraph, metrics))

        assertNoMergedBusLines(layout, threeCouplesGraph)
    }

    @Test
    fun `connectors never run through an unrelated card`() {
        val layout = engine.layout(LayoutRequest(threeCouplesGraph, metrics))

        assertNoEdgeThroughCard(layout)
    }

    @Test
    fun `children of one couple stay contiguous as more are added`() {
        // The reported symptom: newly added children were scattered among their existing siblings
        // (observed row: "5, 3, 2, 1, Александр, 4, Иван"), so an unrelated card sat between two
        // siblings and their connectors had to reach across it.
        val baseNodes = listOf(
            "father", "mother",
            "alexander", "ivan", "ivanSpouse", "maxim", "stepan", "stepanSpouse", "grandchild",
        )
        val baseEdges = listOf(
            union("father", "mother"),
            parentage("father", "alexander"), parentage("mother", "alexander"),
            parentage("father", "ivan"), parentage("mother", "ivan"),
            parentage("father", "maxim"), parentage("mother", "maxim"),
            parentage("father", "stepan"), parentage("mother", "stepan"),
            union("ivan", "ivanSpouse"),
            union("stepan", "stepanSpouse"),
            parentage("ivan", "grandchild"), parentage("ivanSpouse", "grandchild"),
        )

        for (extra in 1..5) {
            val newKids = (1..extra).map { "new$it" }
            val graph = graphOf(
                baseNodes + newKids,
                baseEdges + newKids.flatMap { listOf(parentage("father", it), parentage("mother", it)) },
            )

            val layout = engine.layout(LayoutRequest(graph, metrics))

            assertSiblingsContiguous(layout, graph)
            assertNoMergedBusLines(layout, graph)
            assertNoCardOverlaps(layout)
        }
    }

    @Test
    fun `a wide multi-family generation stays readable end to end`() {
        val layout = engine.layout(LayoutRequest(threeCouplesGraph, metrics))

        assertReadable(layout, threeCouplesGraph)
    }

    @Test
    fun `a root whose only relation is one child sits above that child`() {
        // Reported as "Пётр drifted too far right" (he had in fact been left behind on the left,
        // while the child he connects to sat far right). He is a root: no parents, no spouse, one
        // child, who married into a wide sibling block. Straightening only ever aligned a group to
        // its PARENTS, so a parentless root was never pulled over its own child and sat 482px away
        // with open space alongside, dragging its connector right across the row.
        val nodes = listOf("petr", "child", "childSpouse", "bigFather", "bigMother") +
            (1..8).map { "sib$it" }
        val edges = listOf(
            parentage("petr", "child"),
            union("child", "childSpouse"),
            union("bigFather", "bigMother"),
            parentage("bigFather", "childSpouse"), parentage("bigMother", "childSpouse"),
        ) + (1..8).flatMap {
            listOf(parentage("bigFather", "sib$it"), parentage("bigMother", "sib$it"))
        }
        val graph = graphOf(nodes, edges)

        val layout = engine.layout(LayoutRequest(graph, metrics))

        assertLoneRelativesAligned(layout, graph)
        assertNoCardOverlaps(layout)
        assertNoMergedBusLines(layout, graph)
    }

    @Test
    fun `the reported family tree renders without merged or card-crossing connectors`() {
        // Topology copied verbatim (anonymized) from the real 45-person project that produced the
        // reported screenshot. A hand-built graph does not reproduce this: it takes the real mix of
        // multi-generation edges, in-law couples and an 8-child sibship to put three different
        // couples' bus lines on one shared y. Measured before the fix: 6 cross-family bus overlaps
        // of up to 1107px, while a strict segment-intersection test reported 0.
        val layout = engine.layout(LayoutRequest(reportedGraph, metrics))

        assertNoCardOverlaps(layout)
        assertOrthogonal(layout)
        assertNoMergedBusLines(layout, reportedGraph)
        assertNoEdgeThroughCard(layout)
    }

    private val reportedGraph = graphOf(
        nodes = (0..44).map { "p%02d".format(it) },
        edges = listOf(
            parentage("p01", "p07"),
            parentage("p03", "p37"), parentage("p03", "p44"),
            parentage("p04", "p02"), parentage("p04", "p14"), parentage("p04", "p19"),
            parentage("p04", "p22"), parentage("p04", "p24"), parentage("p04", "p27"),
            parentage("p04", "p31"), parentage("p04", "p34"), parentage("p04", "p38"),
            parentage("p07", "p41"),
            parentage("p09", "p32"),
            parentage("p11", "p35"),
            parentage("p14", "p00"), parentage("p14", "p21"), parentage("p14", "p25"),
            parentage("p15", "p03"), parentage("p15", "p08"), parentage("p15", "p10"),
            parentage("p15", "p13"), parentage("p15", "p20"), parentage("p15", "p33"),
            parentage("p15", "p40"), parentage("p15", "p42"),
            parentage("p16", "p39"),
            parentage("p17", "p36"),
            parentage("p18", "p35"),
            parentage("p23", "p37"), parentage("p23", "p44"),
            parentage("p24", "p07"),
            parentage("p28", "p03"), parentage("p28", "p08"), parentage("p28", "p10"),
            parentage("p28", "p13"), parentage("p28", "p20"), parentage("p28", "p33"),
            parentage("p28", "p40"), parentage("p28", "p42"),
            parentage("p29", "p00"), parentage("p29", "p21"), parentage("p29", "p25"),
            parentage("p30", "p01"),
            parentage("p32", "p06"),
            parentage("p35", "p41"),
            parentage("p36", "p06"),
            parentage("p39", "p12"), parentage("p39", "p26"), parentage("p39", "p28"),
            parentage("p41", "p17"),
            parentage("p44", "p09"),
            union("p05", "p12"),
            union("p15", "p28"),
            union("p18", "p11"),
            union("p23", "p03"),
            union("p24", "p01"),
            union("p29", "p14"),
            union("p32", "p36"),
            union("p33", "p43"),
            union("p35", "p07"),
        ),
    )
}
