package me.terevo.layout

import me.terevo.testing.graphOf
import me.terevo.testing.nodeId
import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutContractTest {

    @Test
    fun `node metrics return measured size when available`() {
        val measured = Size(width = 200.0, height = 80.0)
        val metrics = NodeMetrics(
            sizes = mapOf(nodeId("measured") to measured),
            defaultSize = Size(width = 160.0, height = 64.0),
        )

        assertEquals(measured, metrics.sizeOf(nodeId("measured")))
    }

    @Test
    fun `node metrics fall back to default size`() {
        val default = Size(width = 160.0, height = 64.0)
        val metrics = NodeMetrics(sizes = emptyMap(), defaultSize = default)

        assertEquals(default, metrics.sizeOf(nodeId("unknown")))
    }

    @Test
    fun `layout request keeps graph metrics and options separate`() {
        val graph = graphOf(listOf("root"))
        val metrics = NodeMetrics(emptyMap(), Size(width = 160.0, height = 64.0))
        val options = LayoutOptions(root = nodeId("root"), mode = LayoutMode.DESCENDANTS, depth = 3)

        val request = LayoutRequest(graph, metrics, options)

        assertEquals(graph, request.graph)
        assertEquals(metrics, request.metrics)
        assertEquals(options, request.options)
    }
}
