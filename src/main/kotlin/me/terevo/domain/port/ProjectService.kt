package me.terevo.domain.port

import me.terevo.domain.Outcome

@JvmInline
value class ProjectLocation(val path: String) {
    val fileName: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')

    val displayName: String
        get() = fileName.removeSuffix(".$EXTENSION")

    override fun toString(): String = path

    companion object {
        const val EXTENSION: String = "terevo"
    }
}

interface OpenProject {
    val location: ProjectLocation
    val schemaVersion: Long
    val repository: TreeRepository

    fun saveAs(target: ProjectLocation): Outcome<OpenProject>

    fun close(): Outcome<Unit>
}

interface ProjectService {
    fun create(location: ProjectLocation): Outcome<OpenProject>

    fun open(location: ProjectLocation): Outcome<OpenProject>
}
