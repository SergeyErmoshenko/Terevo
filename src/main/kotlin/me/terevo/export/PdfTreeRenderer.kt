package me.terevo.export

import java.awt.Color
import me.terevo.layout.EdgePath
import me.terevo.layout.EdgeStyle
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Point
import me.terevo.layout.Rect
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font

object PdfTreeRenderer {
    fun render(
        document: PDDocument,
        layout: Layout,
        labels: Map<NodeId, ExportPersonLabel>,
        pages: List<ExportPage>,
        font: PDType0Font,
    ) {
        pages.forEach { page -> renderPage(document, layout, labels, page, font) }
    }

    private fun renderPage(
        document: PDDocument,
        layout: Layout,
        labels: Map<NodeId, ExportPersonLabel>,
        exportPage: ExportPage,
        font: PDType0Font,
    ) {
        val tile = exportPage.worldBounds
        val page = PDPage(PDRectangle(tile.width.toFloat(), tile.height.toFloat()))
        document.addPage(page)
        PDPageContentStream(document, page).use { stream ->
            layout.edges.forEach { edge -> if (edge.boundsRect().intersects(tile)) drawEdge(stream, edge, tile) }
            layout.nodes.forEach { (id, rect) ->
                if (!tile.intersects(rect)) return@forEach
                val label = labels[id] ?: return@forEach
                drawCard(stream, font, rect, label, tile)
            }
            exportPage.joinLabels.forEach { join -> drawJoinLabel(stream, font, join, tile) }
        }
    }

    private fun drawEdge(stream: PDPageContentStream, edge: EdgePath, tile: Rect) {
        stream.setStrokingColor(edgeColor(edge.style))
        stream.setLineWidth(EDGE_WIDTH)
        stream.setLineDashPattern(
            if (edge.style == EdgeStyle.NON_BIOLOGICAL) floatArrayOf(4f, 3f) else floatArrayOf(),
            0f,
        )
        edge.segments.zipWithNext().forEach { (a, b) ->
            val start = a.toPdf(tile)
            val end = b.toPdf(tile)
            stream.moveTo(start.x.toFloat(), start.y.toFloat())
            stream.lineTo(end.x.toFloat(), end.y.toFloat())
            stream.stroke()
        }
    }

    private fun drawCard(
        stream: PDPageContentStream,
        font: PDType0Font,
        rect: Rect,
        label: ExportPersonLabel,
        tile: Rect,
    ) {
        val topLeft = Point(rect.left, rect.top).toPdf(tile)
        val bottomRight = Point(rect.right, rect.bottom).toPdf(tile)
        val x = topLeft.x.toFloat()
        val y = bottomRight.y.toFloat()
        val width = rect.width.toFloat()
        val height = rect.height.toFloat()

        stream.setNonStrokingColor(Color.WHITE)
        stream.addRect(x, y, width, height)
        stream.fill()

        stream.setNonStrokingColor(genderColor(label.gender))
        stream.addRect(x, y, GENDER_STRIPE_WIDTH, height)
        stream.fill()

        stream.setStrokingColor(Color(0xB0, 0xB4, 0xBA))
        stream.setLineWidth(BORDER_WIDTH)
        stream.addRect(x, y, width, height)
        stream.stroke()

        stream.setNonStrokingColor(Color.BLACK)
        drawText(stream, font, label.name, x + TEXT_LEFT, y + height - NAME_TOP, NAME_SIZE)
        stream.setNonStrokingColor(Color.DARK_GRAY)
        drawText(stream, font, label.lifeYears, x + TEXT_LEFT, y + height - YEARS_TOP, YEARS_SIZE)
    }

    private fun drawJoinLabel(stream: PDPageContentStream, font: PDType0Font, join: JoinLabel, tile: Rect) {
        val marker = when (join.direction) {
            JoinDirection.UP -> "^"
            JoinDirection.DOWN -> "v"
            JoinDirection.LEFT -> "<"
            JoinDirection.RIGHT -> ">"
        }
        val x = when (join.direction) {
            JoinDirection.LEFT -> JOIN_MARGIN
            JoinDirection.RIGHT -> tile.width.toFloat() - JOIN_MARGIN - JOIN_WIDTH
            else -> tile.width.toFloat() / 2f
        }
        val y = when (join.direction) {
            JoinDirection.UP -> tile.height.toFloat() - JOIN_MARGIN
            JoinDirection.DOWN -> JOIN_MARGIN
            else -> tile.height.toFloat() / 2f
        }
        stream.setNonStrokingColor(Color(0x3F, 0x51, 0xB5))
        drawText(stream, font, "$marker ${join.personLabel}, стр. ${join.continuesOnPage + 1}", x, y, JOIN_TEXT_SIZE)
    }

    private fun drawText(
        stream: PDPageContentStream,
        font: PDType0Font,
        text: String,
        x: Float,
        y: Float,
        size: Float
    ) {
        if (text.isEmpty()) return
        stream.beginText()
        stream.setFont(font, size)
        stream.newLineAtOffset(x, y)
        stream.showText(text)
        stream.endText()
    }

    private fun edgeColor(style: EdgeStyle): Color = when (style) {
        EdgeStyle.BIOLOGICAL -> Color(0x8A, 0x8F, 0x98)
        EdgeStyle.NON_BIOLOGICAL -> Color(0x3F, 0x51, 0xB5)
    }

    private fun genderColor(gender: ExportGender): Color = when (gender) {
        ExportGender.MALE -> Color(0x4F, 0x86, 0xC6)
        ExportGender.FEMALE -> Color(0xC6, 0x5A, 0x8B)
        ExportGender.UNKNOWN -> Color(0x8A, 0x8F, 0x98)
    }

    private fun Point.toPdf(tile: Rect): Point = Point(x - tile.left, tile.height - (y - tile.top))

    private fun EdgePath.boundsRect(): Rect {
        val xs = segments.map { it.x }
        val ys = segments.map { it.y }
        val left = xs.minOrNull() ?: 0.0
        val top = ys.minOrNull() ?: 0.0
        return Rect(left, top, (xs.maxOrNull() ?: 0.0) - left, (ys.maxOrNull() ?: 0.0) - top)
    }

    private const val EDGE_WIDTH: Float = 1f
    private const val BORDER_WIDTH: Float = 0.5f
    private const val GENDER_STRIPE_WIDTH: Float = 6f
    private const val TEXT_LEFT: Float = 10f
    private const val NAME_TOP: Float = 16f
    private const val YEARS_TOP: Float = 34f
    private const val NAME_SIZE: Float = 10f
    private const val YEARS_SIZE: Float = 8f
    private const val JOIN_MARGIN: Float = 10f
    private const val JOIN_WIDTH: Float = 120f
    private const val JOIN_TEXT_SIZE: Float = 8f
}
