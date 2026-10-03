package me.terevo.ui.tree

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector3D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Size
import me.terevo.ui.Strings
import me.terevo.ui.person.RelationMode
import me.terevo.ui.person.spouseActionLabel
import me.terevo.ui.theme.TerevoTheme

private data class PersonContextMenu(val nodeId: NodeId, val position: Offset)

@Composable
fun TreeCanvas(
    state: TreeCanvasState,
    onIntent: (TreeCanvasIntent) -> Unit,
    onViewPerson: (PersonId) -> Unit = {},
    onDeletePerson: (PersonId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = TerevoTheme.colors
    val cornerRadiusPx = with(LocalDensity.current) { TerevoTheme.spacing.cornerRadius.toPx() }
    val density = LocalDensity.current.density
    val latestState = rememberUpdatedState(state)
    var contextMenu by remember { mutableStateOf<PersonContextMenu?>(null) }
    var pendingDeletion by remember { mutableStateOf<Person?>(null) }
    val animatedCamera = remember { Animatable(state.camera, CameraVectorConverter) }
    LaunchedEffect(state.camera) {
        if (state.camera.scale != animatedCamera.value.scale) {
            animatedCamera.animateTo(state.camera, animationSpec = tween(300, easing = LinearEasing))
        } else {
            animatedCamera.snapTo(state.camera)
        }
    }
    Box(modifier) {
        Canvas(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { size ->
                    onIntent(TreeCanvasIntent.Resize(Size(size.width.toDouble(), size.height.toDouble())))
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            if (change.scrollDelta.y == 0f) continue
                            // Every scroll event applies the same small step, so only the DIRECTION
                            // of the scroll is read and its magnitude ignored. Scaling the step by
                            // the reported delta made a hard flick zoom far faster than a gentle
                            // one and behave differently on a trackpad than on a wheel mouse; a
                            // fixed step is predictable and stays slow however hard the input.
                            val factor = if (change.scrollDelta.y > 0f) 1.0 / ZOOM_STEP else ZOOM_STEP
                            onIntent(TreeCanvasIntent.Zoom(change.position.toPoint(), factor))
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Press || !event.buttons.isSecondaryPressed) continue
                            val position = event.changes.firstOrNull()?.position ?: continue
                            val hit = latestState.value.spatialIndex.hitTest(
                                latestState.value.camera.screenToWorld(position.toPoint()),
                            )
                            if (hit == null) {
                                onIntent(TreeCanvasIntent.AddPersonAt(position.toPoint()))
                            } else {
                                contextMenu = PersonContextMenu(hit, position)
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onIntent(TreeCanvasIntent.SelectAt(it.toPoint())) },
                        onDoubleTap = { onIntent(TreeCanvasIntent.EditAt(it.toPoint())) },
                    )
                }
                .pointerInput(Unit) {
                    var draggedNode: NodeId? = null
                    detectDragGestures(
                        onDragStart = { offset ->
                            val world = latestState.value.camera.screenToWorld(offset.toPoint())
                            val hit = latestState.value.spatialIndex.hitTest(world)
                            draggedNode = hit
                            if (hit != null) onIntent(TreeCanvasIntent.DragNodeStart(hit, offset.toPoint()))
                        },
                        onDragEnd = {
                            if (draggedNode != null) onIntent(TreeCanvasIntent.DragNodeEnd)
                            draggedNode = null
                        },
                        onDragCancel = {
                            if (draggedNode != null) onIntent(TreeCanvasIntent.DragNodeCancel)
                            draggedNode = null
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        if (draggedNode != null) {
                            onIntent(TreeCanvasIntent.DragNodeMove(change.position.toPoint()))
                        } else {
                            onIntent(TreeCanvasIntent.Pan(dragAmount.toPoint()))
                        }
                    }
                },
        ) {
            val camera = animatedCamera.value
            val viewport = camera.visibleWorld(size.width.toDouble(), size.height.toDouble())
            drawDotGrid(camera, colors, viewport)
            drawGenerationBands(state.layout, camera, colors, viewport)
            val visible = state.spatialIndex.visible(state.layout, viewport)
            val layout = state.layout.copy(nodes = visible.nodes, edges = visible.edges)
            drawTree(
                layout,
                state.visuals,
                camera,
                colors,
                textMeasurer,
                state.highlight,
                cornerRadiusPx,
                density
            )
            state.nodeDrag?.let { drawDragPreview(it, state.layout, camera, colors, cornerRadiusPx) }
        }
        contextMenu?.let { menu ->
            val offset = with(LocalDensity.current) { DpOffset(menu.position.x.toDp(), menu.position.y.toDp()) }
            DropdownMenu(expanded = true, onDismissRequest = { contextMenu = null }, offset = offset) {
                DropdownMenuItem(
                    text = { Text(Strings.ADD_PARENT) },
                    onClick = {
                        onIntent(TreeCanvasIntent.AddRelativeAt(menu.nodeId, RelationMode.PARENT))
                        contextMenu = null
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.ADD_CHILD) },
                    onClick = {
                        onIntent(TreeCanvasIntent.AddRelativeAt(menu.nodeId, RelationMode.CHILD))
                        contextMenu = null
                    },
                )
                val menuPerson = state.tree.person(PersonId.parse(menu.nodeId.value))
                DropdownMenuItem(
                    text = { Text(menuPerson?.gender?.spouseActionLabel() ?: Strings.ADD_SPOUSE) },
                    onClick = {
                        onIntent(TreeCanvasIntent.AddRelativeAt(menu.nodeId, RelationMode.SPOUSE))
                        contextMenu = null
                    },
                )
                DropdownMenuItem(
                    text = { Text(Strings.VIEW_PERSON) },
                    onClick = {
                        menuPerson?.let { onViewPerson(it.id) }
                        contextMenu = null
                    },
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(Strings.DELETE_PERSON, color = colors.error) },
                    onClick = {
                        // Deleting also drops every relation the person took part in, so it asks
                        // for confirmation rather than acting on a single menu click.
                        pendingDeletion = menuPerson
                        contextMenu = null
                    },
                )
            }
        }
        pendingDeletion?.let { person ->
            AlertDialog(
                onDismissRequest = { pendingDeletion = null },
                title = { Text(Strings.DELETE_PERSON_TITLE) },
                text = { Text(Strings.deletePersonMessage(person.name.display)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            onDeletePerson(person.id)
                            pendingDeletion = null
                        },
                    ) {
                        Text(Strings.DELETE_PERSON, color = colors.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeletion = null }) { Text(Strings.CANCEL) }
                },
            )
        }
    }
}

private fun Offset.toPoint(): Point = Point(x.toDouble(), y.toDouble())

private val CameraVectorConverter = TwoWayConverter<Camera, AnimationVector3D>(
    convertToVector = { camera ->
        AnimationVector3D(
            camera.scale.toFloat(),
            camera.offset.x.toFloat(),
            camera.offset.y.toFloat()
        )
    },
    convertFromVector = { vector ->
        Camera(
            scale = vector.v1.toDouble(),
            offset = Point(vector.v2.toDouble(), vector.v3.toDouble())
        )
    },
)

// Constant zoom step per scroll event, deliberately small so zooming stays slow and controllable.
// Multiplicative, so the step is the fraction ADDED per scroll event - halving that fraction
// (2% -> 1%) is what makes zooming half as fast, not halving the constant itself.
private const val ZOOM_STEP: Double = 1.012
