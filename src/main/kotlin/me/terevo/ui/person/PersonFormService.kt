package me.terevo.ui.person

import me.terevo.domain.Outcome
import me.terevo.domain.command.AddPerson
import me.terevo.domain.command.CommandBus
import me.terevo.domain.command.RemovePerson
import me.terevo.domain.command.UpdatePerson
import me.terevo.domain.invariant.ValidationWarning

class PersonFormService(
    private val commandBus: CommandBus,
) {
    fun save(state: PersonFormState): Outcome<List<ValidationWarning>> = when (val person = state.toPerson()) {
        is Outcome.Ok -> if (state.original == null) {
            commandBus.execute(AddPerson(person.value))
        } else {
            commandBus.execute(UpdatePerson(person.value))
        }
        is Outcome.Err -> person
    }

    fun delete(state: PersonFormState): Outcome<List<ValidationWarning>> = commandBus.execute(RemovePerson(state.id))

    fun requestCancel(state: PersonFormState): PersonFormState =
        if (state.isDirty) state.copy(isDiscardConfirmationVisible = true) else state
}
