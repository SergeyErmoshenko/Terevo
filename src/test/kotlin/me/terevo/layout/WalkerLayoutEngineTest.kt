package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
    fun `golden unmarried co-parents are grouped adjacent to their shared child`() {
        assertGolden(
            graphOf(
                listOf("father", "mother", "child"),
                listOf(parentage("father", "child"), parentage("mother", "child")),
            ),
            "child=96,160|father=0,0|mother=192,0",
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
    fun `widest root subtree is balanced toward the center instead of alphabetical order`() {
        val graph = graphOf(
            listOf("a_wide", "c1", "c2", "c3", "m_narrow1", "n1", "z_narrow2", "n2"),
            listOf(
                parentage("a_wide", "c1"),
                parentage("a_wide", "c2"),
                parentage("a_wide", "c3"),
                parentage("m_narrow1", "n1"),
                parentage("z_narrow2", "n2"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val wideCenter = layout.rectOf(nodeId("a_wide"))!!.centerX
        val narrow1Center = layout.rectOf(nodeId("m_narrow1"))!!.centerX
        val narrow2Center = layout.rectOf(nodeId("z_narrow2"))!!.centerX

        assertTrue(wideCenter in minOf(narrow1Center, narrow2Center)..maxOf(narrow1Center, narrow2Center))
        assertNoOverlaps(layout)
    }

    @Test
    fun `a narrow branch nested under a common ancestor is balanced among its siblings, not shoved to alphabetical order`() {
        val graph = graphOf(
            listOf("top", "wide", "w1", "w2", "w3", "narrow_a", "narrow_z"),
            listOf(
                parentage("top", "wide"),
                parentage("top", "narrow_a"),
                parentage("top", "narrow_z"),
                parentage("wide", "w1"),
                parentage("wide", "w2"),
                parentage("wide", "w3"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val wideCenter = layout.rectOf(nodeId("wide"))!!.centerX
        val narrowACenter = layout.rectOf(nodeId("narrow_a"))!!.centerX
        val narrowZCenter = layout.rectOf(nodeId("narrow_z"))!!.centerX

        assertTrue(wideCenter in minOf(narrowACenter, narrowZCenter)..maxOf(narrowACenter, narrowZCenter))
        assertNoOverlaps(layout)
    }

    @Test
    fun `unmarried co-parents with asymmetric ancestor depth are placed side by side, not stacked`() {
        val graph = graphOf(
            nodes = listOf("grandfather", "father", "mother", "child"),
            edges = listOf(
                parentage("grandfather", "father"),
                parentage("father", "child"),
                parentage("mother", "child"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val fatherRect = layout.rectOf(nodeId("father"))!!
        val motherRect = layout.rectOf(nodeId("mother"))!!
        assertEquals(fatherRect.top, motherRect.top)
        assertFalse(fatherRect.intersects(motherRect))
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
    }

    @Test
    fun `a couple whose two members each bring their own parents keeps both ancestor lineages anchored above the couple`() {
        val graph = graphOf(
            nodes = listOf("fatherGrandfather", "motherGrandfather", "father", "mother", "child"),
            edges = listOf(
                parentage("fatherGrandfather", "father"),
                parentage("motherGrandfather", "mother"),
                union("father", "mother"),
                parentage("father", "child"),
                parentage("mother", "child"),
            ),
        )

        val layout = engine.layout(LayoutRequest(graph, metrics))

        val coupleLeft = layout.rectOf(nodeId("father"))!!.left
        val coupleRight = layout.rectOf(nodeId("mother"))!!.right
        val fatherGrandfatherCenter = layout.rectOf(nodeId("fatherGrandfather"))!!.centerX
        val motherGrandfatherCenter = layout.rectOf(nodeId("motherGrandfather"))!!.centerX

        assertTrue(fatherGrandfatherCenter in coupleLeft..coupleRight)
        assertTrue(motherGrandfatherCenter in coupleLeft..coupleRight)
        assertNoOverlaps(layout)
        assertEquals(layout, engine.layout(LayoutRequest(graph, metrics)))
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

        val minLeft = children.minOf { layout.rectOf(nodeId(it))!!.left }
        val maxRight = children.maxOf { layout.rectOf(nodeId(it))!!.right }
        assertEquals(Rect(18_308.0, 0.0, 160.0, 64.0), layout.rectOf(nodeId("parent")))
        assertEquals(0.0, minLeft)
        assertEquals(36_776.0, maxRight)
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
