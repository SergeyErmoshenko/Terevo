package me.terevo.app

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import me.terevo.domain.port.ProjectLocation

class ProjectDirectoriesTest {

    @Test
    fun `project location uses portable bundle under Documents Terevo`() {
        val documents = Path.of("home", "Documents")
        val directories = ProjectDirectories(documents)

        val location = directories.location("family")

        assertEquals(
            documents.resolve("Terevo").resolve("family").resolve(ProjectLocation.DEFAULT_FILE_NAME).toString(),
            location.path,
        )
    }
}
