package me.terevo.ui.events

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.EventId

data class EventRow(
    val id: EventId?,
    val type: String,
    val participants: String,
    val date: EventDate,
    val place: String,
    val daysUntilAnniversary: Int?,
    val yearsPassed: Int?,
    val thumbnailPath: String?,
)
