package me.terevo.ui.tree

import me.terevo.domain.Outcome
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.AddRelation
import me.terevo.domain.command.Batch
import me.terevo.domain.command.Command
import me.terevo.domain.command.CommandBus
import me.terevo.domain.command.RemovePerson
import me.terevo.domain.command.RemoveRelation
import me.terevo.domain.command.UpdatePerson
import me.terevo.domain.model.PersonId
import me.terevo.layout.NodeId

data class HistoryState(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val undoLabel: String,
    val redoLabel: String,
    val centerOn: NodeId? = null,
)

class HistoryController(
    private val commandBus: CommandBus,
) {
    fun state(): HistoryState = HistoryState(
        canUndo = commandBus.canUndo,
        canRedo = commandBus.canRedo,
        undoLabel = actionLabel("Отменить", commandBus.nextUndo),
        redoLabel = actionLabel("Повторить", commandBus.nextRedo),
    )

    fun undo(): HistoryState {
        val affected = commandBus.nextUndo?.affectedPerson(commandBus)
        return when (commandBus.undo()) {
            is Outcome.Ok -> state().copy(centerOn = affected?.toNodeId())
            is Outcome.Err -> state()
        }
    }

    fun redo(): HistoryState {
        val affected = commandBus.nextRedo?.affectedPerson(commandBus)
        return when (commandBus.redo()) {
            is Outcome.Ok -> state().copy(centerOn = affected?.toNodeId())
            is Outcome.Err -> state()
        }
    }

    private fun actionLabel(prefix: String, command: Command?): String =
        if (command == null) prefix else "$prefix: ${command.russianDescription()}"
}

private fun Command.russianDescription(): String = when (this) {
    is AddPerson -> "восстановление человека"
    is UpdatePerson -> "изменение человека"
    is RemovePerson -> "удаление человека"
    is AddRelation -> "восстановление связи"
    is RemoveRelation -> "удаление связи"
    is Batch -> commands.singleOrNull()?.russianDescription() ?: "групповое изменение"
}

private fun Command.affectedPerson(commandBus: CommandBus): PersonId? = when (this) {
    is AddPerson -> person.id
    is UpdatePerson -> person.id
    is RemovePerson -> id
    is AddRelation -> relation.participants.firstOrNull()
    is RemoveRelation -> commandBus.tree.value.relation(id)?.participants?.firstOrNull()
    is Batch -> commands.firstNotNullOfOrNull { it.affectedPerson(commandBus) }
}

private fun PersonId.toNodeId(): NodeId = NodeId(value.toString())
