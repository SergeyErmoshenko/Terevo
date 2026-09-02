package me.terevo.persistence

import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import me.terevo.domain.Outcome
import me.terevo.domain.port.ProjectLocation
import me.terevo.testing.shouldBeOk

class ProjectMediaRepositoryTest {
    private val workspace: Path = createTempDirectory("terevo-media")

    @AfterTest
    fun cleanup() {
        Files.walk(workspace).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    @Test
    fun `import stores content by hash and deduplicates metadata`() {
        val project = createProject()
        val source = workspace.resolve("photo.txt")
        Files.writeString(source, "same content")

        val first = project.mediaRepository.import(source.toString()).shouldBeOk()
        val second = project.mediaRepository.import(source.toString()).shouldBeOk()
        val content = Path.of(project.mediaRepository.contentPath(first.id).shouldBeOk())

        assertEquals(first, second)
        assertEquals(first.sha256.take(2), content.parent.fileName.toString())
        assertEquals("same content", Files.readString(content))
        project.close().shouldBeOk()
    }

    @Test
    fun `image thumbnail is generated lazily and unused media is removed`() {
        val project = createProject()
        val source = workspace.resolve("photo.png")
        ImageIO.write(BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB), "png", source.toFile())
        val media = project.mediaRepository.import(source.toString()).shouldBeOk()
        val content = Path.of(project.mediaRepository.contentPath(media.id).shouldBeOk())

        val thumbnail = Path.of(assertNotNull(project.mediaRepository.thumbnailPath(media.id).shouldBeOk()))

        assertTrue(Files.exists(thumbnail))
        assertTrue(ImageIO.read(thumbnail.toFile()).width <= 256)
        project.mediaRepository.deleteIfUnused(media.id).shouldBeOk()
        assertTrue(Files.notExists(content))
        assertTrue(Files.notExists(thumbnail))
        project.close().shouldBeOk()
    }

    private fun createProject() = SqliteProjectService().create(
        ProjectLocation(workspace.resolve("family").resolve(ProjectLocation.DEFAULT_FILE_NAME).toString()),
    ).let { (it as Outcome.Ok).value }
}
