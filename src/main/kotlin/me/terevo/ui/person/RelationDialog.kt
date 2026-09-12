package me.terevo.ui.person

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.domain.Outcome
import me.terevo.domain.flatMap
import me.terevo.domain.model.*
import me.terevo.ui.Strings
import me.terevo.ui.components.SelectableOption
import me.terevo.ui.theme.TerevoTheme

enum class RelationMode {
    PARENT,
    CHILD,
    SPOUSE,
}

data class RelationDialogState(
    val mode: RelationMode,
    val source: Person,
    val people: List<Person>,
    val query: String = "",
    val selected: PersonId? = null,
    val parentKind: ParentKind = ParentKind.BIOLOGICAL,
    val marriageStatus: MarriageStatus = MarriageStatus.MARRIED,
    val marriageSince: EventDateInput = EventDateInput(),
    val marriagePlace: String = "",
    val secondParentCandidates: List<Person> = emptyList(),
    val secondParent: PersonId? = null,
    val secondParentQuery: String = "",
    val error: String? = null,
) {
    val filteredPeople: List<Person>
        get() {
            val normalized = query.trim().lowercase()
            val candidates = if (mode == RelationMode.SPOUSE) {
                source.gender.opposite()?.let { required -> people.filter { it.gender == required } } ?: people
            } else {
                people
            }
            return candidates.filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }

    val filteredSecondParentCandidates: List<Person>
        get() {
            val normalized = secondParentQuery.trim().lowercase()
            return secondParentCandidates
                .filter { it.id != selected }
                .filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }
}

internal fun RelationDialogState.marriageDetails(): Outcome<Pair<EventDate, Place?>> =
    marriageSince.toEventDate()
        .flatMap { since -> placeOf(marriagePlace).flatMap { place -> Outcome.Ok(since to place) } }

@Composable
fun RelationDialog(
    state: RelationDialogState,
    onChange: (RelationDialogState) -> Unit,
    onCreatePerson: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val spacing = TerevoTheme.spacing
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(state.dialogTitle()) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(state.source.name.display)
                Button(onClick = onCreatePerson, modifier = Modifier.fillMaxWidth()) {
                    Text(Strings.CREATE_NEW_PERSON)
                }
                Text(Strings.LINK_EXISTING_PERSON)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onChange(state.copy(query = it, error = null)) },
                    label = { Text(Strings.SEARCH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                state.filteredPeople.forEach { person ->
                    SelectableOption(state.selected == person.id, person.name.display) {
                        onChange(state.copy(selected = person.id, error = null))
                    }
                }
                if (state.mode == RelationMode.PARENT || state.mode == RelationMode.CHILD) {
                    EnumOptions(ParentKind.entries, state.parentKind, ParentKind::label) {
                        onChange(state.copy(parentKind = it, error = null))
                    }
                }
                if (state.mode == RelationMode.CHILD) {
                    Text(Strings.SECOND_PARENT)
                    OutlinedTextField(
                        value = state.secondParentQuery,
                        onValueChange = { onChange(state.copy(secondParentQuery = it, error = null)) },
                        label = { Text(Strings.SEARCH) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SelectableOption(state.secondParent == null, Strings.SECOND_PARENT_NONE) {
                        onChange(state.copy(secondParent = null, error = null))
                    }
                    state.filteredSecondParentCandidates.forEach { person ->
                        SelectableOption(state.secondParent == person.id, person.name.display) {
                            onChange(state.copy(secondParent = person.id, error = null))
                        }
                    }
                }
                if (state.mode != RelationMode.PARENT && state.mode != RelationMode.CHILD) {
                    EnumOptions(MarriageStatus.entries, state.marriageStatus, MarriageStatus::label) {
                        onChange(state.copy(marriageStatus = it, error = null))
                    }
                    EventDateFields(
                        label = Strings.MARRIAGE_DATE,
                        input = state.marriageSince,
                        error = null,
                    ) { onChange(state.copy(marriageSince = it, error = null)) }
                    OutlinedTextField(
                        value = state.marriagePlace,
                        onValueChange = { onChange(state.copy(marriagePlace = it, error = null)) },
                        label = { Text(Strings.MARRIAGE_PLACE) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                state.error?.let { Text(it) }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = state.selected != null) { Text(Strings.SAVE) }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) { Text(Strings.CANCEL) }
        },
    )
}

@Composable
private fun <T> EnumOptions(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    values.forEach { value ->
        SelectableOption(selected == value, label(value)) { onSelect(value) }
    }
}

private fun RelationDialogState.dialogTitle(): String = when (mode) {
    RelationMode.PARENT -> Strings.ADD_PARENT
    RelationMode.CHILD -> Strings.ADD_CHILD
    RelationMode.SPOUSE -> source.gender.spouseActionLabel()
}

private fun ParentKind.label(): String = when (this) {
    ParentKind.BIOLOGICAL -> "Биологический"
    ParentKind.ADOPTIVE -> "Приёмный"
    ParentKind.STEP -> "Неродной"
    ParentKind.FOSTER -> "Опекун"
}

internal fun MarriageStatus.label(): String = when (this) {
    MarriageStatus.MARRIED -> "В браке"
    MarriageStatus.DIVORCED -> "Разведены"
    MarriageStatus.WIDOWED -> "Вдовство"
    MarriageStatus.PARTNERS -> "Партнёры"
}
