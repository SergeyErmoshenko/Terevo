package me.terevo.persistence

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.terevo.domain.port.ProjectLocation
import me.terevo.domain.port.UserSettings
import me.terevo.testing.shouldBeOk

class JsonSettingsStoreTest {

    private lateinit var directory: Path
    private lateinit var store: JsonSettingsStore

    @BeforeTest
    fun prepare() {
        directory = createTempDirectory("terevo-settings")
        store = JsonSettingsStore(directory)
    }

    @AfterTest
    fun cleanup() {
        Files.walk(directory).sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
    }

    @Test
    fun `missing settings file yields empty settings`() {
        assertEquals(UserSettings.EMPTY, store.load().shouldBeOk())
    }

    @Test
    fun `settings survive a round trip`() {
        val settings = UserSettings(listOf(ProjectLocation("/tmp/a.terevo"), ProjectLocation("/tmp/b.terevo")))

        store.save(settings).shouldBeOk()

        assertEquals(settings, store.load().shouldBeOk())
    }

    @Test
    fun `broken settings file degrades to empty settings`() {
        Files.writeString(directory.resolve("settings.json"), "{ это не json")

        assertEquals(UserSettings.EMPTY, store.load().shouldBeOk())
    }

    @Test
    fun `recent project moves to the front without duplicates`() {
        val first = ProjectLocation("/tmp/a.terevo")
        val second = ProjectLocation("/tmp/b.terevo")

        val settings = UserSettings.EMPTY.withRecent(first).withRecent(second).withRecent(first)

        assertEquals(listOf(first, second), settings.recentProjects)
    }

    @Test
    fun `recent list is bounded`() {
        val settings = (1..15).fold(UserSettings.EMPTY) { acc, index ->
            acc.withRecent(ProjectLocation("/tmp/$index.terevo"))
        }

        assertEquals(UserSettings.RECENT_LIMIT, settings.recentProjects.size)
        assertEquals(ProjectLocation("/tmp/15.terevo"), settings.recentProjects.first())
    }

    @Test
    fun `project can be dropped from the recent list`() {
        val location = ProjectLocation("/tmp/a.terevo")

        val settings = UserSettings.EMPTY.withRecent(location).withoutRecent(location)

        assertTrue(settings.recentProjects.isEmpty())
    }
}
