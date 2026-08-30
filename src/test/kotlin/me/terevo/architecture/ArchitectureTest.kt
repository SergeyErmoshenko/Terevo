package me.terevo.architecture

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.assertTrue

class ArchitectureTest {

    @Test
    fun `domain does not depend on frameworks or other layers`() {
        assertNoImports(
            layer = "me.terevo.domain",
            forbidden = listOf(
                "androidx.compose",
                "app.cash.sqldelight",
                "java.sql",
                "java.io",
                "java.nio.file",
                "me.terevo.ui",
                "me.terevo.persistence",
                "me.terevo.layout",
                "me.terevo.gedcom",
                "me.terevo.app",
            ),
        )
    }

    @Test
    fun `layout depends on nothing but stdlib`() {
        assertNoImports(
            layer = "me.terevo.layout",
            forbidden = listOf(
                "androidx.compose",
                "app.cash.sqldelight",
                "me.terevo.ui",
                "me.terevo.persistence",
                "me.terevo.domain",
                "me.terevo.gedcom",
                "me.terevo.app",
            ),
        )
    }

    @Test
    fun `ui does not reach storage directly`() {
        assertNoImports(
            layer = "me.terevo.ui",
            forbidden = listOf(
                "app.cash.sqldelight",
                "java.sql",
                "me.terevo.persistence",
                "me.terevo.gedcom",
            ),
        )
    }

    @Test
    fun `adapters do not depend on ui`() {
        assertNoImports(
            layer = "me.terevo.persistence",
            forbidden = listOf("androidx.compose", "me.terevo.ui", "me.terevo.app"),
        )
        assertNoImports(
            layer = "me.terevo.gedcom",
            forbidden = listOf("androidx.compose", "me.terevo.ui", "me.terevo.app"),
        )
    }

    @Test
    fun `domain reports failures as values instead of throwing`() {
        val violations = productionFilesOf("me.terevo.domain")
            .flatMap { file ->
                file.text.lines()
                    .withIndex()
                    .filter { (_, line) -> THROW.containsMatchIn(line) || line.contains("!!") }
                    .map { (index, line) -> "${file.name}:${index + 1}: ${line.trim()}" }
            }

        assertTrue(
            violations.isEmpty(),
            "Domain must return Outcome instead of throwing\n" + violations.joinToString("\n"),
        )
    }

    private fun assertNoImports(layer: String, forbidden: List<String>) {
        val violations = productionFilesOf(layer).flatMap { file ->
            file.imports
                .map { it.name }
                .filter { imported -> forbidden.any { imported == it || imported.startsWith("$it.") } }
                .map { "${file.name}: $it" }
        }
        assertTrue(
            violations.isEmpty(),
            "Layer $layer must not import ${forbidden.joinToString()}\n" + violations.joinToString("\n"),
        )
    }

    private fun productionFilesOf(layer: String) =
        Konsist.scopeFromProject(sourceSetName = "main")
            .files
            .filter { file ->
                val declared = file.packagee?.name.orEmpty()
                declared == layer || declared.startsWith("$layer.")
            }

    private companion object {
        val THROW = Regex("""\bthrow\b""")
    }
}
