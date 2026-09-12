package me.terevo.persistence

import me.terevo.domain.port.ProjectLocation
import me.terevo.testing.shouldBeOk
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class CrashRecoveryTest {

    private lateinit var workspace: Path

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-crash")
    }

    @AfterTest
    fun cleanup() {
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `every confirmed command survives a killed process`() {
        repeat(ITERATIONS) { iteration ->
            val location = ProjectLocation(workspace.resolve("crash$iteration.terevo").toString())
            val confirmed = writeUntilKilled(location)

            val project = SqliteProjectService().open(location).shouldBeOk()
            val stored = project.repository.load().shouldBeOk().size
            project.close().shouldBeOk()

            assertTrue(confirmed >= COMMANDS_BEFORE_KILL, "child reported only $confirmed commands")
            assertTrue(
                stored >= confirmed,
                "lost data: child confirmed $confirmed, database holds $stored",
            )
            assertTrue(
                stored <= confirmed + 1,
                "database holds $stored while the child confirmed only $confirmed",
            )
        }
    }

    @Test
    fun `database stays readable after a kill`() {
        val location = ProjectLocation(workspace.resolve("integrity.terevo").toString())
        writeUntilKilled(location)

        val project = SqliteProjectService().open(location).shouldBeOk()

        assertEquals(0, project.repository.load().shouldBeOk().relations.size)
        project.close().shouldBeOk()
    }

    private fun writeUntilKilled(location: ProjectLocation): Int {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val process = ProcessBuilder(
            java,
            "-cp",
            System.getProperty("java.class.path"),
            "-Dorg.sqlite.tmpdir=" + System.getProperty("org.sqlite.tmpdir", System.getProperty("java.io.tmpdir")),
            HARNESS,
            location.path,
        ).redirectErrorStream(false).start()

        val confirmed = process.inputStream.bufferedReader().use { reader ->
            countConfirmed(reader, process)
        }
        process.outputStream.bufferedWriter().use { writer ->
            writer.write("crash")
            writer.newLine()
            writer.flush()
        }
        assertTrue(
            process.waitFor(KILL_TIMEOUT_SECONDS, TimeUnit.SECONDS),
            "child did not halt after receiving the crash signal",
        )
        return confirmed
    }

    private fun countConfirmed(reader: BufferedReader, process: Process): Int {
        var confirmed = 0
        while (confirmed < COMMANDS_BEFORE_KILL) {
            val line = reader.readLine() ?: break
            assertTrue(!line.startsWith("FAILED"), "child failed: $line")
            confirmed = line.toInt() + 1
        }
        assertTrue(process.isAlive, "child exited before it could be killed")
        return confirmed
    }

    private companion object {
        const val HARNESS = "me.terevo.testing.CrashHarnessKt"
        const val ITERATIONS = 5
        const val COMMANDS_BEFORE_KILL = 50
        const val KILL_TIMEOUT_SECONDS = 10L
    }
}
