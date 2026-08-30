package me.terevo.domain.port

import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.model.FamilyTree

interface TreeRepository {
    fun load(): Outcome<FamilyTree>

    fun apply(changes: List<Change>): Outcome<Unit>

    companion object {
        val NONE: TreeRepository = object : TreeRepository {
            override fun load(): Outcome<FamilyTree> = Outcome.Ok(FamilyTree.EMPTY)

            override fun apply(changes: List<Change>): Outcome<Unit> = Outcome.Ok(Unit)
        }
    }
}
