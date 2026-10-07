package me.terevo.ui.person

import me.terevo.domain.Outcome
import me.terevo.domain.flatMap
import me.terevo.domain.model.*

enum class RelationMode {
    PARENT,
    CHILD,
    SPOUSE,
}

data class RelationDialogState(
    val mode: RelationMode,
    val source: Person,
    val people: List<Person>,
    val query: String = "",
    val selected: PersonId? = null,
    val parentKind: ParentKind = ParentKind.BIOLOGICAL,
    val marriageStatus: MarriageStatus = MarriageStatus.MARRIED,
    val marriageSince: EventDateInput = EventDateInput(),
    val marriagePlace: String = "",
    val secondParentCandidates: List<Person> = emptyList(),
    val secondParent: PersonId? = null,
    val secondParentQuery: String = "",
    val error: String? = null,
) {
    val filteredPeople: List<Person>
        get() {
            val normalized = query.trim().lowercase()
            val candidates = if (mode == RelationMode.SPOUSE) {
                source.gender.opposite()?.let { required -> people.filter { it.gender == required } } ?: people
            } else {
                people
            }
            return candidates.filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }

    val filteredSecondParentCandidates: List<Person>
        get() {
            val normalized = secondParentQuery.trim().lowercase()
            return secondParentCandidates
                .filter { it.id != selected }
                .filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }
}

internal fun RelationDialogState.marriageDetails(): Outcome<Pair<EventDate, Place?>> =
    marriageSince.toEventDate()
        .flatMap { since -> placeOf(marriagePlace).flatMap { place -> Outcome.Ok(since to place) } }

internal fun MarriageStatus.label(): String = when (this) {
    MarriageStatus.MARRIED -> "В браке"
    MarriageStatus.DIVORCED -> "Разведены"
    MarriageStatus.WIDOWED -> "Вдовство"
    MarriageStatus.PARTNERS -> "Партнёры"
}
