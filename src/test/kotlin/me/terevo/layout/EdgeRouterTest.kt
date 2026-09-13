package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
    fun `married parents with a shared child draw one common bracket instead of a direct connector`() {
        val marriage = union("father", "mother")
        val father = parentage("father", "child")
        val mother = parentage("mother", "child")
        val layout = layoutOf(
            listOf("father", "mother", "child"),
            listOf(marriage, father, mother),
        )

        val childRect = layout.nodes.getValue(NodeId("child"))
        val fatherPath = layout.edges.first { it.edge == father }
        val motherPath = layout.edges.first { it.edge == mother }

        assertTrue(layout.edges.none { it.edge == marriage })
        assertEquals(fatherPath.segments, motherPath.segments)
        assertEquals(childRect.topCenter, fatherPath.segments.last())
    }

    @Test
    fun `married parents with two shared children get one spine branching into each child`() {
        val marriage = union("father", "mother")
        val toChildA = parentage("father", "childA")
        val toChildB = parentage("mother", "childB")
        val layout = layoutOf(
            listOf("father", "mother", "childA", "childB"),
            listOf(marriage, toChildA, parentage("mother", "childA"), toChildB, parentage("father", "childB")),
        )

        val paths = layout.edges.filter { it.edge is LayoutEdge.Parentage }
        val spineStart = paths.first().segments.first()

        assertTrue(layout.edges.none { it.edge == marriage })
        assertTrue(paths.all { it.segments.first() == spineStart })
        assertEquals(4, paths.size)
    }

    @Test
    fun `childless couple is connected by a direct line`() {
        val marriage = union("first", "second")
        val layout = layoutOf(listOf("first", "second"), listOf(marriage))

        val path = layout.edges.single()
        assertEquals(marriage, path.edge)
        assertEquals(EdgeStyle.MARRIAGE, path.style)
        assertEquals(2, path.segments.size)
    }

    @Test
    fun `dissolved childless couple is styled as a dissolved marriage`() {
        val marriage = union("first", "second", dissolved = true)
        val layout = layoutOf(listOf("first", "second"), listOf(marriage))

        assertEquals(EdgeStyle.DISSOLVED_MARRIAGE, layout.edges.single().style)
    }

    @Test
    fun `non biological parentage is styled separately`() {
        val edge = parentage("parent", "child", biological = false)

        assertEquals(EdgeStyle.NON_BIOLOGICAL, layoutOf(listOf("parent", "child"), listOf(edge)).edges.single().style)
    }

    @Test
    fun `unmarried co-parents still land on different points of the child`() {
        val father = parentage("father", "child")
        val mother = parentage("mother", "child")
        val layout = layoutOf(listOf("father", "mother", "child"), listOf(father, mother))

        val fatherPath = layout.edges.first { it.edge == father }
        val motherPath = layout.edges.first { it.edge == mother }

        assertTrue(fatherPath.segments.last().x != motherPath.segments.last().x)
        assertTrue(fatherPath.segments[1].y == motherPath.segments[1].y)
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
