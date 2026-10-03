package me.terevo.ui.tree

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.layout.*
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
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

    @Test
    fun `home button goes to the first person added to the tree`() {
        val first = person(surname = "Первый")
        val second = person(surname = "Второй")
        val state = homeState(first, second, searchAnchor = null)

        val homed = reduceTreeCanvas(state, TreeCanvasIntent.FitToScreen)

        assertEquals(cameraOn(state, first), homed.camera)
    }

    @Test
    fun `home button goes to the person picked from search instead`() {
        val first = person(surname = "Первый")
        val second = person(surname = "Второй")
        val state = homeState(first, second, searchAnchor = second.id.node())

        val homed = reduceTreeCanvas(state, TreeCanvasIntent.FitToScreen)

        assertEquals(cameraOn(state, second), homed.camera)
    }

    private fun homeState(first: Person, second: Person, searchAnchor: NodeId?): TreeCanvasState {
        val tree = FamilyTree.of(listOf(first, second), emptyList()).shouldBeOk()
        val layout = Layout(
            nodes = mapOf(
                second.id.node() to Rect(0.0, 0.0, 200.0, 72.0),
                first.id.node() to Rect(1000.0, 400.0, 200.0, 72.0),
            ),
            edges = emptyList(),
            generations = mapOf(first.id.node() to 1, second.id.node() to 0),
            bounds = Rect(0.0, 0.0, 1200.0, 472.0),
            mainPersonId = second.id.node(),
        )
        return TreeCanvasState(
            layout = layout,
            spatialIndex = SpatialIndex.build(layout),
            viewport = Size(800.0, 600.0),
            tree = tree,
            searchAnchor = searchAnchor,
        )
    }

    private fun cameraOn(state: TreeCanvasState, target: Person): Camera =
        Camera(scale = Camera.DEFAULT_SCALE).center(state.layout.rectOf(target.id.node())!!, state.viewport)

    private fun PersonId.node(): NodeId = NodeId(value.toString())
}
