package me.terevo.gedcom

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import me.terevo.testing.shouldBeOk

class GedcomFilesTest {

    @Test
    fun `reads UTF 16 little endian GEDCOM`() {
        val content = "0 HEAD\n1 CHAR UNICODE\n0 @I1@ INDI\n1 NAME Иван /Иванов/\n0 TRLR"
        val path = createTempFile(suffix = ".ged")
        val bytes = content.toByteArray(StandardCharsets.UTF_16LE)
        Files.write(path, byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + bytes)

        val preview = GedcomFiles.preview(path.toString()).shouldBeOk()

        assertEquals(1, preview.people)
        Files.deleteIfExists(path)
    }
}
