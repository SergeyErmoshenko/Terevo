package me.terevo.persistence

import kotlinx.datetime.LocalDate
import me.terevo.domain.getOrNull
import me.terevo.domain.model.DatePrecision
import me.terevo.domain.model.EventDate

data class EncodedDate(
    val kind: String,
    val value: String?,
    val end: String?,
    val precision: String?,
)

object EventDateCodec {
    private const val EXACT = "EXACT"
    private const val APPROXIMATE = "APPROXIMATE"
    private const val RANGE = "RANGE"
    private const val UNKNOWN = "UNKNOWN"

    fun encode(date: EventDate): EncodedDate = when (date) {
        is EventDate.Exact -> EncodedDate(EXACT, date.date.toString(), null, null)
        is EventDate.Approximate -> EncodedDate(APPROXIMATE, date.around.toString(), null, date.precision.name)
        is EventDate.Range -> EncodedDate(RANGE, date.from.toString(), date.to.toString(), null)
        EventDate.Unknown -> EncodedDate(UNKNOWN, null, null, null)
    }

    fun decode(encoded: EncodedDate): EventDate? = when (encoded.kind) {
        EXACT -> parseDate(encoded.value)?.let { EventDate.Exact(it) }
        APPROXIMATE -> decodeApproximate(encoded)
        RANGE -> decodeRange(encoded)
        UNKNOWN -> EventDate.Unknown
        else -> null
    }

    private fun decodeApproximate(encoded: EncodedDate): EventDate? {
        val around = parseDate(encoded.value) ?: return null
        val precision = DatePrecision.entries.firstOrNull { it.name == encoded.precision } ?: return null
        return EventDate.Approximate(around, precision)
    }

    private fun decodeRange(encoded: EncodedDate): EventDate? {
        val from = parseDate(encoded.value) ?: return null
        val to = parseDate(encoded.end) ?: return null
        return EventDate.Range.of(from, to).getOrNull()
    }

    private fun parseDate(text: String?): LocalDate? {
        if (text == null) return null
        return try {
            LocalDate.parse(text)
        } catch (invalid: IllegalArgumentException) {
            null
        }
    }
}
