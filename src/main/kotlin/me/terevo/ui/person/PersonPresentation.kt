package me.terevo.ui.person

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.LifeSpan
import me.terevo.ui.Strings

fun LifeSpan.displayText(): String {
    val birthText = birth.displayText()
    val deathText = death.displayText()
    return when {
        birthText == null && deathText == null -> Strings.LIFE_DATES_UNKNOWN
        deathText == null -> "Родился: $birthText"
        birthText == null -> "Умер: $deathText"
        else -> "$birthText — $deathText"
    }
}

fun LifeSpan.cardDates(): String {
    val birthDate = birth.displayText()
    val deathDate = death.displayText()
    return when {
        birthDate == null && deathDate == null -> ""
        deathDate == null -> "р. $birthDate"
        birthDate == null -> "ум. $deathDate"
        else -> "$birthDate — $deathDate"
    }
}

private fun EventDate.displayText(): String? = when (this) {
    is EventDate.Exact -> date.toDisplayDate()
    is EventDate.Approximate -> "ок. ${around.toDisplayDate()}"
    is EventDate.Range -> "${from.toDisplayDate()}–${to.toDisplayDate()}"
    EventDate.Unknown -> null
}

