package me.terevo.domain.invariant

import me.terevo.domain.model.PersonId

sealed interface ValidationWarning {
    data class ParentBornAfterChild(val parent: PersonId, val child: PersonId) : ValidationWarning

    data class ParentTooYoung(val parent: PersonId, val child: PersonId, val years: Int) : ValidationWarning

    data class ChildBornAfterParentDeath(val parent: PersonId, val child: PersonId, val years: Int) : ValidationWarning
}
