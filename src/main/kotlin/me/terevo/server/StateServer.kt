package me.terevo.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.terevo.app.AppController
import me.terevo.app.PREVIEW_DIRECTORY
import me.terevo.app.ProjectDirectories
import me.terevo.layout.Layout
import me.terevo.ui.tree.TreeVisuals
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

@Serializable
data class Request(val id: Long, val action: Action)

@Serializable
data class Hello(val projectsRoot: String, val previewDirectory: String)

@Serializable
data class Response(
    val id: Long,
    val state: AppStateDto? = null,
    val error: String? = null,
    val hello: Hello? = null,
)

// One request line in, one response line out. Holds the last layout it sent so unchanged layouts
// (most actions) are not re-serialized: the frontend keeps the previous one by layoutVersion.
class StateServer(
    private val controller: AppController,
    private val directories: ProjectDirectories = ProjectDirectories(),
) {
    private var sentLayout: Layout? = null
    private var sentVisuals: TreeVisuals? = null
    private var layoutVersion = 0L

    fun hello(): String = json.encodeToString(
        Response(
            id = 0,
            state = snapshot(),
            hello = Hello(
                projectsRoot = directories.root.toString(),
                previewDirectory = Path.of(System.getProperty("java.io.tmpdir"), PREVIEW_DIRECTORY).toString(),
            ),
        ),
    )

    fun snapshotResponse(id: Long): String = json.encodeToString(Response(id = id, state = snapshot()))

    fun handle(line: String): String {
        val request = try {
            json.decodeFromString<Request>(line)
        } catch (error: SerializationException) {
            logger.error(error) { "Malformed request" }
            return json.encodeToString(Response(id = -1, error = "Malformed request: ${error.message}"))
        } catch (error: IllegalArgumentException) {
            logger.error(error) { "Malformed request" }
            return json.encodeToString(Response(id = -1, error = "Malformed request: ${error.message}"))
        }
        return try {
            controller.dispatch(request.action, directories)
            json.encodeToString(Response(id = request.id, state = snapshot()))
        } catch (error: RuntimeException) {
            logger.error(error) { "Action failed: ${request.action}" }
            json.encodeToString(Response(id = request.id, state = snapshot(), error = error.message ?: error.toString()))
        }
    }

    private fun snapshot(): AppStateDto {
        val state = controller.state
        val changed = state.canvas.layout !== sentLayout || state.canvas.visuals !== sentVisuals
        if (changed) {
            layoutVersion++
            sentLayout = state.canvas.layout
            sentVisuals = state.canvas.visuals
        }
        val projectDirectory = controller.projectPath?.let { Path.of(it).parent?.toString() }
        return state.toDto(projectDirectory, layoutVersion, includeLayout = changed)
    }

    companion object {
        val json = Json {
            classDiscriminator = "type"
            encodeDefaults = true
            explicitNulls = true
            ignoreUnknownKeys = true
        }
    }
}
