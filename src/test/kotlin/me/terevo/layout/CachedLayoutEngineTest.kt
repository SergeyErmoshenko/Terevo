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
    fun `a width-only change keeps the card centered in place and the connectors untouched`() {
        val engine = CachedLayoutEngine()
        val graph = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))
        val first = engine.layout(LayoutRequest(graph, metrics))
        val resizedMetrics = NodeMetrics(mapOf(nodeId("child") to Size(240.0, NODE_HEIGHT)), metrics.defaultSize)

        val second = engine.layout(LayoutRequest(graph, resizedMetrics))

        val before = first.rectOf(nodeId("child"))!!
        val after = second.rectOf(nodeId("child"))!!
        assertEquals(240.0, after.width)
        assertEquals(before.centerX, after.centerX)
        assertEquals(before.top, after.top)
        assertEquals(first.edges, second.edges)
    }

    @Test
    fun `a height change relays the tree out`() {
        val engine = CachedLayoutEngine()
        val graph = graphOf(listOf("parent", "child"), listOf(parentage("parent", "child")))
        engine.layout(LayoutRequest(graph, metrics))
        val taller = NodeMetrics(mapOf(nodeId("child") to Size(NODE_WIDTH, 120.0)), metrics.defaultSize)

        val resized = engine.layout(LayoutRequest(graph, taller))

        assertEquals(GraphvizLayoutEngine().layout(LayoutRequest(graph, taller)), resized)
    }

    @Test
    fun `a name edit that would overlap a neighbor falls back to a full relayout`() {
        // Reported case: in a small, tightly-packed family (little slack between siblings), editing
        // one person's name lengthens their card. Reusing the cached x/y and only swapping in the
        // new size - the ordinary, cheap "metrics changed" path - pushed the resized card's new
        // right edge straight into the next sibling over, since the cached x was chosen to fit the
        // OLD, narrower width. A full relayout naturally makes room instead. This mattered far more
        // in small trees: a larger tree usually has enough slack elsewhere to absorb the same
        // absolute growth without two cards ever touching.
        val engine = CachedLayoutEngine()
        val nodes = listOf("father", "mother", "kid1", "kid2", "kid3")
        val edges = listOf(
            union("father", "mother"),
            parentage("father", "kid1"), parentage("mother", "kid1"),
            parentage("father", "kid2"), parentage("mother", "kid2"),
            parentage("father", "kid3"), parentage("mother", "kid3"),
        )
        val graph = graphOf(nodes, edges)
        val narrow = NodeMetrics(emptyMap(), Size(160.0, 64.0))
        engine.layout(LayoutRequest(graph, narrow))

        val grown = NodeMetrics(mapOf(nodeId("kid1") to Size(400.0, 64.0)), narrow.defaultSize)
        val resized = engine.layout(LayoutRequest(graph, grown))

        assertNoCardOverlaps(resized)
        val fresh = GraphvizLayoutEngine().layout(LayoutRequest(graph, grown))
        assertEquals(fresh.nodes, resized.nodes)
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
        val fresh = GraphvizLayoutEngine().layout(LayoutRequest(changed, metrics))
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
