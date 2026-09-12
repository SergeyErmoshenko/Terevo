package me.terevo.ui.tree

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
                            val factor = if (change.scrollDelta.y > 0f) ZOOM_OUT else ZOOM_IN
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
            val viewport = state.camera.visibleWorld(size.width.toDouble(), size.height.toDouble())
            drawDotGrid(state.camera, colors, viewport)
            val visible = state.spatialIndex.visible(state.layout, viewport)
            val layout = state.layout.copy(nodes = visible.nodes, edges = visible.edges)
            drawTree(
                layout,
                state.visuals,
                state.camera,
                colors,
                textMeasurer,
                state.highlight,
                cornerRadiusPx,
                density
            )
            state.nodeDrag?.let { drawDragPreview(it, state.layout, state.camera, colors, cornerRadiusPx) }
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

private const val ZOOM_IN: Double = 1.1
private const val ZOOM_OUT: Double = 1.0 / ZOOM_IN
