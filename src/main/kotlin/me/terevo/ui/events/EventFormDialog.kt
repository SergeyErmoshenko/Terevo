package me.terevo.ui.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import compose.icons.TablerIcons
import compose.icons.tablericons.Trash
import me.terevo.ui.Strings
import me.terevo.ui.components.SelectableOption
import me.terevo.ui.person.EventDateFields
import me.terevo.ui.theme.TerevoTheme

@Composable
fun EventFormDialog(
    state: EventFormState,
    onChange: (EventFormState) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (state.original == null) Strings.NEW_EVENT else Strings.EDITING_EVENT) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                OutlinedTextField(
                    value = state.type,
                    onValueChange = { onChange(validateEventForm(state.copy(type = it))) },
                    label = { Text(Strings.EVENT_TYPE) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                EventDateFields(label = Strings.EVENT_DATE, input = state.date, error = null) {
                    onChange(validateEventForm(state.copy(date = it)))
                }
                OutlinedTextField(
                    value = state.place,
                    onValueChange = { onChange(validateEventForm(state.copy(place = it))) },
                    label = { Text(Strings.EVENT_PLACE) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = { onChange(validateEventForm(state.copy(notes = it))) },
                    label = { Text(Strings.NOTES) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Strings.EVENT_PARTICIPANTS)
                state.participants.forEachIndexed { index, participant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val selectedName = participant.personId
                                ?.let { id -> state.people.firstOrNull { it.id == id }?.name?.display }
                            OutlinedTextField(
                                value = selectedName ?: participant.query,
                                onValueChange = { query ->
                                    val participants = state.participants.updated(
                                        index,
                                        participant.copy(personId = null, query = query),
                                    )
                                    onChange(validateEventForm(state.copy(participants = participants)))
                                },
                                label = { Text(Strings.SEARCH) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            if (participant.personId == null) {
                                participant.filteredPeople(state.people).forEach { person ->
                                    SelectableOption(false, person.name.display) {
                                        val participants = state.participants.updated(
                                            index,
                                            participant.copy(personId = person.id, query = ""),
                                        )
                                        onChange(validateEventForm(state.copy(participants = participants)))
                                    }
                                }
                            }
                        }
                        OutlinedTextField(
                            value = participant.role,
                            onValueChange = { role ->
                                val participants = state.participants.updated(index, participant.copy(role = role))
                                onChange(validateEventForm(state.copy(participants = participants)))
                            },
                            label = { Text(Strings.EVENT_PARTICIPANT_ROLE) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        IconButton(
                            onClick = {
                                val participants = state.participants.filterIndexed { row, _ -> row != index }
                                onChange(validateEventForm(state.copy(participants = participants)))
                            },
                        ) {
                            Icon(
                                TablerIcons.Trash,
                                contentDescription = Strings.REMOVE_EVENT_PARTICIPANT,
                                tint = colors.error
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = {
                        onChange(
                            validateEventForm(state.copy(participants = state.participants + EventParticipantInput())),
                        )
                    },
                ) { Text(Strings.ADD_EVENT_PARTICIPANT) }
                state.blockingError?.let { Text(it) }
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

private fun <T> List<T>.updated(index: Int, value: T): List<T> =
    mapIndexed { current, item -> if (current == index) value else item }
