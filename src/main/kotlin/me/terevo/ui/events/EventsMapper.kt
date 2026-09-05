package me.terevo.ui.events

import kotlinx.datetime.*
import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.isKnown
import me.terevo.domain.port.MediaRepository
import me.terevo.ui.person.mainPhotoPath

fun mapEventRows(tree: FamilyTree, mediaRepository: MediaRepository, today: LocalDate): List<EventRow> {
    val rows = mutableListOf<EventRow>()
    for (person in tree.persons.values) {
        if (person.lifeSpan.birth.isKnown) {
            rows += EventRow(
                type = EventType.BIRTH,
                participants = person.name.display,
                date = person.lifeSpan.birth,
                place = person.birthPlace?.title.orEmpty(),
                daysUntilAnniversary = person.lifeSpan.birth.anniversaryDays(today),
                yearsPassed = person.lifeSpan.birth.yearsPassed(today),
                thumbnailPath = person.mainPhotoPath(mediaRepository),
            )
        }
        if (person.lifeSpan.death.isKnown) {
            rows += EventRow(
                type = EventType.DEATH,
                participants = person.name.display,
                date = person.lifeSpan.death,
                place = person.deathPlace?.title.orEmpty(),
                daysUntilAnniversary = person.lifeSpan.death.anniversaryDays(today),
                yearsPassed = person.lifeSpan.death.yearsPassed(today),
                thumbnailPath = person.mainPhotoPath(mediaRepository),
            )
        }
    }
    for (relation in tree.relations.values) {
        if (relation !is Marriage) continue
        if (!relation.since.isKnown) continue
        val spouseA = tree.person(relation.spouseA)
        val spouseB = tree.person(relation.spouseB)
        val participants = listOfNotNull(spouseA?.name?.display, spouseB?.name?.display).joinToString(" и ")
        rows += EventRow(
            type = EventType.WEDDING,
            participants = participants,
            date = relation.since,
            place = relation.place?.title.orEmpty(),
            daysUntilAnniversary = relation.since.anniversaryDays(today),
            yearsPassed = relation.since.yearsPassed(today),
            thumbnailPath = spouseA?.mainPhotoPath(mediaRepository),
        )
    }
    return rows.sortedBy { it.daysUntilAnniversary ?: Int.MAX_VALUE }
}

private fun EventDate.yearsPassed(today: LocalDate): Int? {
    val exact = this as? EventDate.Exact ?: return null
    return exact.date.yearsUntil(today)
}

private fun EventDate.anniversaryDays(today: LocalDate): Int? {
    val exact = this as? EventDate.Exact ?: return null
    val next = nextAnniversary(exact.date, today)
    return today.daysUntil(next)
}

private fun nextAnniversary(date: LocalDate, today: LocalDate): LocalDate {
    val candidate = dateInYear(date, today.year)
    return if (candidate < today) dateInYear(date, today.year + 1) else candidate
}

private fun dateInYear(date: LocalDate, year: Int): LocalDate {
    val lastDayOfMonth = LocalDate(year, date.month, 1).plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY)
    val day = minOf(date.day, lastDayOfMonth.day)
    return LocalDate(year, date.month, day)
}
