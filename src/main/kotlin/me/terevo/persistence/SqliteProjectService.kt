package me.terevo.persistence

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.sql.SQLException
import java.time.Instant
import java.util.Properties
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.port.OpenProject
import me.terevo.domain.port.ProjectLocation
import me.terevo.domain.port.ProjectService
import me.terevo.domain.port.TreeRepository
import me.terevo.persistence.db.TerevoDatabase

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

        fun lockFileOf(file: Path): Path = file.resolveSibling("${file.fileName}.lock")

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

    override fun saveAs(target: ProjectLocation): Outcome<OpenProject> {
        val targetFile = Path.of(target.path)
        if (Files.exists(targetFile)) return Outcome.Err(DomainError.Project.AlreadyExists(target.path))
        return try {
            targetFile.parent?.let { Files.createDirectories(it) }
            val escaped = targetFile.toAbsolutePath().toString().replace("'", "''")
            driver.execute(null, "VACUUM INTO '$escaped'", 0)
            close()
            service.open(target)
        } catch (failure: SQLException) {
            Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
        } catch (failure: IOException) {
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
}
