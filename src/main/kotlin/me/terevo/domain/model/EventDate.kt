package me.terevo.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

enum class DatePrecision {
    DAY,
    MONTH,
    YEAR,
    DECADE,
}

data class DateInterval(val from: LocalDate, val to: LocalDate)

sealed interface EventDate {

    data class Exact(val date: LocalDate) : EventDate

    data class Approximate(val around: LocalDate, val precision: DatePrecision) : EventDate

    class Range private constructor(val from: LocalDate, val to: LocalDate) : EventDate {
        override fun equals(other: Any?): Boolean = other is Range && from == other.from && to == other.to

        override fun hashCode(): Int = from.hashCode() * 31 + to.hashCode()

        override fun toString(): String = "Range($from..$to)"

        companion object {
            fun of(from: LocalDate, to: LocalDate): Outcome<Range> =
                if (from > to) {
                    Outcome.Err(DomainError.Date.RangeReversed(from, to))
                } else {
                    Outcome.Ok(Range(from, to))
                }
        }
    }

    data object Unknown : EventDate
}

val EventDate.interval: DateInterval?
    get() = when (this) {
        is EventDate.Exact -> DateInterval(date, date)
        is EventDate.Approximate -> intervalOf(around, precision)
        is EventDate.Range -> DateInterval(from, to)
        EventDate.Unknown -> null
    }

val EventDate.isKnown: Boolean get() = this !is EventDate.Unknown

fun EventDate.definitelyBefore(other: EventDate): Boolean {
    val own = interval ?: return false
    val theirs = other.interval ?: return false
    return own.to < theirs.from
}

fun EventDate.overlaps(other: EventDate): Boolean {
    val own = interval ?: return false
    val theirs = other.interval ?: return false
    return own.from <= theirs.to && theirs.from <= own.to
}

private fun intervalOf(around: LocalDate, precision: DatePrecision): DateInterval = when (precision) {
    DatePrecision.DAY -> DateInterval(around, around)
    DatePrecision.MONTH -> DateInterval(
        from = LocalDate(around.year, around.month, 1),
        to = LocalDate(around.year, around.month, 1)
            .plus(1, DateTimeUnit.MONTH)
            .minus(1, DateTimeUnit.DAY),
    )

    DatePrecision.YEAR -> DateInterval(
        from = LocalDate(around.year, 1, 1),
        to = LocalDate(around.year, 12, 31),
    )

    DatePrecision.DECADE -> {
        val start = around.year - around.year.mod(DECADE)
        DateInterval(
            from = LocalDate(start, 1, 1),
            to = LocalDate(start + DECADE - 1, 12, 31),
        )
    }
}

private const val DECADE = 10
