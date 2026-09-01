package me.terevo.ui.person

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.domain.model.MarriageStatus
import me.terevo.domain.model.ParentKind
import me.terevo.domain.model.Person
import me.terevo.domain.model.PersonId
import me.terevo.ui.Strings
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
    val error: String? = null,
) {
    val filteredPeople: List<Person>
        get() {
            val normalized = query.trim().lowercase()
            return people.filter { normalized.isEmpty() || normalized in it.name.display.lowercase() }
        }
}

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
        title = { Text(state.mode.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
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
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onChange(state.copy(selected = person.id, error = null))
                        },
                    ) {
                        RadioButton(
                            selected = state.selected == person.id,
                            onClick = { onChange(state.copy(selected = person.id, error = null)) },
                        )
                        Text(person.name.display)
                    }
                }
                if (state.mode == RelationMode.PARENT || state.mode == RelationMode.CHILD) {
                    EnumOptions(ParentKind.entries, state.parentKind, ParentKind::label) {
                        onChange(state.copy(parentKind = it, error = null))
                    }
                } else {
                    EnumOptions(MarriageStatus.entries, state.marriageStatus, MarriageStatus::label) {
                        onChange(state.copy(marriageStatus = it, error = null))
                    }
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
        Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(value) }) {
            RadioButton(selected = selected == value, onClick = { onSelect(value) })
            Text(label(value))
        }
    }
}

private val RelationMode.title: String
    get() = when (this) {
        RelationMode.PARENT -> Strings.ADD_PARENT
        RelationMode.CHILD -> Strings.ADD_CHILD
        RelationMode.SPOUSE -> Strings.ADD_SPOUSE
    }

private fun ParentKind.label(): String = when (this) {
    ParentKind.BIOLOGICAL -> "Биологический"
    ParentKind.ADOPTIVE -> "Приёмный"
    ParentKind.STEP -> "Неродной"
    ParentKind.FOSTER -> "Опекун"
}

private fun MarriageStatus.label(): String = when (this) {
    MarriageStatus.MARRIED -> "В браке"
    MarriageStatus.DIVORCED -> "Разведены"
    MarriageStatus.WIDOWED -> "Вдовство"
    MarriageStatus.PARTNERS -> "Партнёры"
}
