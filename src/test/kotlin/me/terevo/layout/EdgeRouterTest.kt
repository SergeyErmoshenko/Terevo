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
                segments = listOf(Point(80.0, 64.0), Point(80.0, 104.0), Point(80.0, 104.0), Point(80.0, 224.0)),
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

        val fatherRect = layout.nodes.getValue(NodeId("father"))
        val motherRect = layout.nodes.getValue(NodeId("mother"))

        assertTrue(layout.edges.none { it.edge == marriage })
        // Each parent gets its own stem down to a shared bus - same convergence point and same
        // final drop into the child, but the two paths aren't the literal same object since each
        // starts from its own parent's own center.
        assertEquals(fatherPath.segments.last(), motherPath.segments.last())
        assertEquals(childRect.topCenter, fatherPath.segments.last())
        val busY = fatherPath.segments[1].y
        assertEquals(busY, motherPath.segments[1].y)
        // The bar sits in the gap below both parents, not flush with either card's own bottom.
        assertTrue(busY > maxOf(fatherRect.bottom, motherRect.bottom))
        // The bracket must visibly tie into each parent's own center, not start from a point
        // floating in the gap between them.
        assertTrue(fatherPath.segments.any { it.x == fatherRect.centerX && it.y == fatherRect.bottom })
        assertTrue(motherPath.segments.any { it.x == motherRect.centerX && it.y == motherRect.bottom })
    }

    @Test
    fun `married parents with two shared children get one spine per parent branching into each child`() {
        val marriage = union("father", "mother")
        val toChildA = parentage("father", "childA")
        val toChildB = parentage("mother", "childB")
        val layout = layoutOf(
            listOf("father", "mother", "childA", "childB"),
            listOf(marriage, toChildA, parentage("mother", "childA"), toChildB, parentage("father", "childB")),
        )

        val paths = layout.edges.filter { it.edge is LayoutEdge.Parentage }
        // Each parent's own stem is shared across every child they branch into, but the two
        // parents don't share a single combined spine with each other anymore - that's what put
        // the bar flush against whichever card happened to be shorter.
        val fatherPaths = paths.filter { (it.edge as LayoutEdge.Parentage).parent == NodeId("father") }
        val motherPaths = paths.filter { (it.edge as LayoutEdge.Parentage).parent == NodeId("mother") }

        assertTrue(layout.edges.none { it.edge == marriage })
        assertTrue(fatherPaths.all { it.segments.first() == fatherPaths.first().segments.first() })
        assertTrue(motherPaths.all { it.segments.first() == motherPaths.first().segments.first() })
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
    fun `unmarried co-parents converge on the same bracket point as a married couple`() {
        val father = parentage("father", "child")
        val mother = parentage("mother", "child")
        val layout = layoutOf(listOf("father", "mother", "child"), listOf(father, mother))

        val fatherPath = layout.edges.first { it.edge == father }
        val motherPath = layout.edges.first { it.edge == mother }

        assertTrue(fatherPath.segments[0].x != motherPath.segments[0].x)
        assertEquals(fatherPath.segments[2], motherPath.segments[2])
        assertEquals(fatherPath.segments.last(), motherPath.segments.last())
    }

    @Test
    fun `three unmarried co-parents of one child all converge on the child's center`() {
        val first = parentage("first", "child")
        val second = parentage("second", "child")
        val third = parentage("third", "child")
        val layout = layoutOf(listOf("first", "second", "third", "child"), listOf(first, second, third))

        val paths = listOf(first, second, third).map { edge -> layout.edges.first { it.edge == edge } }

        assertEquals(1, paths.map { it.segments[2] }.distinct().size)
        assertEquals(1, paths.map { it.segments.last() }.distinct().size)
        assertEquals(3, paths.map { it.segments[0].x }.distinct().size)
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

    @Test
    fun `two cousin branches joined by a marriage have no proper edge crossings`() {
        val layout = layoutOf(
            listOf(
                "ancestorA", "ancestorB",
                "parentA1", "parentA2", "parentB1", "parentB2",
                "cousinA", "cousinB",
            ),
            listOf(
                parentage("ancestorA", "parentA1"),
                parentage("ancestorA", "parentA2"),
                parentage("ancestorB", "parentB1"),
                parentage("ancestorB", "parentB2"),
                parentage("parentA1", "cousinA"),
                parentage("parentB1", "cousinB"),
                union("cousinA", "cousinB"),
            ),
        )

        assertEquals(0, properCrossings(layout.edges))
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
