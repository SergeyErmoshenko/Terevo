package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TreeGraphTest {

    @Test
    fun `edges pointing outside the node set are dropped`() {
        val graph = graphOf(
            nodes = listOf("a", "b"),
            edges = listOf(parentage("a", "b"), parentage("a", "ghost"), union("b", "ghost")),
        )

        assertEquals(1, graph.edges.size)
        assertEquals(listOf(nodeId("a")), graph.parents(nodeId("b")))
        assertTrue(graph.partners(nodeId("b")).isEmpty())
    }

    @Test
    fun `navigation follows parentage in both directions`() {
        val graph = graphOf(
            nodes = listOf("father", "mother", "child"),
            edges = listOf(parentage("father", "child"), parentage("mother", "child")),
        )

        assertEquals(listOf(nodeId("father"), nodeId("mother")), graph.parents(nodeId("child")))
        assertEquals(listOf(nodeId("child")), graph.children(nodeId("father")))
        assertTrue(graph.children(nodeId("child")).isEmpty())
    }

    @Test
    fun `union is symmetric`() {
        val graph = graphOf(listOf("a", "b"), listOf(union("a", "b")))

        assertEquals(listOf(nodeId("b")), graph.partners(nodeId("a")))
        assertEquals(listOf(nodeId("a")), graph.partners(nodeId("b")))
    }

    @Test
    fun `duplicate unions collapse in the partner list`() {
        val graph = graphOf(listOf("a", "b"), listOf(union("a", "b"), union("b", "a")))

        assertEquals(listOf(nodeId("b")), graph.partners(nodeId("a")))
    }

    @Test
    fun `nodes are ordered by sort key then identifier`() {
        val graph = TreeGraph.of(
            nodes = listOf(node("z", sortKey = "1900"), node("a", sortKey = "1950"), node("b", sortKey = "1900")),
            edges = emptyList(),
        )

        assertEquals(listOf(nodeId("b"), nodeId("z"), nodeId("a")), graph.sortedNodeIds())
    }

    @Test
    fun `children are ordered deterministically`() {
        val graph = TreeGraph.of(
            nodes = listOf(
                node("parent", sortKey = "1900"),
                node("younger", sortKey = "1930"),
                node("older", sortKey = "1920"),
            ),
            edges = listOf(parentage("parent", "younger"), parentage("parent", "older")),
        )

        assertEquals(listOf(nodeId("older"), nodeId("younger")), graph.children(nodeId("parent")))
    }

    @Test
    fun `empty graph knows nothing`() {
        assertFalse(TreeGraph.EMPTY.contains(nodeId("a")))
        assertTrue(TreeGraph.EMPTY.parents(nodeId("a")).isEmpty())
    }
}
