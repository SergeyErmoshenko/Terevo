package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.CommandBus
import me.terevo.domain.port.OpenProject
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.db.TerevoDatabase
import me.terevo.testing.person
import me.terevo.testing.shouldBeErr
import me.terevo.testing.shouldBeOk
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class SqliteProjectServiceTest {

    private lateinit var workspace: Path
    private val service = SqliteProjectService(appVersion = "0.1.0", timestamp = { "2026-08-30T10:00:00Z" })
    private val opened = mutableListOf<OpenProject>()

    @BeforeTest
    fun prepare() {
        workspace = createTempDirectory("terevo-projects")
    }

    @AfterTest
    fun cleanup() {
        opened.forEach { it.close() }
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `created project produces a file and an empty tree without visible lock sibling`() {
        val location = locationOf("family")

        val project = track(service.create(location).shouldBeOk())

        assertTrue(Files.exists(Path.of(location.path)))
        assertTrue(Files.notExists(Path.of("${location.path}.lock")))
        assertEquals(0, project.repository.load().shouldBeOk().size)
        assertEquals("family", location.displayName)
    }

    @Test
    fun `creating over an existing file is rejected`() {
        val location = locationOf("family")
        track(service.create(location).shouldBeOk()).close()

        val error = service.create(location).shouldBeErr()

        assertTrue(error is DomainError.Project.AlreadyExists)
    }

    @Test
    fun `opening a missing file is rejected`() {
        val error = service.open(locationOf("absent")).shouldBeErr()

        assertTrue(error is DomainError.Project.NotFound)
    }

    @Test
    fun `data survives closing and reopening the project`() {
        val location = locationOf("family")
        val project = service.create(location).shouldBeOk()
        val stored = person(surname = "Иванов", born = 1900)
        CommandBus(repository = project.repository).execute(AddPerson(stored)).shouldBeOk()
        project.close().shouldBeOk()

        val reopened = track(service.open(location).shouldBeOk())

        assertEquals(stored, reopened.repository.load().shouldBeOk().person(stored.id))
        assertEquals(TerevoDatabase.Schema.version, reopened.schemaVersion)
    }

    @Test
    fun `opening a project created before the events feature adds the missing tables`() {
        val location = locationOf("family")
        val project = service.create(location).shouldBeOk()
        val stored = person(surname = "Иванов", born = 1900)
        CommandBus(repository = project.repository).execute(AddPerson(stored)).shouldBeOk()
        project.close().shouldBeOk()
        val driver = SqliteProjectService.openDriver(Path.of(location.path))
        driver.execute(null, "DROP TABLE event_participant", 0)
        driver.execute(null, "DROP TABLE event", 0)
        driver.close()

        val reopened = track(service.open(location).shouldBeOk())

        assertEquals(stored, reopened.repository.load().shouldBeOk().person(stored.id))
        val added = person(surname = "Петров")
        CommandBus(repository = reopened.repository).execute(AddPerson(added)).shouldBeOk()
        assertEquals(added, reopened.repository.load().shouldBeOk().person(added.id))
    }

    @Test
    fun `a file that is not a project is reported as such`() {
        val location = locationOf("notes")
        Files.writeString(Path.of(location.path), "это не база")

        val error = service.open(location).shouldBeErr()

        assertTrue(
            error is DomainError.Project.NotAProject || error is DomainError.Project.Corrupted,
            "unexpected $error",
        )
    }

    @Test
    fun `an empty sqlite database without meta is not a project`() {
        val location = locationOf("empty")
        val driver = SqliteProjectService.openDriver(Path.of(location.path))
        driver.execute(null, "CREATE TABLE unrelated(id TEXT)", 0)
        driver.close()

        val error = service.open(location).shouldBeErr()

        assertTrue(error is DomainError.Project.NotAProject, "unexpected $error")
    }

    @Test
    fun `second open of the same file is rejected while the first is held`() {
        val location = locationOf("family")
        track(service.create(location).shouldBeOk())

        val error = service.open(location).shouldBeErr()

        assertTrue(error is DomainError.Project.Locked, "unexpected $error")
    }

    @Test
    fun `lock is released after closing`() {
        val location = locationOf("family")
        service.create(location).shouldBeOk().close().shouldBeOk()

        val reopened = track(service.open(location).shouldBeOk())

        assertNotNull(reopened)
    }

    @Test
    fun `save as copies the data and switches to the new file`() {
        val source = locationOf("family")
        val target = locationOf("family-copy")
        val project = service.create(source).shouldBeOk()
        val stored = person(surname = "Иванов")
        CommandBus(repository = project.repository).execute(AddPerson(stored)).shouldBeOk()

        val copy = track(project.saveAs(target).shouldBeOk())

        assertEquals(target, copy.location)
        assertEquals(stored, copy.repository.load().shouldBeOk().person(stored.id))
        assertTrue(Files.exists(Path.of(source.path)))
    }

    @Test
    fun `save as bundled project copies media content`() {
        val source = ProjectLocation(workspace.resolve("family").resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())
        val target =
            ProjectLocation(workspace.resolve("family-copy").resolve(ProjectLocation.DEFAULT_FILE_NAME).toString())
        val project = service.create(source).shouldBeOk()
        val mediaSource = workspace.resolve("photo.txt")
        Files.writeString(mediaSource, "photo")
        val media = project.mediaRepository.import(mediaSource.toString()).shouldBeOk()
        val originalContent = Path.of(project.mediaRepository.contentPath(media.id).shouldBeOk())

        val copy = track(project.saveAs(target).shouldBeOk())
        val copiedContent = Path.of(copy.mediaRepository.contentPath(media.id).shouldBeOk())

        assertEquals(Files.readString(originalContent), Files.readString(copiedContent))
    }

    @Test
    fun `save as over an existing file is rejected`() {
        val source = locationOf("family")
        val target = locationOf("family-copy")
        Files.writeString(Path.of(target.path), "занято")
        val project = track(service.create(source).shouldBeOk())

        val error = project.saveAs(target).shouldBeErr()

        assertTrue(error is DomainError.Project.AlreadyExists)
    }

    @Test
    fun `source project stays usable after save as reopens the copy`() {
        val source = locationOf("family")
        val target = locationOf("family-copy")
        val project = service.create(source).shouldBeOk()
        track(project.saveAs(target).shouldBeOk())

        val reopenedSource = track(service.open(source).shouldBeOk())

        assertEquals(source, reopenedSource.location)
    }

    private fun locationOf(name: String): ProjectLocation =
        ProjectLocation(workspace.resolve("$name.${ProjectLocation.EXTENSION}").toString())

    private fun track(project: OpenProject): OpenProject {
        opened.add(project)
        return project
    }
}
