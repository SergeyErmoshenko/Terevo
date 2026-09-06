package me.terevo.ui.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.layout.Size

class TreeNavigationTest {
    private val first = NodeId("first")
    private val second = NodeId("second")
    private val layout = Layout(
        nodes = mapOf(first to Rect(0.0, 0.0, 100.0, 50.0), second to Rect(300.0, 200.0, 100.0, 50.0)),
        edges = emptyList(),
        generations = mapOf(first to 0, second to 1),
        bounds = Rect(0.0, 0.0, 400.0, 250.0),
    )
    private val visuals = TreeVisuals(
        mapOf(
            first to PersonVisual(first, listOf("Первый"), "1900–1980", PersonVisualGender.MALE, 1),
            second to PersonVisual(second, listOf("Второй"), "1930–", PersonVisualGender.UNKNOWN, 1),
        ),
    )
    private val initial = TreeNavigationState(
        canvas = TreeCanvasState(
            layout = layout,
            spatialIndex = SpatialIndex.build(layout),
            visuals = visuals,
            viewport = Size(800.0, 600.0),
        ),
    )

    @Test
    fun `selection updates canvas details and centers camera`() {
        val selected = reduceNavigation(initial, TreeNavigationIntent.Select(second), ::details)

        assertEquals(second, selected.canvas.selected)
        assertEquals("Второй", selected.details?.person?.nameLines?.joinToString(" "))
        assertEquals(Point(50.0, 75.0), selected.canvas.camera.offset)
        assertTrue(selected.cameraAnimationMillis < 300)
    }

    @Test
    fun `back and forward restore navigation history`() {
        val firstSelected = reduceNavigation(initial, TreeNavigationIntent.Select(first), ::details)
        val secondSelected = reduceNavigation(firstSelected, TreeNavigationIntent.Select(second), ::details)

        val back = reduceNavigation(secondSelected, TreeNavigationIntent.Back, ::details)
        val forward = reduceNavigation(back, TreeNavigationIntent.Forward, ::details)

        assertEquals(first, back.canvas.selected)
        assertEquals(second, forward.canvas.selected)
    }

    @Test
    fun `clicking background clears selection`() {
        val selected = reduceNavigation(initial, TreeNavigationIntent.Select(first), ::details)

        val cleared = reduceNavigation(selected, TreeNavigationIntent.Click(Point(-1_000.0, -1_000.0)), ::details)

        assertNull(cleared.canvas.selected)
        assertNull(cleared.details)
    }

    @Test
    fun `double click selects and requests editor`() {
        val edited = reduceNavigation(initial, TreeNavigationIntent.DoubleClick(Point(10.0, 10.0)), ::details)

        assertEquals(first, edited.canvas.selected)
        assertEquals(first, edited.editorPerson)
    }

    private fun details(id: NodeId): PersonDetails? {
        val person = visuals.persons[id] ?: return null
        return PersonDetails(person, emptyList(), emptyList(), emptyList())
    }
}
