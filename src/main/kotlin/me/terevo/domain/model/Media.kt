package me.terevo.domain.model

data class Media(
    val id: MediaId,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
)
