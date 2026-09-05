package me.terevo.ui.events

import me.terevo.domain.model.EventDate

enum class EventType { BIRTH, WEDDING, DEATH }

data class EventRow(
    val type: EventType,
    val participants: String,
    val date: EventDate,
    val place: String,
    val daysUntilAnniversary: Int?,
    val yearsPassed: Int?,
    val thumbnailPath: String?,
)
