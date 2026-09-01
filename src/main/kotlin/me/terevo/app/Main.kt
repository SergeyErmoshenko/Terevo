package me.terevo.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Frame
import me.terevo.persistence.SqliteProjectService
import me.terevo.ui.App
import me.terevo.ui.AppAction
import me.terevo.ui.Strings
import me.terevo.ui.person.RelationMode
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.TreeCanvasIntent

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    val controller = remember { AppController(SqliteProjectService()) }
    var appState by remember { mutableStateOf(controller.state) }
    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        state = windowState,
        title = if (appState.isProjectOpen) "${Strings.APP_NAME} — ${appState.projectName}" else Strings.APP_NAME,
    ) {
        val dialogs = remember(window) { ProjectDialogs(window as? Frame) }
        val onAction: (AppAction) -> Unit = { action ->
            when (action) {
                AppAction.NewProject -> dialogs.chooseCreate()?.let { appState = controller.create(it) }
                AppAction.OpenProject -> dialogs.chooseOpen()?.let { appState = controller.open(it) }
                AppAction.SaveProject -> Unit
                AppAction.Undo -> {
                    controller.commandBus?.undo()
                    appState = controller.refreshTree()
                }
                AppAction.Redo -> {
                    controller.commandBus?.redo()
                    appState = controller.refreshTree()
                }
                AppAction.Search -> Unit
                AppAction.FitToScreen -> appState = controller.updateCanvas(TreeCanvasIntent.FitToScreen)
                AppAction.AddPerson -> appState = controller.startAddingPerson()
                AppAction.EditPerson -> appState = controller.startEditingPerson()
                AppAction.DeletePerson -> appState = controller.deleteSelectedPerson()
                AppAction.AddParent -> appState = controller.startAddingRelation(RelationMode.PARENT)
                AppAction.AddChild -> appState = controller.startAddingRelation(RelationMode.CHILD)
                AppAction.AddSpouse -> appState = controller.startAddingRelation(RelationMode.SPOUSE)
                is AppAction.SelectPerson -> appState = controller.selectPerson(action.id)
                is AppAction.UpdatePersonForm -> appState = controller.updatePersonForm(action.form)
                is AppAction.UpdateRelationDialog -> appState = controller.updateRelationDialog(action.dialog)
                is AppAction.Canvas -> appState = controller.updateCanvas(action.intent)
                AppAction.SavePerson -> appState = controller.savePerson()
                AppAction.CancelPerson -> appState = controller.cancelPerson()
                AppAction.CreateRelative -> appState = controller.startCreatingRelative()
                AppAction.SaveRelation -> appState = controller.saveRelation()
                AppAction.CancelRelation -> appState = controller.cancelRelation()
            }
        }
        MenuBar {
            Menu(Strings.FILE) {
                Item(Strings.NEW_PROJECT, shortcut = KeyShortcut(Key.N, meta = true), onClick = { onAction(AppAction.NewProject) })
                Item(Strings.OPEN_PROJECT, shortcut = KeyShortcut(Key.O, meta = true), onClick = { onAction(AppAction.OpenProject) })
                Item(Strings.SAVE_PROJECT, shortcut = KeyShortcut(Key.S, meta = true), enabled = appState.isProjectOpen, onClick = { onAction(AppAction.SaveProject) })
            }
            Menu(Strings.EDIT) {
                Item(Strings.UNDO, shortcut = KeyShortcut(Key.Z, meta = true), enabled = controller.commandBus?.canUndo == true, onClick = { onAction(AppAction.Undo) })
                Item(Strings.REDO, shortcut = KeyShortcut(Key.Z, meta = true, shift = true), enabled = controller.commandBus?.canRedo == true, onClick = { onAction(AppAction.Redo) })
                Item(Strings.SEARCH, shortcut = KeyShortcut(Key.F, meta = true), enabled = appState.isProjectOpen, onClick = { onAction(AppAction.Search) })
            }
            Menu(Strings.VIEW) {
                Item(Strings.FIT_TO_SCREEN, enabled = appState.isProjectOpen, onClick = { onAction(AppAction.FitToScreen) })
            }
            Menu(Strings.HELP) {
                Item(Strings.APP_NAME, onClick = {})
            }
        }
        TerevoTheme {
            App(state = appState, onAction = onAction)
        }
    }
}
