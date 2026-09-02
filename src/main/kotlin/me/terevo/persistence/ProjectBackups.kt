package me.terevo.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.time.Instant
import kotlin.streams.asSequence
import me.terevo.domain.port.ProjectLocation

private val logger = KotlinLogging.logger {}

class ProjectBackups(
    private val keep: Int = DEFAULT_KEEP,
    private val timestamp: () -> String = { Instant.now().toString() },
) {
    fun capture(driver: JdbcSqliteDriver, project: Path) {
        try {
            val directory = directoryOf(project)
            Files.createDirectories(directory)
            val target = freeName(directory, project)
            val escaped = target.toAbsolutePath().toString().replace("'", "''")
            driver.execute(null, "VACUUM INTO '$escaped'", 0)
            rotate(directory)
        } catch (failure: SQLException) {
            logger.warn(failure) { "backup of $project skipped" }
        } catch (failure: IOException) {
            logger.warn(failure) { "backup of $project skipped" }
        }
    }

    fun list(project: Path): List<Path> {
        val directory = directoryOf(project)
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.asSequence().sortedBy { it.fileName.toString() }.toList()
        }
    }

    private fun rotate(directory: Path) {
        val existing = Files.list(directory).use { stream ->
            stream.asSequence().sortedBy { it.fileName.toString() }.toList()
        }
        existing.dropLast(keep).forEach { Files.deleteIfExists(it) }
    }

    private fun freeName(directory: Path, project: Path): Path {
        val base = project.fileName.toString().substringBeforeLast('.')
        val stamp = timestamp().replace(':', '-')
        var candidate = directory.resolve("$base-$stamp.${ProjectLocation.EXTENSION}")
        var counter = 1
        while (Files.exists(candidate)) {
            candidate = directory.resolve("$base-$stamp-$counter.${ProjectLocation.EXTENSION}")
            counter++
        }
        return candidate
    }

    companion object {
        const val DEFAULT_KEEP: Int = 5

        fun directoryOf(project: Path): Path = if (project.fileName.toString() == ProjectLocation.DEFAULT_FILE_NAME) {
            project.parent.resolve(ProjectLocation.BACKUP_DIRECTORY)
        } else {
            project.resolveSibling("${project.fileName}.backup")
        }
    }
}
