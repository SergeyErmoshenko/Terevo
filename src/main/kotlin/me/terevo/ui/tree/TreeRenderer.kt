package me.terevo.ui.tree

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import me.terevo.layout.*
import me.terevo.ui.theme.TerevoColors
import kotlin.math.floor
import kotlin.math.roundToInt

fun DrawScope.drawDotGrid(camera: Camera, colors: TerevoColors, viewport: Rect) {
    val screenSpacing = BASE_GRID_SPACING * camera.scale
    val spacing = if (screenSpacing < MIN_GRID_SPACING_PX) BASE_GRID_SPACING * GRID_LOD_FACTOR else BASE_GRID_SPACING
    val startX = floor(viewport.left / spacing) * spacing
    val startY = floor(viewport.top / spacing) * spacing
    val color = colors.outline.copy(alpha = GRID_DOT_ALPHA)
    var worldY = startY
    while (worldY <= viewport.bottom) {
        var worldX = startX
        while (worldX <= viewport.right) {
            val screenPoint = camera.worldToScreen(Point(worldX, worldY)).toOffset()
            drawCircle(color = color, radius = GRID_DOT_RADIUS, center = screenPoint)
            worldX += spacing
        }
        worldY += spacing
    }
}

fun DrawScope.drawTree(
    layout: Layout,
    visuals: TreeVisuals,
    camera: Camera,
    colors: TerevoColors,
    textMeasurer: TextMeasurer,
    highlight: TreeHighlight,
    cornerRadiusPx: Float = 0f,
    density: Float = 1f,
) {
    layout.edges.forEach { drawEdge(it, camera, colors, highlight.accentOf(it.edge)) }
    layout.nodes.forEach { (id, rect) ->
        val visual = visuals.persons[id] ?: return@forEach
        drawPerson(
            rect,
            visual,
            camera,
            colors,
            textMeasurer,
            highlight.accentOf(id),
            highlight.roleOf(id),
            cornerRadiusPx,
            density,
        )
    }
}

private fun DrawScope.drawEdge(path: EdgePath, camera: Camera, colors: TerevoColors, accent: NodeAccent) {
    val baseColor = when (path.style) {
        EdgeStyle.BIOLOGICAL, EdgeStyle.MARRIAGE, EdgeStyle.DISSOLVED_MARRIAGE -> colors.unknownGender
        EdgeStyle.NON_BIOLOGICAL -> colors.accent
    }
    val color = when (accent) {
        NodeAccent.FOCUSED, NodeAccent.RELATED -> colors.selection
        NodeAccent.MUTED -> baseColor.copy(alpha = MUTED_ALPHA)
        NodeAccent.NEUTRAL -> baseColor
    }
    val effect = if (path.style == EdgeStyle.NON_BIOLOGICAL || path.style == EdgeStyle.DISSOLVED_MARRIAGE) {
        PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
    } else {
        null
    }
    path.segments.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = camera.worldToScreen(start).toOffset(),
            end = camera.worldToScreen(end).toOffset(),
            strokeWidth = EDGE_WIDTH,
            pathEffect = effect,
        )
    }
}

private fun DrawScope.drawPerson(
    rect: Rect,
    visual: PersonVisual,
    camera: Camera,
    colors: TerevoColors,
    textMeasurer: TextMeasurer,
    accent: NodeAccent,
    role: String?,
    cornerRadiusPx: Float,
    density: Float = 1f,
) {
    val topLeft = camera.worldToScreen(Point(rect.left, rect.top)).toOffset()
    val size = Size((rect.width * camera.scale).toFloat(), (rect.height * camera.scale).toFloat())
    val cornerRadius = CornerRadius((cornerRadiusPx * camera.scale).toFloat())
    val genderColor = when (visual.gender) {
        PersonVisualGender.MALE -> colors.male
        PersonVisualGender.FEMALE -> colors.female
        PersonVisualGender.UNKNOWN -> colors.unknownGender
    }
    val contentAlpha = if (accent == NodeAccent.MUTED) MUTED_ALPHA else 1f
    if (accent != NodeAccent.MUTED) {
        drawRoundRect(
            color = colors.outline.copy(alpha = SHADOW_ALPHA),
            topLeft = topLeft + Offset(0f, (SHADOW_OFFSET * camera.scale).toFloat()),
            size = size,
            cornerRadius = cornerRadius,
        )
    }
    drawRoundRect(
        color = colors.cardSurface.copy(alpha = contentAlpha),
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius,
    )
    drawRect(
        color = genderColor.copy(alpha = contentAlpha),
        topLeft = topLeft,
        size = Size(GENDER_STRIPE_WIDTH, size.height)
    )
    when (accent) {
        NodeAccent.FOCUSED -> drawRoundRect(
            color = colors.selection,
            topLeft = topLeft,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(FOCUSED_WIDTH),
        )

        NodeAccent.RELATED -> drawRoundRect(
            color = colors.selection.copy(alpha = RELATED_ALPHA),
            topLeft = topLeft,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(RELATED_WIDTH),
        )

        NodeAccent.NEUTRAL, NodeAccent.MUTED -> Unit
    }
    if (camera.scale < DETAILS_SCALE) return

    val thumbnail = visual.thumbnailPath?.let { ThumbnailCache.get(it) }
    var textLeft = (TEXT_LEFT * camera.scale).toFloat()
    if (thumbnail != null && accent != NodeAccent.MUTED) {
        val thumbSize = (THUMBNAIL_SIZE * camera.scale).toFloat()
        val thumbTopLeft = topLeft + Offset(
            (GENDER_STRIPE_WIDTH + THUMBNAIL_MARGIN) * camera.scale.toFloat(),
            (THUMBNAIL_MARGIN * camera.scale).toFloat()
        )
        clipRect(thumbTopLeft.x, thumbTopLeft.y, thumbTopLeft.x + thumbSize, thumbTopLeft.y + thumbSize) {
            drawImage(
                image = thumbnail,
                dstOffset = IntOffset(thumbTopLeft.x.roundToInt(), thumbTopLeft.y.roundToInt()),
                dstSize = IntSize(thumbSize.roundToInt(), thumbSize.roundToInt()),
            )
        }
        textLeft = thumbTopLeft.x - topLeft.x + thumbSize + (THUMBNAIL_MARGIN * camera.scale).toFloat()
    }
    val maxTextWidth = (size.width - textLeft - (TEXT_RIGHT_MARGIN * camera.scale).toFloat())
        .coerceAtLeast(0f)
        .roundToInt()
    val nameStyle = TextStyle(
        color = colors.textPrimary.copy(alpha = contentAlpha),
        fontSize = (NAME_SIZE * camera.scale / density).sp,
    )
    val lineGap = (NAME_LINE_GAP * camera.scale).toFloat()
    clipRect(topLeft.x, topLeft.y, topLeft.x + size.width, topLeft.y + size.height) {
        var contentTop = (TEXT_TOP * camera.scale).toFloat()
        visual.nameLines.forEach { line ->
            val measured = textMeasurer.measure(
                line,
                nameStyle,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTextWidth),
            )
            drawText(measured, topLeft = topLeft + Offset(textLeft, contentTop))
            contentTop += measured.size.height + lineGap
        }
        val years = textMeasurer.measure(
            visual.lifeYears,
            TextStyle(
                color = colors.textSecondary.copy(alpha = contentAlpha),
                fontSize = (YEARS_SIZE * camera.scale / density).sp,
            ),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = maxTextWidth),
        )
        drawText(years, topLeft = topLeft + Offset(textLeft, contentTop))
    }
    if (role != null) {
        val label = textMeasurer.measure(
            role,
            TextStyle(color = colors.accent, fontSize = (ROLE_SIZE * camera.scale / density).sp),
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            maxLines = 1,
            constraints = Constraints(maxWidth = size.width.roundToInt()),
        )
        val roleTop = topLeft.y - label.size.height - (ROLE_GAP * camera.scale).toFloat()
        drawText(label, topLeft = Offset(topLeft.x, roleTop))
    }
}

