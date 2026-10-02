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
    fun `returns the windows dot shipped inside the application image`() {
        val dot = base.resolve("graphviz/bin/dot.exe").toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }

        assertEquals(dot.absolutePath, bundledDotPath(base.toString()))
    }

    @Test
    fun `returns the unix dot shipped inside the application image`() {
        val dot = base.resolve("graphviz/bin/dot").toFile().apply {
            parentFile.mkdirs()
            createNewFile()
        }

        assertEquals(dot.absolutePath, bundledDotPath(base.toString()))
    }

    @Test
    fun `returns nothing when the image ships no dot`() {
        base.resolve("graphviz/bin").toFile().mkdirs()

        assertNull(bundledDotPath(base.toString()))
    }

    @Test
    fun `returns nothing without an application image`() {
        assertNull(bundledDotPath(null))
    }
}
