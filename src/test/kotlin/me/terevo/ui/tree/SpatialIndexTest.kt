package me.terevo.ui.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Rect

class SpatialIndexTest {

    @Test
    fun `query returns only nodes intersecting viewport`() {
        val layout = layoutWithGrid(100, spacing = 200.0)
        val index = SpatialIndex.build(layout)

        val visible = index.query(Rect(0.0, 0.0, 450.0, 450.0))

        assertTrue(visible.size < layout.nodes.size)
        assertEquals(
            setOf(
                NodeId("0"), NodeId("1"), NodeId("2"),
                NodeId("10"), NodeId("11"), NodeId("12"),
                NodeId("20"), NodeId("21"), NodeId("22"),
            ),
            visible,
        )
    }

    @Test
    fun `hit test checks only indexed cell`() {
        val layout = layoutWithGrid(100, spacing = 200.0)
        val index = SpatialIndex.build(layout)

        assertEquals(NodeId("11"), index.hitTest(Point(210.0, 210.0)))
        assertNull(index.hitTest(Point(150.0, 150.0)))
    }

    @Test
    fun `visible world includes one screen margin`() {
        val visible = Camera(scale = 2.0, offset = Point(0.0, 0.0)).visibleWorld(1000.0, 600.0)

        assertEquals(Rect(-500.0, -300.0, 1500.0, 900.0), visible)
    }

    private fun layoutWithGrid(count: Int, spacing: Double): Layout {
        val nodes = (0 until count).associate { index ->
            val x = (index % 10) * spacing
            val y = (index / 10) * spacing
            NodeId(index.toString()) to Rect(x, y, 100.0, 100.0)
        }
        return Layout(nodes, emptyList(), nodes.keys.associateWith { 0 }, Rect.enclosing(nodes.values))
    }
}
