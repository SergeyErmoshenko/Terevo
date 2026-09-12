package me.terevo.ui.export

import me.terevo.domain.Outcome
import me.terevo.layout.Layout
import me.terevo.layout.NodeId
import me.terevo.layout.Rect
import me.terevo.ui.theme.LightColors
import me.terevo.ui.tree.PersonVisual
import me.terevo.ui.tree.PersonVisualGender
import me.terevo.ui.tree.TreeVisuals
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals

class PngExporterTest {

    private val nodeId = NodeId("p1")
    private val bounds = Rect(left = 0.0, top = 0.0, width = 200.0, height = 72.0)
    private val layout = Layout(
        nodes = mapOf(nodeId to bounds),
        edges = emptyList(),
        generations = emptyMap(),
        bounds = bounds,
    )
    private val visuals = TreeVisuals(
        mapOf(nodeId to PersonVisual(nodeId, listOf("Иванов", "Иван"), "1900 – 1970", PersonVisualGender.MALE, version = 0L)),
    )

    @Test
    fun `exports a png with dimensions matching bounds and scale`() {
        val path = createTempFile(suffix = ".png")
        try {
            val outcome = exportTreePng(layout, visuals, LightColors, bounds, scale = 2.0, path.toString())

            assertEquals(Outcome.Ok(Unit), outcome)
            val image = ImageIO.read(path.toFile())
            assertEquals((bounds.width * 2.0).toInt(), image.width)
            assertEquals((bounds.height * 2.0).toInt(), image.height)
        } finally {
            Files.deleteIfExists(path)
        }
    }

    @Test
    fun `scales below the details threshold are clamped to remain readable`() {
        val path = createTempFile(suffix = ".png")
        try {
            exportTreePng(layout, visuals, LightColors, bounds, scale = 0.05, path.toString())

            val image = ImageIO.read(path.toFile())
            assertEquals((bounds.width * EXPORT_MIN_SCALE).toInt(), image.width)
            assertEquals((bounds.height * EXPORT_MIN_SCALE).toInt(), image.height)
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
