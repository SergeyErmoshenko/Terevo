package me.terevo.ui.tree

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.sp
import me.terevo.layout.EdgePath
import me.terevo.layout.EdgeStyle
import me.terevo.layout.Layout
import me.terevo.layout.Point
import me.terevo.layout.Rect
import me.terevo.ui.theme.TerevoColors

fun DrawScope.drawTree(
    layout: Layout,
    visuals: TreeVisuals,
    camera: Camera,
    colors: TerevoColors,
    textMeasurer: TextMeasurer,
    highlight: TreeHighlight,
) {
    layout.edges.forEach { drawEdge(it, camera, colors, highlight.accentOf(it.edge)) }
    layout.nodes.forEach { (id, rect) ->
        val visual = visuals.persons[id] ?: return@forEach
        drawPerson(rect, visual, camera, colors, textMeasurer, highlight.accentOf(id), highlight.roleOf(id))
    }
}

private fun DrawScope.drawEdge(path: EdgePath, camera: Camera, colors: TerevoColors, accent: NodeAccent) {
    val baseColor = when (path.style) {
        EdgeStyle.BIOLOGICAL, EdgeStyle.MARRIAGE -> colors.unknownGender
        EdgeStyle.NON_BIOLOGICAL -> colors.selection
        EdgeStyle.DISSOLVED_MARRIAGE -> colors.female
    }
    val color = when (accent) {
        NodeAccent.FOCUSED, NodeAccent.RELATED -> colors.selection
        NodeAccent.MUTED -> baseColor.copy(alpha = MUTED_ALPHA)
        NodeAccent.NEUTRAL -> baseColor
    }
    val effect = if (path.style == EdgeStyle.NON_BIOLOGICAL) PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
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
) {
    val topLeft = camera.worldToScreen(Point(rect.left, rect.top)).toOffset()
    val size = Size((rect.width * camera.scale).toFloat(), (rect.height * camera.scale).toFloat())
    val genderColor = when (visual.gender) {
        PersonVisualGender.MALE -> colors.male
        PersonVisualGender.FEMALE -> colors.female
        PersonVisualGender.UNKNOWN -> colors.unknownGender
    }
    val contentAlpha = if (accent == NodeAccent.MUTED) MUTED_ALPHA else 1f
    drawRect(color = Color.White.copy(alpha = contentAlpha), topLeft = topLeft, size = size)
    drawRect(color = genderColor.copy(alpha = contentAlpha), topLeft = topLeft, size = Size(GENDER_STRIPE_WIDTH, size.height))
    when (accent) {
        NodeAccent.FOCUSED -> drawRect(
            color = colors.selection,
            topLeft = topLeft,
            size = size,
            style = Stroke(FOCUSED_WIDTH),
        )
        NodeAccent.RELATED -> drawRect(
            color = colors.selection.copy(alpha = RELATED_ALPHA),
            topLeft = topLeft,
            size = size,
            style = Stroke(RELATED_WIDTH),
        )
        NodeAccent.NEUTRAL, NodeAccent.MUTED -> Unit
    }
    if (camera.scale < DETAILS_SCALE) return

    val name = textMeasurer.measure(
        visual.name,
        TextStyle(color = Color.Black.copy(alpha = contentAlpha), fontSize = (NAME_SIZE * camera.scale).sp),
    )
    val years = textMeasurer.measure(
        visual.lifeYears,
        TextStyle(color = Color.DarkGray.copy(alpha = contentAlpha), fontSize = (YEARS_SIZE * camera.scale).sp),
    )
    val textLeft = (TEXT_LEFT * camera.scale).toFloat()
    drawText(name, topLeft = topLeft + Offset(textLeft, (TEXT_TOP * camera.scale).toFloat()))
    drawText(years, topLeft = topLeft + Offset(textLeft, (YEARS_TOP * camera.scale).toFloat()))
    if (role != null) {
        val label = textMeasurer.measure(
            role,
            TextStyle(color = colors.selection, fontSize = (ROLE_SIZE * camera.scale).sp),
        )
        val roleTop = topLeft.y - label.size.height - (ROLE_GAP * camera.scale).toFloat()
        drawText(label, topLeft = Offset(topLeft.x, roleTop))
    }
}

private fun Point.toOffset(): Offset = Offset(x.toFloat(), y.toFloat())

private const val DETAILS_SCALE: Double = 0.3
private const val EDGE_WIDTH: Float = 2f
private const val FOCUSED_WIDTH: Float = 3f
private const val RELATED_WIDTH: Float = 2f
private const val RELATED_ALPHA: Float = 0.7f
private const val MUTED_ALPHA: Float = 0.3f
private const val GENDER_STRIPE_WIDTH: Float = 6f
private const val TEXT_LEFT: Float = 14f
private const val TEXT_TOP: Float = 8f
private const val YEARS_TOP: Float = 40f
private const val ROLE_GAP: Float = 4f
private const val NAME_SIZE: Int = 14
private const val YEARS_SIZE: Int = 12
private const val ROLE_SIZE: Int = 11
