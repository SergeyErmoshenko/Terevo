package me.terevo.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.terevo.testing.NODE_HEIGHT
import me.terevo.testing.NODE_WIDTH
import me.terevo.testing.graphOf
import me.terevo.testing.nodeId
import me.terevo.testing.parentage
import me.terevo.testing.union

class WalkerLayoutEngineTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    @Test
    fun `golden singleton`() {
        assertGolden(
            graphOf(listOf("person")),
            "person=0,0",
        )
    }

    @Test
    fun `golden married pair`() {
        assertGolden(
            graphOf(listOf("first", "second"), listOf(union("first", "second"))),
            "first=0,0|second=192,0",
        )
    }

    @Test
    fun `golden three generations`() {
        assertGolden(
            graphOf(
                listOf("grandparent", "parent", "child"),
                listOf(parentage("grandparent", "parent"), parentage("parent", "child")),
            ),
            "child=0,320|grandparent=0,0|parent=0,160",
        )
    }

    @Test
    fun `golden remarriage keeps partners in one compound group`() {
        assertGolden(
            graphOf(
                listOf("person", "first", "second", "childA", "childB"),
                listOf(
                    union("person", "first"),
                    union("person", "second"),
                    parentage("person", "childA"),
                    parentage("first", "childA"),
                    parentage("person", "childB"),
                    parentage("second", "childB"),
                ),
            ),
            "childA=100,160|childB=284,160|first=0,0|person=192,0|second=384,0",
        )
    }

    @Test
    fun `golden adoption uses the same geometry as parentage`() {
        assertGolden(
            graphOf(listOf("parent", "child"), listOf(parentage("parent", "child", biological = false))),
            "child=0,160|parent=0,0",
        )
    }

    @Test
    fun `golden disconnected components are spaced horizontally`() {
        assertGolden(
            graphOf(listOf("a", "b", "c"), listOf(parentage("a", "b"))),
            "a=0,0|b=0,160|c=208,0",
        )
    }

    @Test
    fun `golden two hundred siblings do not overlap`() {
        val children = (0 until 200).map { "child${it.toString().padStart(3, '0')}" }
        val graph = graphOf(
            nodes = listOf("parent") + children,
            edges = children.map { parentage("parent", it) },
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        assertEquals(Rect(18_308.0, 0.0, 160.0, 64.0), layout.rectOf(nodeId("parent")))
        assertEquals(Rect(0.0, 160.0, 160.0, 64.0), layout.rectOf(nodeId("child000")))
        assertEquals(Rect(36_616.0, 160.0, 160.0, 64.0), layout.rectOf(nodeId("child199")))
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `golden twenty generations stay vertically aligned`() {
        val nodes = (0 until 20).map { "person${it.toString().padStart(2, '0')}" }
        val graph = graphOf(
            nodes = nodes,
            edges = nodes.zipWithNext().map { (parent, child) -> parentage(parent, child) },
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        assertEquals(Rect(0.0, 0.0, 160.0, 64.0), layout.rectOf(nodeId("person00")))
        assertEquals(Rect(0.0, 3_040.0, 160.0, 64.0), layout.rectOf(nodeId("person19")))
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `spacing options control node distances`() {
        val graph = graphOf(listOf("parent", "a", "b"), listOf(parentage("parent", "a"), parentage("parent", "b")))
        val options = LayoutOptions(siblingSpacing = 50.0, generationSpacing = 120.0)

        val layout = engine.layout(LayoutRequest(graph, metrics, options))

        assertEquals(50.0, layout.rectOf(nodeId("b"))!!.left - layout.rectOf(nodeId("a"))!!.right)
        assertEquals(184.0, layout.rectOf(nodeId("a"))!!.top)
    }

    private fun assertGolden(graph: TreeGraph, expected: String) {
        val request = LayoutRequest(graph, metrics)
        val first = engine.layout(request)
        val second = engine.layout(request)

        assertEquals(expected, coordinates(first))
        assertEquals(first, second)
        assertNoOverlaps(first)
    }

    private fun coordinates(layout: Layout): String = layout.nodes.entries
        .sortedBy { it.key.value }
        .joinToString("|") { (id, rect) -> "${id.value}=${rect.left.toInt()},${rect.top.toInt()}" }

    private fun assertNoOverlaps(layout: Layout) {
        val rects = layout.nodes.values.toList()
        for (first in rects.indices) {
            for (second in first + 1 until rects.size) {
                assertFalse(rects[first].intersects(rects[second]), "${rects[first]} overlaps ${rects[second]}")
            }
        }
        assertTrue(layout.bounds.width >= 0.0)
    }
}