fun DrawScope.drawDragPreview(
    dragState: NodeDragState,
    layout: Layout,
    camera: Camera,
    colors: TerevoColors,
    cornerRadiusPx: Float = 0f,
) {
    val rect = dragState.currentRect
    val topLeft = camera.worldToScreen(Point(rect.left, rect.top)).toOffset()
    val size = Size((rect.width * camera.scale).toFloat(), (rect.height * camera.scale).toFloat())
    val cornerRadius = CornerRadius((cornerRadiusPx * camera.scale).toFloat())
    drawRoundRect(
        color = colors.accent.copy(alpha = DRAG_GHOST_ALPHA),
        topLeft = topLeft,
        size = size,
        cornerRadius = cornerRadius,
        style = Stroke(DRAG_GHOST_STROKE),
    )
    val target = dragState.hoverTarget?.let(layout::rectOf) ?: return
    val ringColor = if (dragState.hoverValid == true) colors.accent else colors.outline.copy(alpha = MUTED_ALPHA)
    val targetTopLeft = camera.worldToScreen(Point(target.left, target.top)).toOffset() -
            Offset(DRAG_RING_INSET, DRAG_RING_INSET)
    val targetSize = Size(
        (target.width * camera.scale).toFloat() + DRAG_RING_INSET * 2,
        (target.height * camera.scale).toFloat() + DRAG_RING_INSET * 2,
    )
    drawRoundRect(
        color = ringColor,
        topLeft = targetTopLeft,
        size = targetSize,
        cornerRadius = cornerRadius,
        style = Stroke(DRAG_RING_WIDTH),
    )
}

private fun Point.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

private const val DETAILS_SCALE: Double = 0.3
private const val EDGE_WIDTH: Float = 2f
private const val FOCUSED_WIDTH: Float = 3f
private const val RELATED_WIDTH: Float = 2f
private const val RELATED_ALPHA: Float = 0.7f
private const val MUTED_ALPHA: Float = 0.3f
private const val GENDER_STRIPE_WIDTH: Float = 4f
private const val SHADOW_ALPHA: Float = 0.18f
private const val SHADOW_OFFSET: Float = 3f
private const val TEXT_LEFT: Float = 14f
private const val TEXT_RIGHT_MARGIN: Float = 10f
private const val THUMBNAIL_SIZE: Float = 40f
private const val THUMBNAIL_MARGIN: Float = 8f
private const val TEXT_TOP: Float = 8f
private const val NAME_LINE_GAP: Float = 2f
private const val ROLE_GAP: Float = 4f
private const val NAME_SIZE: Int = 20
private const val YEARS_SIZE: Int = 16
private const val ROLE_SIZE: Int = 14
private const val BASE_GRID_SPACING: Double = 32.0
private const val MIN_GRID_SPACING_PX: Double = 12.0
private const val GRID_LOD_FACTOR: Double = 4.0
private const val GRID_DOT_ALPHA: Float = 0.4f
private const val GRID_DOT_RADIUS: Float = 1.5f
private const val DRAG_GHOST_ALPHA: Float = 0.4f
private const val DRAG_GHOST_STROKE: Float = 2f
private const val DRAG_RING_WIDTH: Float = 3f
private const val DRAG_RING_INSET: Float = 4f
