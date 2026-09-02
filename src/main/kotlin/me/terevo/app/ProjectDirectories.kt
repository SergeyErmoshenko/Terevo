package me.terevo.app

import java.nio.file.Path
import me.terevo.domain.port.ProjectLocation

class ProjectDirectories(
    private val documents: Path = Path.of(System.getProperty("user.home"), "Documents"),
) {
    val root: Path get() = documents.resolve("Terevo")

    fun location(projectName: String): ProjectLocation = ProjectLocation(
        root.resolve(projectName).resolve(ProjectLocation.DEFAULT_FILE_NAME).toString(),
    )
}
