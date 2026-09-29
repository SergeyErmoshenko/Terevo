package me.terevo.layout

import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BundledDotPathTest {
    private lateinit var base: java.nio.file.Path

    @BeforeTest
    fun setup() {
        base = createTempDirectory("terevo-test")
    }

    @Test
    fun `returns the dot shipped inside the application image`() {
        val graphviz = base.resolve("graphviz").toFile().apply { mkdirs() }
        val dot = graphviz.resolve("dot").apply { createNewFile() }

        assertEquals(dot.absolutePath, bundledDotPath(base.toString()))
    }

    @Test
    fun `returns nothing when the image ships no dot`() {
        val image = base.resolve("empty-image").toFile().apply { mkdirs() }
        image.resolve("graphviz").mkdirs()

        assertNull(bundledDotPath(image.absolutePath))
    }

    @Test
    fun `returns nothing without an application image`() {
        assertNull(bundledDotPath(null))
    }
}
