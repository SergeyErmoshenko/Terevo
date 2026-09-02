package me.terevo.domain.port

import me.terevo.domain.Outcome
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId

interface MediaRepository {
    fun import(sourcePath: String): Outcome<Media>

    fun find(id: MediaId): Outcome<Media>

    fun contentPath(id: MediaId): Outcome<String>

    fun thumbnailPath(id: MediaId): Outcome<String?>

    fun deleteIfUnused(id: MediaId): Outcome<Unit>

    companion object {
        val NONE: MediaRepository = object : MediaRepository {
            private val error = Outcome.Err(me.terevo.domain.DomainError.Storage.Failure("Медиа недоступны"))

            override fun import(sourcePath: String): Outcome<Media> = error
            override fun find(id: MediaId): Outcome<Media> = error
            override fun contentPath(id: MediaId): Outcome<String> = error
            override fun thumbnailPath(id: MediaId): Outcome<String?> = error
            override fun deleteIfUnused(id: MediaId): Outcome<Unit> = error
        }
    }
}
