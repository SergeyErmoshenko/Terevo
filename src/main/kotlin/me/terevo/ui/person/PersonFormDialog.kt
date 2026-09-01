package me.terevo.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import me.terevo.domain.model.Gender
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme

@Composable
fun PersonFormDialog(
    state: PersonFormState,
    onChange: (PersonFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = TerevoTheme.spacing
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (state.original == null) Strings.NEW_PERSON else Strings.EDITING_PERSON) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                PersonField(Strings.SURNAME, state.surname, capitalizeWords = true) {
                    onChange(validatePersonForm(state.copy(surname = it)))
                }
                PersonField(Strings.GIVEN_NAME, state.givenName, capitalizeWords = true) {
                    onChange(validatePersonForm(state.copy(givenName = it)))
                }
                PersonField(Strings.PATRONYMIC, state.patronymic, capitalizeWords = true) {
                    onChange(validatePersonForm(state.copy(patronymic = it)))
                }
                Text(Strings.GENDER)
                Gender.entries.forEach { gender ->
                    Option(gender == state.gender, gender.label) {
                        onChange(
                            validatePersonForm(
                                state.copy(
                                    gender = gender,
                                    maidenName = state.maidenName.takeIf { gender == Gender.FEMALE }.orEmpty(),
                                ),
                            ),
                        )
                    }
                }
                if (state.gender == Gender.FEMALE) {
                    PersonField(Strings.MAIDEN_NAME, state.maidenName, capitalizeWords = true) {
                        onChange(validatePersonForm(state.copy(maidenName = it)))
                    }
                }
                EventDateFields(
                    label = Strings.BIRTH_DATE,
                    input = state.birth,
                    error = state.blockingError.takeIf { state.birth.hasInvalidInput() },
                ) {
                    onChange(validatePersonForm(state.copy(birth = it)))
                }
                PersonField(Strings.BIRTH_PLACE, state.birthPlace) {
                    onChange(validatePersonForm(state.copy(birthPlace = it)))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                    Switch(
                        checked = state.isAlive,
                        onCheckedChange = { isAlive ->
                            val changed = if (isAlive) {
                                state.copy(isAlive = true, death = EventDateInput(), deathPlace = "")
                            } else {
                                state.copy(isAlive = false, death = EventDateInput(mode = EventDateMode.EXACT))
                            }
                            onChange(validatePersonForm(changed))
                        },
                    )
                    Text(Strings.IS_ALIVE)
                }
                if (!state.isAlive) {
                    EventDateFields(
                        label = Strings.DEATH_DATE,
                        input = state.death,
                        error = state.blockingError.takeIf { state.death.hasInvalidInput() },
                    ) {
                        onChange(validatePersonForm(state.copy(death = it)))
                    }
                    PersonField(Strings.DEATH_PLACE, state.deathPlace) {
                        onChange(validatePersonForm(state.copy(deathPlace = it)))
                    }
                }
                PersonField(Strings.NOTES, state.notes) {
                    onChange(validatePersonForm(state.copy(notes = it)))
                }
                if (!state.hasDateInputError()) state.blockingError?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = state.canSave) { Text(Strings.SAVE) }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun EventDateFields(
    label: String,
    input: EventDateInput,
    error: String?,
    onChange: (EventDateInput) -> Unit,
) {
    Text(label)
    EventDateMode.entries.forEach { mode ->
        Option(mode == input.mode, mode.label) { onChange(input.copy(mode = mode)) }
    }
    when (input.mode) {
        EventDateMode.EXACT, EventDateMode.APPROXIMATE -> PersonField(
            label = label,
            value = input.value,
            placeholder = Strings.DATE_FORMAT,
            error = error,
        ) { onChange(input.copy(value = it)) }
        EventDateMode.RANGE -> {
            PersonField(Strings.RANGE_START, input.value, Strings.DATE_FORMAT, error = error) {
                onChange(input.copy(value = it))
            }
            PersonField(Strings.RANGE_END, input.end, Strings.DATE_FORMAT, error = error) {
                onChange(input.copy(end = it))
            }
        }
        EventDateMode.UNKNOWN -> Unit
    }
}

@Composable
private fun Option(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}

@Composable
private fun PersonField(
    label: String,
    value: String,
    placeholder: String = "",
    capitalizeWords: Boolean = false,
    error: String? = null,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = if (placeholder.isEmpty()) null else { { Text(placeholder) } },
        supportingText = error?.let { message -> { Text(message) } },
        isError = error != null,
        modifier = Modifier.fillMaxWidth(),
        singleLine = label != Strings.NOTES,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalizeWords) KeyboardCapitalization.Words else KeyboardCapitalization.None,
        ),
    )
}

private fun PersonFormState.hasDateInputError(): Boolean =
    blockingError != null && (birth.hasInvalidInput() || (!isAlive && death.hasInvalidInput()))

private fun EventDateInput.hasInvalidInput(): Boolean = when (mode) {
    EventDateMode.EXACT, EventDateMode.APPROXIMATE -> value.isNotBlank()
    EventDateMode.RANGE -> value.isNotBlank() || end.isNotBlank()
    EventDateMode.UNKNOWN -> false
}

private val Gender.label: String
    get() = when (this) {
        Gender.MALE -> Strings.GENDER_MALE
        Gender.FEMALE -> Strings.GENDER_FEMALE
        Gender.UNKNOWN -> Strings.GENDER_UNKNOWN
    }

private val EventDateMode.label: String
    get() = when (this) {
        EventDateMode.EXACT -> Strings.DATE_EXACT
        EventDateMode.APPROXIMATE -> Strings.DATE_APPROXIMATE
        EventDateMode.RANGE -> Strings.DATE_RANGE
        EventDateMode.UNKNOWN -> Strings.DATE_UNKNOWN
    }
