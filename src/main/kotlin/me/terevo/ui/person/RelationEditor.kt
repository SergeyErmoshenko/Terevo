package me.terevo.ui.person

import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.command.*
import me.terevo.domain.invariant.ValidationWarning
import me.terevo.domain.model.*

class RelationEditor(
    private val commandBus: CommandBus,
) {
    fun search(query: String): List<Person> {
        val normalized = query.trim().lowercase()
        return commandBus.tree.value.persons.values
            .filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
            .sortedWith(compareBy({ it.name.sortKey }, { it.id.value }))
    }

    fun addParent(parent: PersonId, child: PersonId, kind: ParentKind): RelationResult =
        executeRelation(ParentChild.of(parent = parent, child = child, kind = kind))

    fun addChild(parent: PersonId, child: PersonId, kind: ParentKind): RelationResult =
        addParent(parent, child, kind)

    fun addSpouse(
        first: PersonId,
        second: PersonId,
        status: MarriageStatus = MarriageStatus.MARRIED,
        since: EventDate = EventDate.Unknown,
        place: Place? = null,
    ): RelationResult =
        executeRelation(Marriage.of(first = first, second = second, since = since, status = status, place = place))

    fun addParentWithPerson(parent: Person, child: PersonId, kind: ParentKind): RelationResult =
        executeCreatedPerson(parent, ParentChild.of(parent = parent.id, child = child, kind = kind))

    fun addChildWithPerson(child: Person, parent: PersonId, kind: ParentKind): RelationResult =
        executeCreatedPerson(child, ParentChild.of(parent = parent, child = child.id, kind = kind))

    fun addSpouseWithPerson(
        spouse: Person,
        person: PersonId,
        status: MarriageStatus = MarriageStatus.MARRIED,
        since: EventDate = EventDate.Unknown,
        place: Place? = null,
    ): RelationResult = executeCreatedPerson(
        spouse,
        Marriage.of(first = person, second = spouse.id, since = since, status = status, place = place),
    )

    fun remove(id: RelationId): RelationResult = execute(RemoveRelation(id))

    fun changeParentKind(id: RelationId, kind: ParentKind): RelationResult {
        val relation = commandBus.tree.value.relation(id) as? ParentChild
            ?: return RelationResult.Error("Связь родитель — ребёнок не найдена")
        val changed = when (val value = relation.with(kind)) {
            is Outcome.Ok -> value.value
            is Outcome.Err -> return RelationResult.Error(value.error.toRelationMessage(commandBus.tree.value))
        }
        return execute(Batch(listOf(RemoveRelation(id), AddRelation(changed))))
    }

    private fun executeCreatedPerson(
        person: Person,
        relation: Outcome<me.terevo.domain.model.Relation>
    ): RelationResult =
        when (relation) {
            is Outcome.Ok -> execute(Batch(listOf(AddPerson(person), AddRelation(relation.value))))
            is Outcome.Err -> RelationResult.Error(relation.error.toRelationMessage(commandBus.tree.value))
        }

    private fun executeRelation(relation: Outcome<me.terevo.domain.model.Relation>): RelationResult = when (relation) {
        is Outcome.Ok -> execute(AddRelation(relation.value))
        is Outcome.Err -> RelationResult.Error(relation.error.toRelationMessage(commandBus.tree.value))
    }

    private fun execute(command: Command): RelationResult = when (val result = commandBus.execute(command)) {
        is Outcome.Ok -> RelationResult.Success(result.value)
        is Outcome.Err -> RelationResult.Error(result.error.toRelationMessage(commandBus.tree.value))
    }
}

sealed interface RelationResult {
    data class Success(val warnings: List<ValidationWarning>) : RelationResult
    data class Error(val message: String) : RelationResult
}

fun DomainError.toRelationMessage(tree: FamilyTree): String = when (this) {
    is DomainError.Link.SelfRelation -> "Человек не может состоять в связи с самим собой"
    is DomainError.Link.Duplicate -> "Такая связь уже существует"
    is DomainError.Link.TooManyBiologicalParents -> "У человека уже есть два биологических родителя"
    is DomainError.Link.MarriageEndsBeforeStart -> "Дата окончания брака не может быть раньше даты начала"
    is DomainError.Link.CycleDetected -> {
        val names = path.map { tree.person(it)?.name?.display ?: it.toString() }
        "Эта связь создаст цикл (${names.joinToString(" → ")})"
    }

    is DomainError.Missing.Person -> "Человек не найден"
    is DomainError.Missing.Relation -> "Связь не найдена"
    else -> "Не удалось изменить связь"
}
