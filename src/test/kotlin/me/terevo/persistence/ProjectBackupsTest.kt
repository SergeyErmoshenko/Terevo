package me.terevo.persistence

import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.CommandBus
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.testing.person
import me.terevo.testing.shouldBeOk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class ProjectBackupsTest {

    private lateinit var workspace: Path
    private var tick = 0
    private lateinit var service: SqliteProjectService

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-backups")
        service = SqliteProjectService(
            timestamp = { "2026-08-30T10:00:%02dZ".format(tick++) },
            backups = ProjectBackups(timestamp = { "2026-08-30T10:00:%02dZ".format(tick++) }),
        )
    }

    @AfterTest
    fun cleanup() {
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `creating a project makes no backup`() {
        val location = locationOf("family")

        service.create(location).shouldBeOk().close().shouldBeOk()

        assertTrue(ProjectBackups().list(Path.of(location.path)).isEmpty())
    }

    @Test
    fun `opening a project makes a backup`() {
        val location = locationOf("family")
        service.create(location).shouldBeOk().close().shouldBeOk()

        service.open(location).shouldBeOk().close().shouldBeOk()

        assertEquals(1, ProjectBackups().list(Path.of(location.path)).size)
    }

    @Test
    fun `bundled project creates media directories and stores backups inside bundle`() {
        val directory = workspace.resolve("family")
        val location = ProjectLocation(directory.resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())

        service.create(location).shouldBeOk().close().shouldBeOk()
        service.open(location).shouldBeOk().close().shouldBeOk()

        assertTrue(Files.isDirectory(directory.resolve(ProjectLocation.MEDIA_DIRECTORY)))
        assertTrue(Files.isDirectory(directory.resolve(ProjectLocation.THUMBNAIL_DIRECTORY)))
        assertEquals(directory.resolve(ProjectLocation.BACKUP_DIRECTORY), ProjectBackups.directoryOf(Path.of(location.path)))
        assertEquals(1, ProjectBackups().list(Path.of(location.path)).size)
    }

    @Test
    fun `rotation keeps only the newest five backups`() {
        val location = locationOf("family")
        service.create(location).shouldBeOk().close().shouldBeOk()

        repeat(SEVEN) { service.open(location).shouldBeOk().close().shouldBeOk() }

        val kept = ProjectBackups().list(Path.of(location.path))
        assertEquals(ProjectBackups.DEFAULT_KEEP, kept.size)
        assertTrue(kept.first().fileName.toString() < kept.last().fileName.toString())
    }

    @Test
    fun `backup holds the data that was in the project`() {
        val location = locationOf("family")
        val project = service.create(location).shouldBeOk()
        val stored = person(surname = "Иванов", born = 1900)
        CommandBus(repository = project.repository).execute(AddPerson(stored)).shouldBeOk()
        project.close().shouldBeOk()

        service.open(location).shouldBeOk().close().shouldBeOk()

        val backup = ProjectBackups().list(Path.of(location.path)).single()
        assertEquals(stored, readTree(backup).person(stored.id))
    }

    @Test
    fun `backup keeps data that was never checkpointed`() {
        val location = locationOf("family")
        val project = service.create(location).shouldBeOk()
        val bus = CommandBus(repository = project.repository)
        val stored = person(surname = "Петров")
        bus.execute(AddPerson(stored)).shouldBeOk()

        val backup = ProjectBackups(timestamp = { "wal" })
        backup.capture(SqliteProjectService.openDriver(Path.of(location.path)), Path.of(location.path))
        project.close().shouldBeOk()

        val captured = backup.list(Path.of(location.path)).single()
        assertEquals(stored, readTree(captured).person(stored.id))
    }

    private fun readTree(file: Path) = SqliteProjectService.openDriver(file).let { driver ->
        val tree = SqlDelightTreeRepository(TerevoDatabase(driver)).load().shouldBeOk()
        driver.close()
        tree
    }

    private fun locationOf(name: String): ProjectLocation =
        ProjectLocation(workspace.resolve("$name.${ProjectLocation.EXTENSION}").toString())

    private companion object {
        const val SEVEN = 7
    }
}
