package me.terevo.ui.documents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.unit.dp
import me.terevo.ui.AppAction
import me.terevo.ui.AppState
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
fun DocumentsScreen(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    Row(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
        Box(modifier = Modifier.width(280.dp).fillMaxHeight()) {
            PersonPicker(state = state, onAction = onAction)
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = spacing.medium)) {
            DocumentsPanel(state = state, onAction = onAction)
        }
    }
}

@Composable
private fun PersonPicker(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    var query by remember { mutableStateOf("") }
    val filtered = remember(state.personRows, query) {
        if (query.isBlank()) {
            state.personRows
        } else {
            state.personRows.filter { it.fullName.lowercase().contains(query.trim().lowercase()) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(Strings.SEARCH) },
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(filtered, key = { it.id.value }) { row ->
                Text(
                    row.fullName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAction(AppAction.SelectPerson(row.id)) }
                        .padding(vertical = spacing.small),
                    color = if (state.selectedPerson?.id == row.id) colors.accent else colors.textPrimary,
                )
                HorizontalDivider(color = colors.outline)
            }
        }
    }
}

@Composable
private fun DocumentsPanel(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    val person = state.selectedPerson
    if (person == null) {
        Text(Strings.NO_RELATIONS)
        return
    }
    val dropTarget = object : DragAndDropTarget {
        @OptIn(ExperimentalComposeUiApi::class)
        override fun onDrop(event: DragAndDropEvent): Boolean {
            val transferable = event.awtTransferable
            if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
            @Suppress("UNCHECKED_CAST")
            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File> ?: return false
            onAction(AppAction.DropMedia(files.map { it.absolutePath }))
            return true
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Text(person.name.display, style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onAction(AppAction.ChooseMedia) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AttachFile, contentDescription = null)
            Text(Strings.ADD_MEDIA, modifier = Modifier.padding(start = spacing.small))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.selectedMedia, key = { it.id.value }) { media ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
                    border = BorderStroke(1.dp, colors.outline),
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.extraSmall),
                ) {
                    Column(modifier = Modifier.padding(spacing.small)) {
                        Text(media.fileName)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            OutlinedButton(onClick = { onAction(AppAction.OpenMedia(media.id)) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Text(Strings.OPEN_MEDIA, modifier = Modifier.padding(start = spacing.small))
                            }
                            OutlinedButton(onClick = { onAction(AppAction.RemoveMedia(media.id)) }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.error)
                                Text(Strings.REMOVE_MEDIA, modifier = Modifier.padding(start = spacing.small))
                            }
                        }
                    }
                }
            }
        }
    }
}
