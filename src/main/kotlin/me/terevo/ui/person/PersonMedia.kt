package me.terevo.ui.person

import me.terevo.domain.Outcome
import me.terevo.domain.model.Media
import me.terevo.domain.model.Person
import me.terevo.domain.port.MediaRepository

fun MediaRepository.imagePathOf(media: Media): String? {
    if (!media.mimeType.startsWith("image/")) return null
    val thumbnail = thumbnailPath(media.id)
    if (thumbnail is Outcome.Ok && thumbnail.value != null) return thumbnail.value
    val content = contentPath(media.id)
    return if (content is Outcome.Ok) content.value else null
}

fun Person.mainPhotoPath(mediaRepository: MediaRepository): String? =
    mediaIds.firstNotNullOfOrNull { mediaId ->
        (mediaRepository.find(mediaId) as? Outcome.Ok)?.value?.let(mediaRepository::imagePathOf)
    }
