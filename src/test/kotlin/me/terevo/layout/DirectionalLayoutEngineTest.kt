package me.terevo.layout

import me.terevo.testing.graphOf
import me.terevo.testing.nodeId
import me.terevo.testing.parentage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DirectionalLayoutEngineTest {
    private val engine = DirectionalLayoutEngine(WalkerLayoutEngine())

    // Square node size: makes the "rotated by 90 degrees" property a plain coordinate
    // swap to verify, independent of how the algorithm splits spacing across width vs height.
    private val metrics = NodeMetrics(emptyMap(), Size(100.0, 100.0))

    @Test
    fun `top down direction mirrors generations so descendants render above ancestors`() {
        val graph = graphOf(
            listOf("grandparent", "parent", "child"),
            listOf(parentage("grandparent", "parent"), parentage("parent", "child")),
        )
        val request = LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.TOP_DOWN))

        val raw = WalkerLayoutEngine().layout(request)
        val layout = engine.layout(request)
        val axis = raw.bounds.top + raw.bounds.bottom

        for ((id, rect) in raw.nodes) {
            val mirrored = layout.rectOf(id)!!
            assertEquals(rect.left, mirrored.left)
            assertEquals(axis - rect.bottom, mirrored.top)
        }
        assertTrue(layout.rectOf(nodeId("child"))!!.top < layout.rectOf(nodeId("parent"))!!.top)
        assertTrue(layout.rectOf(nodeId("parent"))!!.top < layout.rectOf(nodeId("grandparent"))!!.top)
    }

    @Test
    fun `left right direction rotates the tree ninety degrees`() {
        val graph = graphOf(
            listOf("grandparent", "parent", "child"),
            listOf(parentage("grandparent", "parent"), parentage("parent", "child")),
        )
        val topDown = engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.TOP_DOWN)))
        val leftRight =
            engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.LEFT_RIGHT)))

        for ((id, rect) in topDown.nodes) {
            val rotated = leftRight.rectOf(id)!!
            assertEquals(rect.top, rotated.left)
            assertEquals(rect.left, rotated.top)
            assertEquals(rect.height, rotated.width)
            assertEquals(rect.width, rotated.height)
        }
    }

    @Test
    fun `left right edge segments are transposed`() {
        val graph = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))
        val topDown = engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.TOP_DOWN)))
        val leftRight =
            engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.LEFT_RIGHT)))

        val topDownSegments = topDown.edges.single().segments
        val leftRightSegments = leftRight.edges.single().segments

        assertEquals(topDownSegments.map { Point(it.y, it.x) }, leftRightSegments)
    }

    @Test
    fun `left right bounds are transposed`() {
        val graph = graphOf(
            listOf("grandparent", "parent", "child"),
            listOf(parentage("grandparent", "parent"), parentage("parent", "child")),
        )
        val topDown = engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.TOP_DOWN)))
        val leftRight =
            engine.layout(LayoutRequest(graph, metrics, LayoutOptions(direction = LayoutDirection.LEFT_RIGHT)))

        assertEquals(topDown.bounds.top, leftRight.bounds.left)
        assertEquals(topDown.bounds.left, leftRight.bounds.top)
        assertEquals(topDown.bounds.height, leftRight.bounds.width)
        assertEquals(topDown.bounds.width, leftRight.bounds.height)
    }
}
