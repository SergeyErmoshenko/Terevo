package me.terevo.domain.invariant

import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearsUntil
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.model.ParentChild
import me.terevo.domain.model.Person
import me.terevo.domain.model.interval

object ChronologyPlausible {
    const val MIN_PARENT_AGE: Int = 12
    const val POSTHUMOUS_GRACE_YEARS: Int = 1

    fun check(tree: FamilyTree, change: Change): List<ValidationWarning> {
        val relation = (change as? Change.AddRelation)?.relation as? ParentChild ?: return emptyList()
        val parent = tree.person(relation.parent) ?: return emptyList()
        val child = tree.person(relation.child) ?: return emptyList()
        val parentBirth = birthOf(parent) ?: return emptyList()
        val childBirth = birthOf(child) ?: return emptyList()

        val warnings = mutableListOf<ValidationWarning>()
        if (parentBirth > childBirth) {
            warnings.add(ValidationWarning.ParentBornAfterChild(parent.id, child.id))
        } else {
            val age = parentBirth.yearsUntil(childBirth)
            if (age < MIN_PARENT_AGE) {
                warnings.add(ValidationWarning.ParentTooYoung(parent.id, child.id, age))
            }
        }

        val parentDeath = parent.lifeSpan.death.interval?.to
        if (parentDeath != null && childBirth > parentDeath) {
            val gap = parentDeath.yearsUntil(childBirth)
            if (gap > POSTHUMOUS_GRACE_YEARS) {
                warnings.add(ValidationWarning.ChildBornAfterParentDeath(parent.id, child.id, gap))
            }
        }
        return warnings
    }

    private fun birthOf(person: Person): LocalDate? = person.lifeSpan.birth.interval?.from
}
