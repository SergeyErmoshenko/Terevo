package me.terevo.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import org.jetbrains.skia.Image
import me.terevo.domain.port.ThemeMode
import me.terevo.persistence.SqliteProjectService
import me.terevo.ui.App
import me.terevo.ui.AppAction
import me.terevo.ui.Strings
import me.terevo.ui.person.RelationMode
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.TreeCanvasIntent
import java.awt.Frame

fun main() = application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    val controller = remember { AppController(SqliteProjectService()) }
    var appState by remember { mutableStateOf(controller.state) }
    val appIcon = remember { loadAppIcon() }
    Window(
        onCloseRequest = {
            controller.close()
            exitApplication()
        },
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when {
                    event.key == Key.Escape -> {
                        appState = when {
                            appState.dragRelationMenu != null -> controller.cancelDragRelationMenu()
                            appState.canvas.nodeDrag != null -> controller.updateCanvas(TreeCanvasIntent.DragNodeCancel)
                            else -> controller.updateCanvas(TreeCanvasIntent.ClearSelection)
                        }
                        true
                    }

                    event.isAltPressed && event.key == Key.DirectionLeft -> {
                        appState = controller.navigateBack()
                        true
                    }

                    event.isAltPressed && event.key == Key.DirectionRight -> {
                        appState = controller.navigateForward()
                        true
                    }

                    else -> false
                }
            }
        },
        state = windowState,
        title = if (appState.isProjectOpen) "${Strings.APP_NAME} — ${appState.projectName}" else Strings.APP_NAME,
        icon = appIcon,
    ) {
        val dialogs = remember(window) { ProjectDialogs(window as? Frame) }
        val onAction: (AppAction) -> Unit = { action ->
            when (action) {
                AppAction.NewProject -> dialogs.chooseCreate()?.let { appState = controller.create(it) }
                AppAction.OpenProject -> dialogs.chooseOpen()?.let { appState = controller.open(it) }
                AppAction.ImportGedcom -> dialogs.chooseGedcomImport()?.let { appState = controller.previewGedcom(it) }
                AppAction.ExportGedcom -> dialogs.chooseGedcomExport()?.let { appState = controller.exportGedcom(it) }
                AppAction.ExportPng -> dialogs.choosePngExport()?.let { appState = controller.exportPng(it) }
                AppAction.ExportPdf -> dialogs.choosePdfExport()?.let { appState = controller.exportPdf(it) }
                AppAction.ConfirmGedcomImport -> appState = controller.confirmGedcomImport()
                AppAction.CancelGedcomImport -> appState = controller.cancelGedcomImport()
                AppAction.Undo -> appState = controller.undo()
                AppAction.Redo -> appState = controller.redo()
                AppAction.FitToScreen -> appState = controller.updateCanvas(TreeCanvasIntent.FitToScreen)
                AppAction.ActualSize -> appState = controller.updateCanvas(TreeCanvasIntent.ActualSize)
                AppAction.CenterSelected -> appState = controller.updateCanvas(TreeCanvasIntent.CenterSelected)
                AppAction.ClearSelection -> appState = controller.updateCanvas(TreeCanvasIntent.ClearSelection)
                AppAction.NavigateBack -> appState = controller.navigateBack()
                AppAction.NavigateForward -> appState = controller.navigateForward()
                is AppAction.ChangeLayoutMode -> appState = controller.changeLayoutMode(action.mode)
                is AppAction.ChangeLayoutDepth -> appState = controller.changeLayoutDepth(action.depth)
                is AppAction.ChangeLayoutDirection -> appState = controller.changeLayoutDirection(action.direction)
                is AppAction.ChangeLayoutDensity -> appState = controller.changeLayoutDensity(action.density)
                AppAction.ResetPins -> appState = controller.resetPins()
                is AppAction.ChangeSearchFilter -> appState = controller.changeSearchFilter(action.filter)
                AppAction.AddPerson -> appState = controller.startAddingPerson()
                AppAction.EditPerson -> appState = controller.startEditingPerson()
                AppAction.DeletePerson -> appState = controller.deleteSelectedPerson()
                AppAction.AddParent -> appState = controller.startAddingRelation(RelationMode.PARENT)
                AppAction.AddChild -> appState = controller.startAddingRelation(RelationMode.CHILD)
                AppAction.AddSpouse -> appState = controller.startAddingRelation(RelationMode.SPOUSE)
                AppAction.ChooseMedia -> dialogs.chooseMedia()?.let { appState = controller.importMedia(it) }
                is AppAction.DropMedia -> action.paths.forEach { appState = controller.importMedia(it) }
                AppAction.ChoosePersonFormMedia ->
                    dialogs.chooseMedia()?.let { appState = controller.addPendingPersonMedia(it) }

                is AppAction.DropPersonFormMedia -> appState = controller.addPendingPersonMedia(action.paths)
                is AppAction.RemovePendingPersonMedia -> appState = controller.removePendingPersonMedia(action.path)
                is AppAction.OpenMedia -> appState = controller.openMedia(action.id)
                is AppAction.RemoveMedia -> appState = controller.removeMedia(action.id)
                is AppAction.ChangeMediaZoom -> appState = controller.changeMediaZoom(action.zoom)
                AppAction.CloseMedia -> appState = controller.closeMedia()
                is AppAction.SelectPerson -> appState = controller.selectPerson(action.id)
                is AppAction.UpdatePersonForm -> appState = controller.updatePersonForm(action.form)
                is AppAction.UpdateRelationDialog -> appState = controller.updateRelationDialog(action.dialog)
                is AppAction.Canvas -> appState = controller.updateCanvas(action.intent)
                AppAction.SavePerson -> appState = controller.savePerson()
                AppAction.CancelPerson -> appState = controller.cancelPerson()
                AppAction.ConfirmDiscardPerson -> appState = controller.confirmDiscardPerson()
                AppAction.KeepEditingPerson -> appState = controller.keepEditingPerson()
                AppAction.CreateRelative -> appState = controller.startCreatingRelative()
                AppAction.SaveRelation -> appState = controller.saveRelation()
                AppAction.CancelRelation -> appState = controller.cancelRelation()
                is AppAction.ChooseDragRelationMode -> appState = controller.chooseDragRelationMode(action.mode)
                AppAction.CancelDragRelationMenu -> appState = controller.cancelDragRelationMenu()
                AppAction.OpenKinshipDialog -> appState = controller.startResolvingKinship()
                is AppAction.UpdateKinshipDialog -> appState = controller.updateKinshipDialog(action.dialog)
                AppAction.CloseKinshipDialog -> appState = controller.closeKinshipDialog()
                AppAction.OpenStatistics -> appState = controller.openStatistics()
                AppAction.CloseStatistics -> appState = controller.closeStatistics()
                is AppAction.ChangeThemeMode -> appState = controller.changeThemeMode(action.mode)
                is AppAction.ChangeMainTab -> appState = controller.changeMainTab(action.tab)
                AppAction.ToggleSidebar -> appState = controller.toggleSidebar()
            }
        }
        MenuBar {
            Menu(Strings.FILE) {
                Item(
                    Strings.NEW_PROJECT,
                    shortcut = KeyShortcut(Key.N, meta = true),
                    onClick = { onAction(AppAction.NewProject) })
                Item(
                    Strings.OPEN_PROJECT,
                    shortcut = KeyShortcut(Key.O, meta = true),
                    onClick = { onAction(AppAction.OpenProject) })
                Item(
                    Strings.IMPORT_GEDCOM,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.ImportGedcom) })
                Item(
                    Strings.EXPORT_GEDCOM,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.ExportGedcom) })
                Item(
                    Strings.EXPORT_PNG,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.ExportPng) })
                Item(
                    Strings.EXPORT_PDF,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.ExportPdf) })
            }
            Menu(Strings.EDIT) {
                Item(
                    appState.undoLabel,
                    shortcut = KeyShortcut(Key.Z, meta = true),
                    enabled = appState.canUndo,
                    onClick = { onAction(AppAction.Undo) })
                Item(
                    appState.redoLabel,
                    shortcut = KeyShortcut(Key.Z, meta = true, shift = true),
                    enabled = appState.canRedo,
                    onClick = { onAction(AppAction.Redo) })
            }
            Menu(Strings.VIEW) {
                Item(
                    Strings.FIT_TO_SCREEN,
                    shortcut = KeyShortcut(Key.Zero, meta = true),
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.FitToScreen) })
                Item(
                    Strings.ACTUAL_SIZE,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.ActualSize) })
                Item(
                    Strings.CENTER_SELECTED,
                    enabled = appState.selectedPerson != null,
                    onClick = { onAction(AppAction.CenterSelected) })
                Item(
                    Strings.STATISTICS,
                    enabled = appState.isProjectOpen,
                    onClick = { onAction(AppAction.OpenStatistics) })
                Menu(Strings.THEME) {
                    RadioButtonItem(
                        Strings.THEME_LIGHT,
                        selected = appState.themeMode == ThemeMode.LIGHT,
                        onClick = { onAction(AppAction.ChangeThemeMode(ThemeMode.LIGHT)) })
                    RadioButtonItem(
                        Strings.THEME_DARK,
                        selected = appState.themeMode == ThemeMode.DARK,
                        onClick = { onAction(AppAction.ChangeThemeMode(ThemeMode.DARK)) })
                    RadioButtonItem(
                        Strings.THEME_SYSTEM,
                        selected = appState.themeMode == ThemeMode.SYSTEM,
                        onClick = { onAction(AppAction.ChangeThemeMode(ThemeMode.SYSTEM)) })
                }
            }
        }
        val isDark = when (appState.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
        }
        TerevoTheme(darkTheme = isDark) {
            App(state = appState, onAction = onAction)
        }
    }
}

private fun loadAppIcon(): Painter {
    val bytes = requireNotNull(object {}.javaClass.getResourceAsStream("/icons/icon.png")) {
        "Missing app icon resource /icons/icon.png"
    }.use { it.readBytes() }
    return BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}
