package me.terevo.domain.model

import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.invariant.Change
import me.terevo.domain.invariant.Invariants

class FamilyTree private constructor(
    val persons: PersistentMap<PersonId, Person>,
    val relations: PersistentMap<RelationId, Relation>,
    private val index: TreeIndex,
) {
    val size: Int get() = persons.size

    fun person(id: PersonId): Person? = persons[id]

    fun relation(id: RelationId): Relation? = relations[id]

    fun contains(id: PersonId): Boolean = persons.containsKey(id)

    fun relationsOf(person: PersonId): List<Relation> =
        index.relationIdsOf(person)
            .mapNotNull { relations[it] }
            .sortedBy { it.id.value }

    fun parentsOf(child: PersonId): List<PersonId> = parentLinksOf(child).map { it.parent }.sortedBy { it.value }

    fun biologicalParentsOf(child: PersonId): List<PersonId> =
        parentLinksOf(child).filter { it.isBiological }.map { it.parent }.sortedBy { it.value }

    fun childrenOf(parent: PersonId): List<PersonId> =
        relationsOf(parent)
            .filterIsInstance<ParentChild>()
            .filter { it.parent == parent }
            .map { it.child }
            .sortedBy { it.value }

    fun spousesOf(person: PersonId): List<PersonId> =
        relationsOf(person)
            .filterIsInstance<Marriage>()
            .mapNotNull { it.spouseOf(person) }
            .sortedBy { it.value }

    fun ancestors(person: PersonId, depth: Int = UNLIMITED): Set<PersonId> =
        traverse(person, depth) { parentsOf(it) }

    fun descendants(person: PersonId, depth: Int = UNLIMITED): Set<PersonId> =
        traverse(person, depth) { childrenOf(it) }

    fun ancestryPath(from: PersonId, to: PersonId): List<PersonId>? {
        if (from == to) return listOf(from)
        val previous = mutableMapOf<PersonId, PersonId>()
        val visited = mutableSetOf(from)
        val queue = ArrayDeque(listOf(from))
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (parent in parentsOf(current)) {
                if (!visited.add(parent)) continue
                previous[parent] = current
                if (parent == to) return pathTo(parent, from, previous)
                queue.addLast(parent)
            }
        }
        return null
    }

    fun addPerson(person: Person): Outcome<FamilyTree> =
        if (contains(person.id)) {
            Outcome.Err(DomainError.Tree.PersonAlreadyExists(person.id))
        } else {
            Outcome.Ok(FamilyTree(persons.put(person.id, person), relations, index))
        }

    fun updatePerson(person: Person): Outcome<FamilyTree> =
        if (!contains(person.id)) {
            Outcome.Err(DomainError.Missing.Person(person.id))
        } else {
            Outcome.Ok(FamilyTree(persons.put(person.id, person), relations, index))
        }

    fun removePerson(id: PersonId): Outcome<FamilyTree> {
        if (!contains(id)) return Outcome.Err(DomainError.Missing.Person(id))
        val detached = relationsOf(id).fold(this) { tree, relation -> tree.withoutRelation(relation) }
        return Outcome.Ok(
            FamilyTree(
                persons = detached.persons.remove(id),
                relations = detached.relations,
                index = detached.index.afterPersonRemoved(id),
            ),
        )
    }

    fun addRelation(relation: Relation): Outcome<FamilyTree> {
        val missing = relation.participants.firstOrNull { !contains(it) }
        if (missing != null) return Outcome.Err(DomainError.Missing.Person(missing))
        if (relations.containsKey(relation.id)) {
            return Outcome.Err(DomainError.Tree.RelationAlreadyExists(relation.id))
        }
        val violation = Invariants.check(this, Change.AddRelation(relation))
        if (violation != null) return Outcome.Err(violation)
        return Outcome.Ok(
            FamilyTree(
                persons = persons,
                relations = relations.put(relation.id, relation),
                index = index.afterRelationAdded(relation),
            ),
        )
    }

    fun removeRelation(id: RelationId): Outcome<FamilyTree> {
        val relation = relations[id] ?: return Outcome.Err(DomainError.Missing.Relation(id))
        return Outcome.Ok(withoutRelation(relation))
    }

    private fun withoutRelation(relation: Relation): FamilyTree =
        FamilyTree(
            persons = persons,
            relations = relations.remove(relation.id),
            index = index.afterRelationRemoved(relation),
        )

    override fun equals(other: Any?): Boolean =
        other is FamilyTree && persons == other.persons && relations == other.relations

    override fun hashCode(): Int = persons.hashCode() * PRIME + relations.hashCode()

    override fun toString(): String = "FamilyTree(${persons.size} persons, ${relations.size} relations)"

    fun parentLinksOf(child: PersonId): List<ParentChild> =
        relationsOf(child).filterIsInstance<ParentChild>().filter { it.child == child }

    private fun traverse(start: PersonId, depth: Int, next: (PersonId) -> List<PersonId>): Set<PersonId> {
        if (depth <= 0) return emptySet()
        val visited = mutableSetOf(start)
        val collected = mutableSetOf<PersonId>()
        var frontier = listOf(start)
        var level = 0
        while (frontier.isNotEmpty() && level < depth) {
            val nextFrontier = mutableListOf<PersonId>()
            for (person in frontier) {
                for (neighbour in next(person)) {
                    if (!visited.add(neighbour)) continue
                    collected.add(neighbour)
                    nextFrontier.add(neighbour)
                }
            }
            frontier = nextFrontier
            level++
        }
        return collected
    }

    private fun pathTo(target: PersonId, start: PersonId, previous: Map<PersonId, PersonId>): List<PersonId> {
        val path = mutableListOf(target)
        var current = target
        while (current != start) {
            current = previous.getValue(current)
            path.add(current)
        }
        return path.reversed()
    }

    companion object {
        private const val PRIME = 31

        const val UNLIMITED: Int = Int.MAX_VALUE

        val EMPTY: FamilyTree = FamilyTree(persistentMapOf(), persistentMapOf(), TreeIndex.EMPTY)

        fun of(persons: Collection<Person>, relations: Collection<Relation>): Outcome<FamilyTree> {
            var tree = EMPTY
            for (person in persons) {
                when (val added = tree.addPerson(person)) {
                    is Outcome.Ok -> tree = added.value
                    is Outcome.Err -> return added
                }
            }
            for (relation in relations) {
                when (val added = tree.addRelation(relation)) {
                    is Outcome.Ok -> tree = added.value
                    is Outcome.Err -> return added
                }
            }
            return Outcome.Ok(tree)
        }
    }
}
