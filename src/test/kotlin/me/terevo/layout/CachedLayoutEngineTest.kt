package me.terevo.layout

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import me.terevo.testing.NODE_HEIGHT
import me.terevo.testing.NODE_WIDTH
import me.terevo.testing.graphOf
import me.terevo.testing.node
import me.terevo.testing.nodeId
import me.terevo.testing.parentage

class CachedLayoutEngineTest {
    private val metrics = NodeMetrics(emptyMap(), Size(NODE_WIDTH, NODE_HEIGHT))

    @Test
    fun `identical request returns cached layout instance`() {
        val engine = CachedLayoutEngine()
        val request = LayoutRequest(graphOf(listOf("parent", "child"), listOf(parentage("parent", "child"))), metrics)

        val first = engine.layout(request)
        val second = engine.layout(request)

        assertSame(first, second)
    }

    @Test
    fun `metrics change preserves origins and updates sizes`() {
        val engine = CachedLayoutEngine()
        val graph = graphOf(listOf("person"))
        val first = engine.layout(LayoutRequest(graph, metrics))
        val resizedMetrics = NodeMetrics(mapOf(nodeId("person") to Size(240.0, 80.0)), metrics.defaultSize)

        val second = engine.layout(LayoutRequest(graph, resizedMetrics))

        assertEquals(first.rectOf(nodeId("person"))!!.copy(width = 240.0, height = 80.0), second.rectOf(nodeId("person")))
        assertEquals(first.rectOf(nodeId("person"))!!.left, second.rectOf(nodeId("person"))!!.left)
        assertEquals(first.rectOf(nodeId("person"))!!.top, second.rectOf(nodeId("person"))!!.top)
    }

    @Test
    fun `relationship change invalidates structural cache`() {
        val engine = CachedLayoutEngine()
        val withoutRelation = graphOf(listOf("parent", "child"))
        val withRelation = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))

        val first = engine.layout(LayoutRequest(withoutRelation, metrics))
        val second = engine.layout(LayoutRequest(withRelation, metrics))

        assertNotEquals(first, second)
        assertEquals(1, second.generations.getValue(nodeId("child")))
    }

    @Test
    fun `name size edit on ten thousand nodes completes under ten milliseconds`() {
        val graph = largeGraph()
        val engine = CachedLayoutEngine()
        engine.layout(LayoutRequest(graph, metrics))
        val changed = NodeMetrics(mapOf(nodeId("09999") to Size(180.0, NODE_HEIGHT)), metrics.defaultSize)

        val elapsed = measureTimeMillis {
            engine.layout(LayoutRequest(graph, changed))
        }

        assertTrue(elapsed < 10, "Metric-only layout update took ${elapsed}ms")
    }

    @Test
    fun `relationship addition on ten thousand nodes completes under fifty milliseconds`() {
        val graph = largeGraph()
        val engine = CachedLayoutEngine()
        val original = engine.layout(LayoutRequest(graph, metrics))
        val changed = TreeGraph.of(
            nodes = graph.nodes,
            edges = graph.edges + parentage("05000", "09999"),
        )
        lateinit var updated: Layout

        val elapsed = measureTimeMillis {
            updated = engine.layout(LayoutRequest(changed, metrics))
        }

        assertTrue(elapsed < 50, "Structural layout update took ${elapsed}ms")
        assertEquals(original.rectOf(nodeId("00001")), updated.rectOf(nodeId("00001")))
        assertEquals(original.rectOf(nodeId("09999"))!!.left, updated.rectOf(nodeId("09999"))!!.left)
        assertEquals(original.generations.getValue(nodeId("05000")) + 1, updated.generations.getValue(nodeId("09999")))
    }

    private fun largeGraph(): TreeGraph {
        val count = 10_000
        val nodes = (0 until count).map { node(it.toString().padStart(5, '0')) }
        val edges = (1 until count - 1).map { index ->
            parentage(((index - 1) / 2).toString().padStart(5, '0'), index.toString().padStart(5, '0'))
        }
        return TreeGraph.of(nodes, edges)
    }
}
