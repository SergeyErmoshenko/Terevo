package me.terevo.ui.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.layout.Size

class CameraTest {

    @Test
    fun `world and screen transforms are inverse`() {
        val camera = Camera(scale = 2.5, offset = Point(30.0, -15.0))
        val world = Point(120.0, 80.0)

        assertEquals(world, camera.screenToWorld(camera.worldToScreen(world)))
    }

    @Test
    fun `zoom keeps cursor world point fixed`() {
        val camera = Camera(scale = 1.0, offset = Point(10.0, 20.0))
        val cursor = Point(200.0, 150.0)
        val before = camera.screenToWorld(cursor)

        val zoomed = camera.zoomAt(cursor, 2.0)

        assertEquals(before, zoomed.screenToWorld(cursor))
    }

    @Test
    fun `zoom clamps to supported range`() {
        assertEquals(Camera.MAX_SCALE, Camera().zoomAt(Point(0.0, 0.0), 100.0).scale)
        assertEquals(Camera.MIN_SCALE, Camera().zoomAt(Point(0.0, 0.0), 0.001).scale)
    }

    @Test
    fun `fit centers small bounds without upscaling`() {
        val camera = Camera().fit(Rect(100.0, 200.0, 400.0, 200.0), Size(1000.0, 600.0), 0.0)

        assertEquals(Point(500.0, 300.0), camera.worldToScreen(Point(300.0, 300.0)))
        assertEquals(1.0, camera.scale)
    }

    @Test
    fun `fit scales down bounds larger than viewport`() {
        val camera = Camera().fit(Rect(0.0, 0.0, 2000.0, 1000.0), Size(1000.0, 600.0), 0.0)

        assertEquals(Point(500.0, 300.0), camera.worldToScreen(Point(1000.0, 500.0)))
        assertEquals(0.5, camera.scale)
    }

    @Test
    fun `pan changes offset by screen delta`() {
        assertEquals(Point(15.0, 5.0), Camera(offset = Point(10.0, 10.0)).pan(Point(5.0, -5.0)).offset)
    }
}
