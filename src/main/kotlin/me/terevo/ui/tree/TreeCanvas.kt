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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
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
import me.terevo.domain.model.PersonId
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Size
import me.terevo.ui.Strings
import me.terevo.ui.person.RelationMode
import me.terevo.ui.person.spouseActionLabel
import me.terevo.ui.theme.TerevoTheme
import kotlin.math.abs
import kotlin.math.pow

private data class PersonContextMenu(val nodeId: NodeId, val position: Offset)

@Composable
fun TreeCanvas(
    state: TreeCanvasState,
    onIntent: (TreeCanvasIntent) -> Unit,
    onViewPerson: (PersonId) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val colors = TerevoTheme.colors
    val cornerRadiusPx = with(LocalDensity.current) { TerevoTheme.spacing.cornerRadius.toPx() }
    val density = LocalDensity.current.density
    val latestState = rememberUpdatedState(state)
    var contextMenu by remember { mutableStateOf<PersonContextMenu?>(null) }
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
                            // A trackpad fires many more scroll events per gesture than a wheel
                            // mouse's discrete notches, each with a smaller delta. Scaling the
                            // factor by that delta's magnitude (instead of a fixed step per event)
                            // keeps zoom speed proportional to actual scroll input regardless of
                            // how often the device reports events.
                            val magnitude = abs(change.scrollDelta.y).toDouble().coerceIn(0.0, MAX_SCROLL_MAGNITUDE)
                            val factor = ZOOM_SENSITIVITY.pow(if (change.scrollDelta.y > 0f) -magnitude else magnitude)
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
            }
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

private const val ZOOM_SENSITIVITY: Double = 1.035
private const val MAX_SCROLL_MAGNITUDE: Double = 3.0
