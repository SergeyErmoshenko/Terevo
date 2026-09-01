package me.terevo.domain.command

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.invariant.ValidationWarning
import me.terevo.domain.model.FamilyTree
import me.terevo.domain.port.TreeRepository

class CommandBus(
    initial: FamilyTree = FamilyTree.EMPTY,
    private val repository: TreeRepository = TreeRepository.NONE,
    private val undoLimit: Int = DEFAULT_UNDO_LIMIT,
) {
    private val mutableTree = MutableStateFlow(initial)
    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    val tree: StateFlow<FamilyTree> = mutableTree.asStateFlow()

    val canUndo: Boolean get() = undoStack.isNotEmpty()

    val canRedo: Boolean get() = redoStack.isNotEmpty()

    val undoDepth: Int get() = undoStack.size

    val nextUndo: Command? get() = undoStack.lastOrNull()

    val nextRedo: Command? get() = redoStack.lastOrNull()

    fun execute(command: Command): Outcome<List<ValidationWarning>> {
        val result = when (val applied = command.applyTo(mutableTree.value)) {
            is Outcome.Ok -> applied.value
            is Outcome.Err -> return applied
        }
        val persisted = repository.apply(result.changes)
        if (persisted is Outcome.Err) return persisted
        mutableTree.value = result.tree
        push(undoStack, result.inverse)
        redoStack.clear()
        return Outcome.Ok(result.warnings)
    }

    fun undo(): Outcome<List<ValidationWarning>> = step(undoStack, redoStack, DomainError.History.NothingToUndo)

    fun redo(): Outcome<List<ValidationWarning>> = step(redoStack, undoStack, DomainError.History.NothingToRedo)

    private fun step(
        source: ArrayDeque<Command>,
        target: ArrayDeque<Command>,
        emptyError: DomainError,
    ): Outcome<List<ValidationWarning>> {
        val command = source.removeLastOrNull() ?: return Outcome.Err(emptyError)
        val result = when (val applied = command.applyTo(mutableTree.value)) {
            is Outcome.Ok -> applied.value
            is Outcome.Err -> {
                source.addLast(command)
                return applied
            }
        }
        val persisted = repository.apply(result.changes)
        if (persisted is Outcome.Err) {
            source.addLast(command)
            return persisted
        }
        mutableTree.value = result.tree
        push(target, result.inverse)
        return Outcome.Ok(result.warnings)
    }

    private fun push(stack: ArrayDeque<Command>, command: Command) {
        stack.addLast(command)
        while (stack.size > undoLimit) {
            stack.removeFirst()
        }
    }

    companion object {
        const val DEFAULT_UNDO_LIMIT: Int = 200
    }
}
