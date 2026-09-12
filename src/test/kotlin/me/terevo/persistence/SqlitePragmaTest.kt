package me.terevo.persistence

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.AddRelation
import me.terevo.domain.command.CommandBus
import me.terevo.domain.model.ParentChild
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SqlitePragmaTest {

    private lateinit var workspace: Path

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-pragma")
    }

    @AfterTest
    fun cleanup() {
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `project connection runs in write ahead log mode`() {
        val file = projectFile()
        SqliteProjectService().create(ProjectLocation(file.toString())).shouldBeOk().close().shouldBeOk()

        val driver = SqliteProjectService.openDriver(file)
        val mode = readText(driver, "PRAGMA journal_mode")
        driver.close()

        assertEquals("wal", mode.lowercase())
    }

    @Test
    fun `foreign keys are enforced on the project connection`() {
        val file = projectFile()
        SqliteProjectService().create(ProjectLocation(file.toString())).shouldBeOk().close().shouldBeOk()

        val driver = SqliteProjectService.openDriver(file)
        val enabled = readText(driver, "PRAGMA foreign_keys")
        driver.close()

        assertEquals("1", enabled)
    }

    @Test
    fun `deleting a person row cascades to their relations`() {
        val file = projectFile()
        val project = SqliteProjectService().create(ProjectLocation(file.toString())).shouldBeOk()
        val bus = CommandBus(repository = project.repository)
        val parent = person()
        val child = person()
        bus.execute(AddPerson(parent)).shouldBeOk()
        bus.execute(AddPerson(child)).shouldBeOk()
        bus.execute(AddRelation(ParentChild.of(parent = parent.id, child = child.id).shouldBeOk())).shouldBeOk()
        project.close().shouldBeOk()

        val driver = SqliteProjectService.openDriver(file)
        driver.execute(null, "DELETE FROM person WHERE id = '${parent.id}'", 0)
        val remaining = TerevoDatabase(driver).parentChildQueries.selectAll().executeAsList()
        driver.close()

        assertEquals(0, remaining.size)
    }

    private fun readText(driver: JdbcSqliteDriver, sql: String): String =
        driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                QueryResult.Value(if (cursor.next().value) cursor.getString(0).orEmpty() else "")
            },
            parameters = 0,
        ).value

    private fun projectFile(): Path = workspace.resolve("family.${ProjectLocation.EXTENSION}")
}
