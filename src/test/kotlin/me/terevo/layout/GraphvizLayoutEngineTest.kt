package me.terevo.layout

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import me.terevo.testing.graphOf
import me.terevo.testing.nodeId
import me.terevo.testing.parentage
import me.terevo.testing.union
import kotlin.test.Test
import kotlin.test.assertTrue

class GraphvizLayoutEngineTest {
    private val engine = GraphvizLayoutEngine()
    private val size = Size(240.0, 64.0)

    private fun layout(graph: TreeGraph, options: LayoutOptions = LayoutOptions()): Layout =
        engine.layout(LayoutRequest(graph, NodeMetrics(emptyMap(), size), options))

    @Test
    fun `node pixel size and generation spacing survive the round trip`() {
        val result = layout(
            graphOf(listOf("p", "c"), listOf(parentage("p", "c"))),
            LayoutOptions(generationSpacing = 150.0),
        )
        val parent = result.nodes.getValue(nodeId("p"))
        val child = result.nodes.getValue(nodeId("c"))

        parent.width shouldBe size.width
        parent.height shouldBe size.height
        // Graphviz rounds rank positions to whole points, and the family-node rank between two
        // generations adds its own rounding, so the gap may be off by up to two pixels.
        (child.top - parent.bottom) shouldBe (150.0 plusOrMinus 2.0)
    }

    @Test
    fun `spouses share a row and their child hangs from between them`() {
        val result = layout(
            graphOf(
                listOf("a", "b", "c"),
                listOf(union("a", "b"), parentage("a", "c"), parentage("b", "c")),
            ),
        )
        val a = result.nodes.getValue(nodeId("a"))
        val b = result.nodes.getValue(nodeId("b"))
        val c = result.nodes.getValue(nodeId("c"))

        a.top shouldBe b.top
        assertTrue(c.top > a.bottom)
        val childLine = result.edges.first { it.edge == parentage("a", "c") }
        val start = childLine.segments.first()
        assertTrue(start.x > minOf(a.right, b.right) - 1 && start.x < maxOf(a.left, b.left) + 1, "$start")
        childLine.segments shouldBe result.edges.first { it.edge == parentage("b", "c") }.segments
    }

    @Test
    fun `every relation produces a styled path that touches both cards`() {
        val graph = graphOf(
            listOf("a", "b", "c", "d"),
            listOf(union("a", "b", dissolved = true), parentage("a", "c"), parentage("b", "c"), parentage("c", "d", false)),
        )
        val result = layout(graph)

        result.edges.map { it.edge }.toSet() shouldBe graph.edges.toSet()
        result.edges.first { it.edge is LayoutEdge.Union }.style shouldBe EdgeStyle.DISSOLVED_MARRIAGE
        result.edges.first { it.edge == parentage("c", "d", false) }.style shouldBe EdgeStyle.NON_BIOLOGICAL
        val marriage = result.edges.first { it.edge is LayoutEdge.Union }.segments
        assertTouches(marriage.first(), result.nodes.getValue(nodeId("a")))
        assertTouches(marriage.last(), result.nodes.getValue(nodeId("b")))
        val direct = result.edges.first { it.edge == parentage("c", "d", false) }.segments
        assertTouches(direct.first(), result.nodes.getValue(nodeId("c")))
        assertTouches(direct.last(), result.nodes.getValue(nodeId("d")))
    }

    @Test
    fun `rows line up across unrelated families`() {
        val result = layout(
            graphOf(
                listOf("p1", "c1", "g1", "p2", "c2"),
                listOf(parentage("p1", "c1"), parentage("c1", "g1"), parentage("p2", "c2")),
            ),
        )
        val generations = result.generations
        result.nodes.entries.groupBy { generations.getValue(it.key) }.values.forEach { row ->
            row.map { it.value.centerY }.distinct().size shouldBe 1
        }
    }

    @Test
    fun `cousins share a row even when one spouse's recorded ancestry is deeper`() {
        // Reported shape: father's own lineage is recorded one generation deeper than his wife's,
        // which used to push the wife (and every descendant) one row down while her sister's
        // family stayed put, splitting first and second cousins across rows.
        val graph = graphOf(
            listOf(
                "greatGrandfather", "grandfatherA", "grandmotherA", "father",
                "sorokinF", "sorokinM", "lyudmila", "tatyana", "kotov",
                "vladimir", "dmitry", "violetta", "maxim",
            ),
            listOf(
                parentage("greatGrandfather", "grandfatherA"),
                union("grandfatherA", "grandmotherA"),
                parentage("grandfatherA", "father"), parentage("grandmotherA", "father"),
                union("sorokinF", "sorokinM"),
                parentage("sorokinF", "lyudmila"), parentage("sorokinM", "lyudmila"),
                parentage("sorokinF", "tatyana"), parentage("sorokinM", "tatyana"),
                union("father", "lyudmila"),
                parentage("father", "vladimir"), parentage("lyudmila", "vladimir"),
                union("kotov", "tatyana"),
                parentage("kotov", "dmitry"), parentage("tatyana", "dmitry"),
                parentage("vladimir", "violetta"),
                parentage("dmitry", "maxim"),
            ),
        )
        val rows = layout(graph).generations

        fun row(id: String) = rows.getValue(nodeId(id))
        row("lyudmila") shouldBe row("tatyana")
        row("father") shouldBe row("lyudmila")
        row("vladimir") shouldBe row("dmitry")
        row("violetta") shouldBe row("maxim")
        row("vladimir") shouldBe row("father") + 1
    }

    private fun assertTouches(point: Point, rect: Rect) {
        val tolerance = 2.0
        assertTrue(
            point.x >= rect.left - tolerance && point.x <= rect.right + tolerance &&
                point.y >= rect.top - tolerance && point.y <= rect.bottom + tolerance,
            "$point is not on $rect",
        )
    }
}
