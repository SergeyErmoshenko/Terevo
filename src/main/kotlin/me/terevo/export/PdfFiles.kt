package me.terevo.export

import java.io.IOException
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.model.FamilyTree
import me.terevo.layout.LayoutOptions
import me.terevo.layout.Size
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.font.PDType0Font

object PdfFiles {
    val A4_LANDSCAPE: Size = Size(width = 841.89, height = 595.28)

    fun export(tree: FamilyTree, path: String, pageSize: Size = A4_LANDSCAPE): Outcome<Unit> = try {
        val exported = ExportTreeMapper.map(tree, LayoutOptions())
        PDDocument().use { document ->
            val font = PdfFiles::class.java.getResourceAsStream(FONT_RESOURCE)
                ?.use { PDType0Font.load(document, it, true) }
                ?: throw IOException("Font resource $FONT_RESOURCE not found")
            val pages = TreePagination.paginate(exported.layout, exported.labels, pageSize)
            PdfTreeRenderer.render(document, exported.layout, exported.labels, pages, font)
            document.save(path)
        }
        Outcome.Ok(Unit)
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    private const val FONT_RESOURCE = "/fonts/PTSans-Regular.ttf"
}
