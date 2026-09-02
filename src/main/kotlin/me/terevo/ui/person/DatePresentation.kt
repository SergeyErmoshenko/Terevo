package me.terevo.ui.person

import kotlinx.datetime.LocalDate

fun LocalDate.toDisplayDate(): String = "%02d.%02d.%04d".format(day, month.ordinal + 1, year)

fun parseDisplayDate(value: String): LocalDate {
    val match = DATE_PATTERN.matchEntire(value.trim()) ?: throw IllegalArgumentException()
    val (day, month, year) = match.destructured
    return LocalDate(year.toInt(), month.toInt(), day.toInt())
}

private val DATE_PATTERN: Regex = Regex("(\\d{2})\\.(\\d{2})\\.(\\d{4})")
