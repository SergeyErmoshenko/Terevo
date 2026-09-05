package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PinnedLayoutTest {
    private val engine = WalkerLayoutEngine()
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    @Test
    fun `empty pins leave the layout unchanged`() {
        val graph = graphOf(listOf("parent", "a", "b"), listOf(parentage("parent", "a"), parentage("parent", "b")))
        val layout = engine.layout(LayoutRequest(graph, metrics))

        assertEquals(layout, layout.withPinned(emptyMap(), graph))
    }

    @Test
    fun `pinned node moves to the override position, others stay put`() {
        val graph = graphOf(listOf("parent", "a", "b"), listOf(parentage("parent", "a"), parentage("parent", "b")))
        val layout = engine.layout(LayoutRequest(graph, metrics))
        val override = Point(500.0, 500.0)

        val pinned = layout.withPinned(mapOf(nodeId("a") to override), graph)

        val aRect = pinned.rectOf(nodeId("a"))!!
        assertEquals(override.x, aRect.left)
        assertEquals(override.y, aRect.top)
        assertEquals(layout.rectOf(nodeId("a"))!!.width, aRect.width)
        assertEquals(layout.rectOf(nodeId("a"))!!.height, aRect.height)
        assertEquals(layout.rectOf(nodeId("parent")), pinned.rectOf(nodeId("parent")))
        assertEquals(layout.rectOf(nodeId("b")), pinned.rectOf(nodeId("b")))
    }

    @Test
    fun `edges are rerouted to follow the pinned position`() {
        val graph = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))
        val layout = engine.layout(LayoutRequest(graph, metrics))

        val pinned = layout.withPinned(mapOf(nodeId("child") to Point(900.0, 900.0)), graph)

        assertNotEquals(layout.edges, pinned.edges)
        val childRect = pinned.rectOf(nodeId("child"))!!
        val edge = pinned.edges.single()
        assertEquals(childRect.topCenter, edge.segments.last())
    }

    @Test
    fun `bounds are recomputed to enclose the pinned position`() {
        val graph = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))
        val layout = engine.layout(LayoutRequest(graph, metrics))

        val pinned = layout.withPinned(mapOf(nodeId("child") to Point(-1_000.0, -1_000.0)), graph)

        assertEquals(-1_000.0, pinned.bounds.left)
        assertEquals(-1_000.0, pinned.bounds.top)
    }
}
