package me.terevo.ui.person

import me.terevo.domain.model.EventDate
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Person

data class PersonSearchFilter(
    val query: String = "",
    val gender: Gender? = null,
    val birthYearFrom: Int? = null,
    val birthYearTo: Int? = null,
    val deathYearFrom: Int? = null,
    val deathYearTo: Int? = null,
    val place: String = "",
    val hasParents: Boolean? = null,
    val hasDates: Boolean? = null,
) {
    fun isEmpty(): Boolean = query.isBlank() && gender == null && birthYearFrom == null && birthYearTo == null &&
            deathYearFrom == null && deathYearTo == null && place.isBlank() && hasParents == null && hasDates == null
}

object PersonSearch {
    fun find(tree: FamilyTree, filter: PersonSearchFilter): List<Person> = tree.persons.values
        .asSequence()
        .filter { it.matchesText(filter.query) }
        .filter { filter.gender == null || it.gender == filter.gender }
        .filter { it.lifeSpan.birth.matchesYears(filter.birthYearFrom, filter.birthYearTo) }
        .filter { it.lifeSpan.death.matchesYears(filter.deathYearFrom, filter.deathYearTo) }
        .filter { it.matchesPlace(filter.place) }
        .filter { filter.hasParents == null || tree.parentsOf(it.id).isNotEmpty() == filter.hasParents }
        .filter { filter.hasDates == null || it.hasDates == filter.hasDates }
        .sortedBy { it.name.sortKey }
        .toList()
}

private fun Person.matchesText(query: String): Boolean {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
    if (terms.isEmpty()) return true
    val text = searchText()
    return terms.all(text::contains)
}

// Lower-cased text the people search matches against; the people table reuses it so both searches agree.
fun Person.searchText(): String = buildString {
    append(name.display)
    append(' ')
    append(birthPlace?.title.orEmpty())
    append(' ')
    append(deathPlace?.title.orEmpty())
    append(' ')
    append(notes)
    customFields.forEach { (key, value) ->
        append(' ')
        append(key)
        append(' ')
        append(value)
    }
}.lowercase()

private val Person.hasDates: Boolean
    get() = lifeSpan.birth != EventDate.Unknown || lifeSpan.death != EventDate.Unknown

private fun EventDate.matchesYears(from: Int?, to: Int?): Boolean {
    if (from == null && to == null) return true
    val years = years() ?: return false
    return (from == null || years.last >= from) && (to == null || years.first <= to)
}

private fun Person.matchesPlace(place: String): Boolean {
    val normalized = place.trim().lowercase()
    if (normalized.isEmpty()) return true
    return birthPlace?.title.orEmpty().lowercase().contains(normalized) ||
            deathPlace?.title.orEmpty().lowercase().contains(normalized)
}

private fun EventDate.years(): IntRange? = when (this) {
    is EventDate.Exact -> date.year..date.year
    is EventDate.Approximate -> around.year..around.year
    is EventDate.Range -> from.year..to.year
    EventDate.Unknown -> null
}
