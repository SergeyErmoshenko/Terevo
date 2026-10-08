package me.terevo.persistence

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.port.MediaRepository
import me.terevo.domain.port.ProjectLocation
import me.terevo.persistence.db.TerevoDatabase
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import javax.imageio.ImageIO

class ProjectMediaRepository(
    private val database: TerevoDatabase,
    projectFile: Path,
) : MediaRepository {
    private val projectDirectory = projectFile.parent
    private val mediaDirectory = projectDirectory.resolve(ProjectLocation.MEDIA_DIRECTORY)
    private val thumbnailDirectory = projectDirectory.resolve(ProjectLocation.THUMBNAIL_DIRECTORY)

    override fun import(sourcePath: String): Outcome<Media> = guarded {
        val source = Path.of(sourcePath)
        val hash = source.sha256()
        val existing = database.mediaQueries.selectMediaBySha(hash).executeAsOneOrNull()
        if (existing != null) return@guarded Outcome.Ok(existing.toMedia())
        val media = Media(
            id = MediaId.next(),
            fileName = source.fileName.toString(),
            mimeType = mimeTypeOf(source),
            sizeBytes = Files.size(source),
            sha256 = hash,
        )
        val target = contentPath(hash)
        Files.createDirectories(target.parent)
        val temporary = Files.createTempFile(target.parent, ".import-", ".tmp")
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING)
            database.transaction {
                database.mediaQueries.insertMedia(
                    id = media.id.toString(),
                    file_name = media.fileName,
                    mime_type = media.mimeType,
                    size_bytes = media.sizeBytes,
                    sha256 = media.sha256,
                )
                if (!Files.exists(target)) temporary.moveTo(target)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        Outcome.Ok(media)
    }

    override fun find(id: MediaId): Outcome<Media> = guarded {
        val row = database.mediaQueries.selectMediaById(id.toString()).executeAsOneOrNull()
            ?: return@guarded Outcome.Err(DomainError.Storage.Failure("Медиа не найдено"))
        Outcome.Ok(row.toMedia())
    }

    override fun contentPath(id: MediaId): Outcome<String> = when (val media = find(id)) {
        is Outcome.Ok -> Outcome.Ok(contentPath(media.value.sha256).toString())
        is Outcome.Err -> media
    }

    override fun thumbnailPath(id: MediaId): Outcome<String?> = guarded {
        val media = when (val found = find(id)) {
            is Outcome.Ok -> found.value
            is Outcome.Err -> return@guarded found
        }
        if (!media.mimeType.startsWith("image/")) return@guarded Outcome.Ok(null)
        val target = thumbnailDirectory.resolve("${media.sha256}.png")
        if (!Files.exists(target)) {
            val source = contentPath(media.sha256)
            val image = ImageIO.read(source.toFile()) ?: return@guarded Outcome.Ok(null)
            Files.createDirectories(target.parent)
            ImageIO.write(image.thumbnail(), "png", target.toFile())
        }
        Outcome.Ok(target.toString())
    }

    override fun deleteIfUnused(id: MediaId): Outcome<Unit> = guarded {
        if (database.mediaQueries.countAttachmentsOfMedia(id.toString()).executeAsOne() > 0L) {
            return@guarded Outcome.Ok(Unit)
        }
        val media = when (val found = find(id)) {
            is Outcome.Ok -> found.value
            is Outcome.Err -> return@guarded Outcome.Ok(Unit)
        }
        database.mediaQueries.deleteMedia(id.toString())
        Files.deleteIfExists(contentPath(media.sha256))
        Files.deleteIfExists(thumbnailDirectory.resolve("${media.sha256}.png"))
        Outcome.Ok(Unit)
    }

    private fun contentPath(sha256: String): Path = mediaDirectory.resolve(sha256.take(2)).resolve(sha256)

    private inline fun <T> guarded(block: () -> Outcome<T>): Outcome<T> = try {
        block()
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    } catch (failure: IllegalArgumentException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }
}

private fun Path.moveTo(target: Path) {
    try {
        Files.move(this, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(this, target)
    }
}

private fun Path.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(this).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun BufferedImage.thumbnail(): BufferedImage {
    val scale = minOf(1.0, THUMBNAIL_SIZE.toDouble() / maxOf(width, height))
    val targetWidth = maxOf(1, (width * scale).toInt())
    val targetHeight = maxOf(1, (height * scale).toInt())
    val target = BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB)
    val graphics = target.createGraphics()
    try {
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        graphics.drawImage(this, 0, 0, targetWidth, targetHeight, null)
    } finally {
        graphics.dispose()
    }
    return target
}

private fun me.terevo.persistence.db.Media.toMedia(): Media = Media(
    id = MediaId.parse(id),
    fileName = file_name,
    // Rows imported before the extension table existed may carry application/octet-stream.
    mimeType = KNOWN_MIME_TYPES[file_name.substringAfterLast('.', "").lowercase()] ?: mime_type,
    sizeBytes = size_bytes,
    sha256 = sha256,
)

private const val THUMBNAIL_SIZE: Int = 256

// Files.probeContentType depends on the OS registry and often answers null for PDF/Office files
// (notably on macOS), which would make them unopenable, so well-known extensions win.
private val KNOWN_MIME_TYPES = mapOf(
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "odt" to "application/vnd.oasis.opendocument.text",
    "rtf" to "application/rtf",
    "txt" to "text/plain",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "bmp" to "image/bmp",
    "webp" to "image/webp",
)

internal fun mimeTypeOf(source: Path): String {
    val extension = source.fileName.toString().substringAfterLast('.', "").lowercase()
    return KNOWN_MIME_TYPES[extension] ?: Files.probeContentType(source) ?: "application/octet-stream"
}
