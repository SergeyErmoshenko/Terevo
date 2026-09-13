package me.terevo.layout

import me.terevo.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationAssignerTest {

    @Test
    fun `diamond graph uses longest parent path`() {
        val graph = graphOf(
            nodes = listOf("root", "left", "right", "bottom"),
            edges = listOf(
                parentage("root", "left"),
                parentage("root", "right"),
                parentage("left", "bottom"),
                parentage("right", "bottom"),
            ),
        )

        assertEquals(
            mapOf(
                nodeId("root") to 0,
                nodeId("left") to 1,
                nodeId("right") to 1,
                nodeId("bottom") to 2,
            ),
            GenerationAssigner.assign(graph).generations,
        )
    }

    @Test
    fun `disconnected components are grouped independently`() {
        val graph = graphOf(
            nodes = listOf("a", "b", "c", "d", "isolated"),
            edges = listOf(parentage("a", "b"), union("c", "d")),
        )

        assertEquals(
            listOf(
                listOf(nodeId("a"), nodeId("b")),
                listOf(nodeId("c"), nodeId("d")),
                listOf(nodeId("isolated")),
            ),
            GenerationAssigner.assign(graph).components,
        )
    }

    @Test
    fun `spouse with larger descendant branch keeps its generation`() {
        val graph = graphOf(
            nodes = listOf("shallow", "deepRoot", "deep", "child", "grandchild"),
            edges = listOf(
                parentage("deepRoot", "deep"),
                parentage("deep", "child"),
                parentage("child", "grandchild"),
                union("shallow", "deep"),
            ),
        )

        val generations = GenerationAssigner.assign(graph).generations

        assertEquals(1, generations.getValue(nodeId("shallow")))
        assertEquals(1, generations.getValue(nodeId("deep")))
    }

    @Test
    fun `unmarried co-parents with asymmetric ancestor depth are pulled to the same generation`() {
        val graph = graphOf(
            nodes = listOf("grandfather", "father", "mother", "child"),
            edges = listOf(
                parentage("grandfather", "father"),
                parentage("father", "child"),
                parentage("mother", "child"),
            ),
        )

        val generations = GenerationAssigner.assign(graph).generations

        assertEquals(generations.getValue(nodeId("father")), generations.getValue(nodeId("mother")))
    }

    @Test
    fun `a deeper spouse with fewer descendants is never pulled up above their own parent`() {
        val graph = graphOf(
            nodes = listOf("grandparent", "parent", "spouse", "child", "otherChild"),
            edges = listOf(
                parentage("grandparent", "parent"),
                union("parent", "spouse"),
                parentage("parent", "child"),
                parentage("spouse", "child"),
                parentage("spouse", "otherChild"),
            ),
        )

        val generations = GenerationAssigner.assign(graph).generations

        val grandparentGen = generations.getValue(nodeId("grandparent"))
        val parentGen = generations.getValue(nodeId("parent"))
        val spouseGen = generations.getValue(nodeId("spouse"))
        val childGen = generations.getValue(nodeId("child"))

        assertEquals(parentGen, spouseGen)
        assertTrue(grandparentGen < parentGen)
        assertTrue(parentGen < childGen)
    }

    @Test
    fun `ten thousand node chain is assigned iteratively and deterministically`() {
        val count = 10_000
        val graph = TreeGraph.of(
            nodes = (0 until count).map { node(it.toString().padStart(5, '0')) },
            edges = (1 until count).map { index ->
                parentage((index - 1).toString().padStart(5, '0'), index.toString().padStart(5, '0'))
            },
        )

        val first = GenerationAssigner.assign(graph)
        val second = GenerationAssigner.assign(graph)

        assertEquals(count - 1, first.generations.getValue(nodeId("09999")))
        assertEquals(first, second)
    }
}
