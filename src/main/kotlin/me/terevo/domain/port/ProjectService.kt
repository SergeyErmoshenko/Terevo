package me.terevo.domain.port

import me.terevo.domain.Outcome

@JvmInline
value class ProjectLocation(val path: String) {
    val fileName: String
        get() = path.substringAfterLast('/').substringAfterLast('\\')

    val displayName: String
        get() = if (fileName == DEFAULT_FILE_NAME) {
            path.removeSuffix(fileName).trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        } else {
            fileName.removeSuffix(".$EXTENSION")
        }

    override fun toString(): String = path

    companion object {
        const val EXTENSION: String = "terevo"
        const val DEFAULT_FILE_NAME: String = "project.$EXTENSION"
        const val BACKUP_DIRECTORY: String = "backups"
        const val MEDIA_DIRECTORY: String = "media"
        const val THUMBNAIL_DIRECTORY: String = "thumbnails"
    }
}

interface OpenProject {
    val location: ProjectLocation
    val schemaVersion: Long
    val repository: TreeRepository
    val mediaRepository: MediaRepository get() = MediaRepository.NONE

    fun saveAs(target: ProjectLocation): Outcome<OpenProject>

    fun close(): Outcome<Unit>
}

interface ProjectService {
    fun create(location: ProjectLocation): Outcome<OpenProject>

    fun open(location: ProjectLocation): Outcome<OpenProject>
}
