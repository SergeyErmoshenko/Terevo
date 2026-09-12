package me.terevo.ui.tree

import me.terevo.layout.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeCanvasStateTest {
    private val person = NodeId("person")
    private val layout = Layout(
        nodes = mapOf(person to Rect(0.0, 0.0, 200.0, 72.0)),
        edges = emptyList(),
        generations = mapOf(person to 0),
        bounds = Rect(0.0, 0.0, 200.0, 72.0),
    )

    @Test
    fun `first viewport measurement fits tree in center`() {
        val state = TreeCanvasState(layout = layout, spatialIndex = SpatialIndex.build(layout))

        val resized = reduceTreeCanvas(state, TreeCanvasIntent.Resize(Size(800.0, 600.0)))

        assertEquals(Point(300.0, 264.0), resized.camera.offset)
    }

    @Test
    fun `subsequent viewport resize preserves user camera`() {
        val camera = Camera(scale = 1.5, offset = Point(75.0, 90.0))
        val state = TreeCanvasState(
            layout = layout,
            spatialIndex = SpatialIndex.build(layout),
            camera = camera,
            viewport = Size(800.0, 600.0),
        )

        val resized = reduceTreeCanvas(state, TreeCanvasIntent.Resize(Size(900.0, 700.0)))

        assertEquals(camera, resized.camera)
    }
}
