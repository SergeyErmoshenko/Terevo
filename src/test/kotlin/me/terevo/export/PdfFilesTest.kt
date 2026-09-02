package me.terevo.export

import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertTrue
import me.terevo.testing.largeFamilyTree
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper

class PdfFilesTest {

    @Test
    fun `exports a large tree as a paginated pdf with selectable text`() {
        val tree = largeFamilyTree(PEOPLE)
        val path = createTempFile(suffix = ".pdf")

        try {
            PdfFiles.export(tree, path.toString())

            val document = Loader.loadPDF(path.toFile())
            try {
                assertTrue(document.numberOfPages > 1, "expected multiple pages, got ${document.numberOfPages}")
                val text = PDFTextStripper().getText(document)
                assertTrue(text.contains("Фамилия0"), "expected exported text to contain a person's surname")
            } finally {
                document.close()
            }
        } finally {
            Files.deleteIfExists(path)
        }
    }
}

private const val PEOPLE = 500
