package me.terevo.ui.kinship

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.ui.Strings
import me.terevo.ui.components.SelectableOption
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(state.source.name.display)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = { onChange(state.copy(query = it)) },
                    label = { Text(Strings.SEARCH) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                state.filteredPeople.forEach { person ->
                    SelectableOption(state.target == person.id, person.name.display) {
                        onChange(state.copy(target = person.id))
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
