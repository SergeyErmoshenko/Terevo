package me.terevo.ui.person

import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.kinship.KinshipCalculator
import me.terevo.kinship.KinshipResult

data class RelatedPerson(
    val person: Person,
    val role: String,
)

object KinshipRoles {
    fun resolve(tree: FamilyTree, selected: PersonId): List<RelatedPerson> =
        tree.persons.values.asSequence()
            .filter { it.id != selected }
            .mapNotNull { person -> roleOf(tree, selected, person.id)?.let { RelatedPerson(person, it) } }
            .sortedWith(compareBy(RelatedPerson::role, { it.person.name.sortKey }))
            .toList()

    private fun roleOf(tree: FamilyTree, selected: PersonId, other: PersonId): String? =
        when (val result = KinshipCalculator.resolve(tree, selected, other)) {
            is KinshipResult.Blood -> result.term
            is KinshipResult.InLaw -> result.term
            KinshipResult.SamePerson, KinshipResult.Unrelated -> null
        }
}
