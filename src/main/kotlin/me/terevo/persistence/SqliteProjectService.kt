package me.terevo.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.port.*
import me.terevo.persistence.db.TerevoDatabase
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.*
import java.security.MessageDigest
import java.sql.SQLException
import java.time.Instant
import java.util.*

class SqliteProjectService(
    private val appVersion: String = "0.1.0",
    private val timestamp: () -> String = { Instant.now().toString() },
    private val backups: ProjectBackups = ProjectBackups(timestamp = timestamp),
) : ProjectService {

    override fun create(location: ProjectLocation): Outcome<OpenProject> {
        val file = Path.of(location.path)
        if (Files.exists(file)) return Outcome.Err(DomainError.Project.AlreadyExists(location.path))
        return guarded(location) {
            file.parent?.let { Files.createDirectories(it) }
            if (file.fileName.toString() == ProjectLocation.DEFAULT_FILE_NAME) {
                Files.createDirectories(file.parent.resolve(ProjectLocation.BACKUP_DIRECTORY))
                Files.createDirectories(file.parent.resolve(ProjectLocation.MEDIA_DIRECTORY))
                Files.createDirectories(file.parent.resolve(ProjectLocation.THUMBNAIL_DIRECTORY))
            }
            val lock = acquireLock(file) ?: return@guarded Outcome.Err(DomainError.Project.Locked(location.path))
            val driver = openDriver(file)
            TerevoDatabase.Schema.create(driver)
            val database = TerevoDatabase(driver)
            database.metaQueries.insert(
                schema_version = TerevoDatabase.Schema.version,
                app_version = appVersion,
                created_at = timestamp(),
            )
            Outcome.Ok(SqliteOpenProject(location, TerevoDatabase.Schema.version, driver, database, lock, this))
        }
    }

    override fun open(location: ProjectLocation): Outcome<OpenProject> {
        val file = Path.of(location.path)
        if (!Files.exists(file)) return Outcome.Err(DomainError.Project.NotFound(location.path))
        return guarded(location) {
            val lock = acquireLock(file) ?: return@guarded Outcome.Err(DomainError.Project.Locked(location.path))
            val driver = openDriver(file)
            val integrity = readIntegrity(driver)
            if (integrity != "ok") {
                driver.close()
                releaseLock(lock)
                return@guarded Outcome.Err(DomainError.Project.Corrupted(location.path, integrity))
            }
            val database = TerevoDatabase(driver)
            val meta = readMeta(database)
            if (meta == null) {
                driver.close()
                releaseLock(lock)
                return@guarded Outcome.Err(DomainError.Project.NotAProject(location.path))
            }
            if (meta > TerevoDatabase.Schema.version) {
                driver.close()
                releaseLock(lock)
                return@guarded Outcome.Err(
                    DomainError.Project.SchemaTooNew(meta, TerevoDatabase.Schema.version),
                )
            }
            backups.capture(driver, file)
            Outcome.Ok(SqliteOpenProject(location, meta, driver, database, lock, this))
        }
    }

    private fun readIntegrity(driver: JdbcSqliteDriver): String =
        driver.executeQuery(
            identifier = null,
            sql = "PRAGMA integrity_check",
            mapper = { cursor ->
                app.cash.sqldelight.db.QueryResult.Value(
                    if (cursor.next().value) cursor.getString(0).orEmpty() else "",
                )
            },
            parameters = 0,
        ).value

    private fun readMeta(database: TerevoDatabase): Long? = try {
        database.metaQueries.select().executeAsOneOrNull()?.schema_version
    } catch (missing: SQLException) {
        null
    }

    private fun acquireLock(file: Path): FileLock? {
        val lockFile = lockFileOf(file)
        return try {
            Files.createDirectories(lockFile.parent)
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            channel.tryLock() ?: run {
                channel.close()
                null
            }
        } catch (busy: OverlappingFileLockException) {
            null
        }
    }

    private inline fun guarded(
        location: ProjectLocation,
        block: () -> Outcome<OpenProject>,
    ): Outcome<OpenProject> = try {
        block()
    } catch (failure: SQLException) {
        Outcome.Err(DomainError.Project.Corrupted(location.path, failure.message.orEmpty()))
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    internal companion object {
        fun openDriver(file: Path): JdbcSqliteDriver {
            val properties = Properties().apply {
                setProperty("foreign_keys", "true")
                setProperty("journal_mode", "WAL")
                setProperty("synchronous", "NORMAL")
            }
            return JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", properties)
        }

        fun lockFileOf(file: Path): Path {
            val path = file.toAbsolutePath().normalize().toString()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(path.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte) }
            return Path.of(System.getProperty("java.io.tmpdir"), "terevo-locks", "$hash.lock")
        }

        fun releaseLock(lock: FileLock) {
            lock.release()
            lock.channel().close()
        }
    }
}

