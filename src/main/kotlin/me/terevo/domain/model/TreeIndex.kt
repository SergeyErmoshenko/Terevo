package me.terevo.domain.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf

class TreeIndex private constructor(
    private val relationsByPerson: PersistentMap<PersonId, PersistentSet<RelationId>>,
) {
    fun relationIdsOf(person: PersonId): Set<RelationId> = relationsByPerson[person] ?: emptySet()

    fun afterRelationAdded(relation: Relation): TreeIndex =
        TreeIndex(
            relation.participants.fold(relationsByPerson) { acc, person ->
                acc.put(person, (acc[person] ?: persistentSetOf()).add(relation.id))
            },
        )

    fun afterRelationRemoved(relation: Relation): TreeIndex =
        TreeIndex(
            relation.participants.fold(relationsByPerson) { acc, person ->
                val remaining = (acc[person] ?: persistentSetOf()).remove(relation.id)
                if (remaining.isEmpty()) acc.remove(person) else acc.put(person, remaining)
            },
        )

    fun afterPersonRemoved(person: PersonId): TreeIndex = TreeIndex(relationsByPerson.remove(person))

    companion object {
        val EMPTY: TreeIndex = TreeIndex(persistentMapOf())

        fun of(relations: Collection<Relation>): TreeIndex =
            relations.fold(EMPTY) { index, relation -> index.afterRelationAdded(relation) }
    }
}
