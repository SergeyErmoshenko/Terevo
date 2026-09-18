package me.terevo.layout

import me.terevo.testing.*
import kotlin.system.measureTimeMillis
import kotlin.test.*

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
        assertEquals(0, second.generations.getValue(nodeId("parent")))
        assertEquals(1, second.generations.getValue(nodeId("child")))
        assertTrue(second.rectOf(nodeId("parent"))!!.top < second.rectOf(nodeId("child"))!!.top)
    }

    @Test
    fun `adding parent after child was cached keeps parent above child`() {
        val engine = CachedLayoutEngine()
        engine.layout(LayoutRequest(graphOf(listOf("child")), metrics))
        val withParent = graphOf(listOf("child", "parent"), listOf(parentage("parent", "child")))

        val layout = engine.layout(LayoutRequest(withParent, metrics))

        assertTrue(layout.rectOf(nodeId("parent"))!!.top < layout.rectOf(nodeId("child"))!!.top)
    }

    @Test
    fun `name size edit on a large tree completes under ten milliseconds`() {
        val graph = largeGraph()
        val engine = CachedLayoutEngine()
        engine.layout(LayoutRequest(graph, metrics))
        val changed = NodeMetrics(mapOf(nodeId(id(LARGE_TREE_SIZE - 1)) to Size(180.0, NODE_HEIGHT)), metrics.defaultSize)

        val elapsed = measureTimeMillis {
            engine.layout(LayoutRequest(graph, changed))
        }

        assertTrue(elapsed < 10, "Metric-only layout update took ${elapsed}ms")
    }

    @Test
    fun `relationship addition repositions the tree instead of leaving stale coordinates`() {
        // Horizontal placement is derived from lineage - cluster membership, order within the row,
        // and whose center a node must sit above - so attaching a relation invalidates x, not just
        // y. This used to reuse the cached x and only recompute y, which left the newly attached
        // child wherever it sat BEFORE it had a parent (measured: 408px off to the side, while a
        // full relayout put it directly under its parent). Adding someone is precisely when the
        // user is watching the tree make room, so the layout must actually be recomputed.
        val graph = largeGraph()
        val engine = CachedLayoutEngine()
        val newChild = id(LARGE_TREE_SIZE - 1)
        val newParent = id(LARGE_TREE_SIZE / 2)
        val original = engine.layout(LayoutRequest(graph, metrics))
        val changed = TreeGraph.of(
            nodes = graph.nodes,
            edges = graph.edges + parentage(newParent, newChild),
        )

        val updated = engine.layout(LayoutRequest(changed, metrics))

        // Matches a layout computed from scratch, i.e. the cache never serves a stale position.
        val fresh = WalkerLayoutEngine().layout(LayoutRequest(changed, metrics))
        assertEquals(fresh.nodes, updated.nodes)
        assertEquals(
            original.generations.getValue(nodeId(newParent)) + 1,
            updated.generations.getValue(nodeId(newChild)),
        )
    }

    private fun id(index: Int): String = index.toString().padStart(5, '0')

    private fun largeGraph(): TreeGraph {
        val nodes = (0 until LARGE_TREE_SIZE).map { node(id(it)) }
        val edges = (1 until LARGE_TREE_SIZE - 1).map { index -> parentage(id((index - 1) / 2), id(index)) }
        return TreeGraph.of(nodes, edges)
    }

    private companion object {
        // Large enough to catch a real regression in the incremental path while keeping the suite
        // quick; the full layout is re-run from scratch on every structural change.
        const val LARGE_TREE_SIZE: Int = 1_000
    }
}
