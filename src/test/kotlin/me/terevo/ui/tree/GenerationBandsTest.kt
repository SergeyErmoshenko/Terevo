package me.terevo.ui.tree

import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Rect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerationBandsTest {
    @Test
    fun `draws nothing for an empty layout`() {
        assertTrue(generationBands(Layout.EMPTY, viewport()).isEmpty())
    }

    @Test
    fun `draws one band per occupied generation row`() {
        val layout = layoutOf(
            "grandparent" to 0,
            "parent" to 1,
            "child" to 2,
        )

        val bands = generationBands(layout, viewport())

        assertEquals(3, bands.size)
        assertEquals(listOf(0, 1, 2), bands.map { it.first })
        assertTrue(bands[0].second.top < bands[1].second.top, "rows must run top to bottom")
        assertTrue(bands[1].second.top < bands[2].second.top)
    }

    @Test
    fun `gives every person on a generation row the same band`() {
        val sharedTop = 100.0
        val layout = Layout(
            nodes = mapOf(
                NodeId("child") to Rect(left = 0.0, top = sharedTop, width = 200.0, height = 64.0),
                NodeId("cousin") to Rect(left = 400.0, top = sharedTop, width = 200.0, height = 64.0),
            ),
            edges = emptyList(),
            generations = mapOf(NodeId("child") to 3, NodeId("cousin") to 3),
            bounds = Rect(0.0, sharedTop, 600.0, 64.0),
        )

        val bands = generationBands(layout, viewport())

        assertEquals(1, bands.size)
        assertEquals(sharedTop, bands.single().second.top, 0.001)
        assertEquals(64.0, bands.single().second.height, 0.001)
    }

    @Test
    fun `skips a generation whose cards are not in the layout`() {
        val layout = Layout(
            nodes = mapOf(
                NodeId("a") to Rect(left = 0.0, top = 0.0, width = 200.0, height = 64.0),
                NodeId("b") to Rect(left = 0.0, top = 300.0, width = 200.0, height = 64.0),
            ),
            edges = emptyList(),
            generations = mapOf(
                NodeId("a") to 0,
                NodeId("b") to 2,
                NodeId("gone") to 1,
            ),
            bounds = Rect(0.0, 0.0, 200.0, 364.0),
        )

        val bands = generationBands(layout, viewport())

        assertEquals(2, bands.size)
        assertEquals(listOf(0, 1), bands.map { it.first })
    }

    @Test
    fun `each band spans the whole viewport width`() {
        val layout = layoutOf("a" to 0)

        val bands = generationBands(layout, viewport(left = -500.0, width = 4000.0))

        assertEquals(-500.0, bands.single().second.left, 0.001)
        assertEquals(4000.0, bands.single().second.width, 0.001)
    }

    private fun layoutOf(vararg entries: Pair<String, Int>): Layout {
        val nodes = entries.mapIndexed { index, (id, _) ->
            NodeId(id) to Rect(
                left = index * 250.0,
                top = entries[index].second * GENERATION_SPACING,
                width = 200.0,
                height = 64.0,
            )
        }.toMap()
        return Layout(
            nodes = nodes,
            edges = emptyList(),
            generations = entries.associate { NodeId(it.first) to it.second },
            bounds = Rect.enclosing(nodes.values),
        )
    }

    private fun viewport(left: Double = 0.0, width: Double = 1200.0): Rect =
        Rect(left = left, top = 0.0, width = width, height = 800.0)

    private companion object {
        const val GENERATION_SPACING = 220.0
    }
}
