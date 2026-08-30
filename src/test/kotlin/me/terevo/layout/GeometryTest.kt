package me.terevo.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GeometryTest {

    @Test
    fun `rect exposes its edges and centre`() {
        val rect = Rect(left = 10.0, top = 20.0, width = 100.0, height = 50.0)

        assertEquals(110.0, rect.right)
        assertEquals(70.0, rect.bottom)
        assertEquals(Point(60.0, 45.0), rect.center)
        assertEquals(Point(60.0, 20.0), rect.topCenter)
        assertEquals(Point(60.0, 70.0), rect.bottomCenter)
    }

    @Test
    fun `touching rects do not intersect`() {
        val left = Rect(0.0, 0.0, 10.0, 10.0)
        val right = Rect(10.0, 0.0, 10.0, 10.0)

        assertFalse(left.intersects(right))
        assertFalse(right.intersects(left))
    }

    @Test
    fun `overlapping rects intersect`() {
        val first = Rect(0.0, 0.0, 10.0, 10.0)
        val second = Rect(9.0, 9.0, 10.0, 10.0)

        assertTrue(first.intersects(second))
    }

    @Test
    fun `union covers both rects`() {
        val first = Rect(0.0, 0.0, 10.0, 10.0)
        val second = Rect(20.0, 5.0, 10.0, 10.0)

        assertEquals(Rect(0.0, 0.0, 30.0, 15.0), first.union(second))
    }

    @Test
    fun `enclosing an empty collection yields an empty rect`() {
        assertEquals(Rect.EMPTY, Rect.enclosing(emptyList()))
    }

    @Test
    fun `translation moves the rect without resizing it`() {
        val rect = Rect(1.0, 2.0, 3.0, 4.0).translated(10.0, 20.0)

        assertEquals(Rect(11.0, 22.0, 3.0, 4.0), rect)
    }
}
