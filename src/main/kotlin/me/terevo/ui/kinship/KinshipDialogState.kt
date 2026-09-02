package me.terevo.ui.kinship

import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId

data class KinshipDialogState(
    val source: Person,
    val people: List<Person>,
    val query: String = "",
    val target: PersonId? = null,
    val term: String? = null,
) {
    val filteredPeople: List<Person>
        get() {
            val normalized = query.trim().lowercase()
            return people.filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }
}
