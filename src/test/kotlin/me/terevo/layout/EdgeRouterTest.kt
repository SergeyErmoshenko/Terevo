package me.terevo.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.terevo.testing.NODE_HEIGHT
import me.terevo.testing.NODE_WIDTH
import me.terevo.testing.graphOf
import me.terevo.testing.parentage
import me.terevo.testing.union

class EdgeRouterTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    @Test
    fun `parentage uses orthogonal generation bus`() {
        val edge = parentage("parent", "child")
        val layout = layoutOf(listOf("parent", "child"), listOf(edge))

        assertEquals(
            EdgePath(
                edge = edge,
                segments = listOf(Point(80.0, 64.0), Point(80.0, 112.0), Point(80.0, 112.0), Point(80.0, 160.0)),
                style = EdgeStyle.BIOLOGICAL,
            ),
            layout.edges.single(),
        )
    }

    @Test
    fun `two parents share the centre of their pair`() {
        val father = parentage("father", "child")
        val mother = parentage("mother", "child")
        val layout = layoutOf(
            listOf("father", "mother", "child"),
            listOf(union("father", "mother"), father, mother),
        )

        assertEquals(Point(176.0, 64.0), layout.edges.first { it.edge == father }.segments.first())
        assertEquals(Point(176.0, 64.0), layout.edges.first { it.edge == mother }.segments.first())
    }

    @Test
    fun `non biological parentage is styled separately`() {
        val edge = parentage("parent", "child", biological = false)

        assertEquals(EdgeStyle.NON_BIOLOGICAL, layoutOf(listOf("parent", "child"), listOf(edge)).edges.single().style)
    }

    @Test
    fun `marriage connects facing card edges`() {
        val edge = union("first", "second")

        assertEquals(
            EdgePath(edge, listOf(Point(160.0, 32.0), Point(192.0, 32.0)), EdgeStyle.MARRIAGE),
            layoutOf(listOf("first", "second"), listOf(edge)).edges.single(),
        )
    }

    @Test
    fun `dissolved marriage has dedicated style`() {
        val edge = union("first", "second", dissolved = true)

        assertEquals(EdgeStyle.DISSOLVED_MARRIAGE, layoutOf(listOf("first", "second"), listOf(edge)).edges.single().style)
    }

    @Test
    fun `golden family has no proper edge crossings`() {
        val layout = layoutOf(
            listOf("first", "second", "childA", "childB", "partner", "grandchild"),
            listOf(
                union("first", "second"),
                parentage("first", "childA"),
                parentage("second", "childA"),
                parentage("first", "childB"),
                parentage("second", "childB"),
                union("childA", "partner"),
                parentage("childA", "grandchild"),
                parentage("partner", "grandchild"),
            ),
        )

        assertEquals(0, properCrossings(layout.edges))
        assertTrue(layout.edges.all { path -> path.segments.zipWithNext().all { (a, b) -> a.x == b.x || a.y == b.y } })
    }

    private fun layoutOf(nodes: List<String>, edges: List<LayoutEdge>): Layout =
        engine.layout(LayoutRequest(graphOf(nodes, edges), metrics))

    private fun properCrossings(paths: List<EdgePath>): Int {
        val segments = paths.flatMap { path -> path.segments.zipWithNext() }
        var crossings = 0
        for (first in segments.indices) {
            for (second in first + 1 until segments.size) {
                if (crosses(segments[first], segments[second])) crossings++
            }
        }
        return crossings
    }

    private fun crosses(first: Pair<Point, Point>, second: Pair<Point, Point>): Boolean {
        val firstVertical = first.first.x == first.second.x
        val secondVertical = second.first.x == second.second.x
        if (firstVertical == secondVertical) return false
        val vertical = if (firstVertical) first else second
        val horizontal = if (firstVertical) second else first
        val x = vertical.first.x
        val y = horizontal.first.y
        return x > minOf(horizontal.first.x, horizontal.second.x) &&
            x < maxOf(horizontal.first.x, horizontal.second.x) &&
            y > minOf(vertical.first.y, vertical.second.y) &&
            y < maxOf(vertical.first.y, vertical.second.y)
    }
}