private class SqliteOpenProject(
    override val location: ProjectLocation,
    override val schemaVersion: Long,
    private val driver: JdbcSqliteDriver,
    private val database: TerevoDatabase,
    private val lock: FileLock,
    private val service: ProjectService,
) : OpenProject {

    override val repository: TreeRepository = SqlDelightTreeRepository(database)
    override val mediaRepository: MediaRepository = ProjectMediaRepository(database, Path.of(location.path))
    override val nodePositionRepository: NodePositionRepository = SqlNodePositionRepository(database)

    override fun saveAs(target: ProjectLocation): Outcome<OpenProject> {
        val targetFile = Path.of(target.path)
        val bundled = targetFile.fileName.toString() == ProjectLocation.DEFAULT_FILE_NAME
        if (Files.exists(targetFile) || bundled && Files.exists(targetFile.parent)) {
            return Outcome.Err(DomainError.Project.AlreadyExists(target.path))
        }
        val token = UUID.randomUUID().toString()
        val temporaryRoot = if (bundled) {
            targetFile.parent.resolveSibling(".${targetFile.parent.fileName}-$token.tmp")
        } else {
            targetFile.parent
        }
        val temporaryFile = if (bundled) {
            temporaryRoot.resolve(ProjectLocation.DEFAULT_FILE_NAME)
        } else {
            targetFile.resolveSibling(".${targetFile.fileName}-$token.tmp")
        }
        return try {
            temporaryFile.parent?.let { Files.createDirectories(it) }
            val escaped = temporaryFile.toAbsolutePath().toString().replace("'", "''")
            driver.execute(null, "VACUUM INTO '$escaped'", 0)
            copyMedia(temporaryFile)
            if (bundled) temporaryRoot.moveTo(targetFile.parent) else temporaryFile.moveTo(targetFile)
            close()
            service.open(target)
        } catch (failure: SQLException) {
            cleanupTemporary(bundled, temporaryRoot, temporaryFile)
            Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
        } catch (failure: IOException) {
            cleanupTemporary(bundled, temporaryRoot, temporaryFile)
            Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
        }
    }

    override fun close(): Outcome<Unit> = try {
        driver.execute(null, "PRAGMA wal_checkpoint(TRUNCATE)", 0)
        driver.close()
        SqliteProjectService.releaseLock(lock)
        Files.deleteIfExists(SqliteProjectService.lockFileOf(Path.of(location.path)))
        Outcome.Ok(Unit)
    } catch (failure: SQLException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    private fun copyMedia(targetFile: Path) {
        if (targetFile.fileName.toString() != ProjectLocation.DEFAULT_FILE_NAME) return
        val targetDirectory = targetFile.parent
        Files.createDirectories(targetDirectory.resolve(ProjectLocation.BACKUP_DIRECTORY))
        Files.createDirectories(targetDirectory.resolve(ProjectLocation.THUMBNAIL_DIRECTORY))
        val source = Path.of(location.path).parent.resolve(ProjectLocation.MEDIA_DIRECTORY)
        val target = targetDirectory.resolve(ProjectLocation.MEDIA_DIRECTORY)
        Files.createDirectories(target)
        if (!Files.exists(source)) return
        Files.walk(source).use { paths ->
            paths.forEach { path ->
                val destination = target.resolve(source.relativize(path))
                if (Files.isDirectory(path)) Files.createDirectories(destination)
                else Files.copy(path, destination)
            }
        }
    }
}

private fun Path.moveTo(target: Path) {
    try {
        Files.move(this, target, StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(this, target)
    }
}

private fun cleanupTemporary(bundled: Boolean, root: Path, file: Path) {
    if (bundled && Files.exists(root)) {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    } else {
        Files.deleteIfExists(file)
    }
}
