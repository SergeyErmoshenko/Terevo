package me.terevo.ui.kinship

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme

@Composable
fun KinshipDialog(
    state: KinshipDialogState,
    onChange: (KinshipDialogState) -> Unit,
    onClose: () -> Unit,
) {
    val spacing = TerevoTheme.spacing
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(Strings.KINSHIP_DIALOG_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                Text(state.source.name.display)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onChange(state.copy(query = it)) },
                    label = { Text(Strings.SEARCH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                state.filteredPeople.forEach { person ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onChange(state.copy(target = person.id))
                        },
                    ) {
                        RadioButton(
                            selected = state.target == person.id,
                            onClick = { onChange(state.copy(target = person.id)) },
                        )
                        Text(person.name.display)
                    }
                }
                state.term?.let { Text("${Strings.KINSHIP_RESULT}: $it") }
            }
        },
        confirmButton = {
            Button(onClick = onClose) { Text(Strings.CLOSE) }
        },
    )
}
