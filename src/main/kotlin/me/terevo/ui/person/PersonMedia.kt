package me.terevo.ui.person

import me.terevo.domain.Outcome
import me.terevo.domain.model.Person
import me.terevo.domain.port.MediaRepository

fun Person.mainPhotoPath(mediaRepository: MediaRepository): String? {
    for (mediaId in mediaIds) {
        val media = (mediaRepository.find(mediaId) as? Outcome.Ok)?.value ?: continue
        if (!media.mimeType.startsWith("image/")) continue
        val thumbnail = mediaRepository.thumbnailPath(mediaId)
        if (thumbnail is Outcome.Ok && thumbnail.value != null) return thumbnail.value
        val content = mediaRepository.contentPath(mediaId)
        if (content is Outcome.Ok) return content.value
    }
    return null
}
