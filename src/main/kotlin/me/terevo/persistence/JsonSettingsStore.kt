package me.terevo.persistence

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.port.ProjectLocation
import me.terevo.domain.port.SettingsStore
import me.terevo.domain.port.ThemeMode
import me.terevo.domain.port.UserSettings
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Serializable
private data class SettingsDocument(
    val recentProjects: List<String> = emptyList(),
    val themeMode: String = ThemeMode.SYSTEM.name,
)

class JsonSettingsStore(private val directory: Path) : SettingsStore {

    private val file: Path get() = directory.resolve(FILE_NAME)

    override fun load(): Outcome<UserSettings> = try {
        if (!Files.exists(file)) {
            Outcome.Ok(UserSettings.EMPTY)
        } else {
            val document = json.decodeFromString<SettingsDocument>(Files.readString(file))
            val themeMode = runCatching { ThemeMode.valueOf(document.themeMode) }.getOrDefault(ThemeMode.SYSTEM)
            Outcome.Ok(UserSettings(document.recentProjects.map { ProjectLocation(it) }, themeMode))
        }
    } catch (broken: SerializationException) {
        Outcome.Ok(UserSettings.EMPTY)
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    override fun save(settings: UserSettings): Outcome<Unit> = try {
        Files.createDirectories(directory)
        val document = SettingsDocument(settings.recentProjects.map { it.path }, settings.themeMode.name)
        val temporary = directory.resolve("$FILE_NAME.tmp")
        Files.writeString(temporary, json.encodeToString(document))
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        Outcome.Ok(Unit)
    } catch (failure: IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    private companion object {
        const val FILE_NAME = "settings.json"

        val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    }
}
