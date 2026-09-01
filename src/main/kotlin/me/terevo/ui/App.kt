package me.terevo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.terevo.domain.model.Person
import me.terevo.ui.person.PersonFormDialog
import me.terevo.ui.person.PersonFormState
import me.terevo.ui.person.RelatedPerson
import me.terevo.ui.person.RelationDialog
import me.terevo.ui.person.RelationDialogState
import me.terevo.ui.person.displayText
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.TreeCanvas
import me.terevo.ui.tree.TreeCanvasIntent
import me.terevo.ui.tree.TreeCanvasState

data class AppState(
    val isProjectOpen: Boolean = false,
    val projectName: String = "",
    val personCount: Int = 0,
    val canvas: TreeCanvasState = TreeCanvasState(),
    val selectedPerson: Person? = null,
    val selectedParents: List<Person> = emptyList(),
    val selectedChildren: List<Person> = emptyList(),
    val selectedSpouses: List<Person> = emptyList(),
    val relatedPeople: List<RelatedPerson> = emptyList(),
    val personForm: PersonFormState? = null,
    val relationDialog: RelationDialogState? = null,
    val pendingRelation: RelationDialogState? = null,
    val status: String = Strings.NO_PROJECT,
)

sealed interface AppAction {
    data object NewProject : AppAction
    data object OpenProject : AppAction
    data object SaveProject : AppAction
    data object Undo : AppAction
    data object Redo : AppAction
    data object Search : AppAction
    data object FitToScreen : AppAction
    data object AddPerson : AppAction
    data object EditPerson : AppAction
    data object DeletePerson : AppAction
    data object AddParent : AppAction
    data object AddChild : AppAction
    data object AddSpouse : AppAction
    data class SelectPerson(val id: me.terevo.domain.model.PersonId) : AppAction
    data class UpdatePersonForm(val form: PersonFormState) : AppAction
    data class UpdateRelationDialog(val dialog: RelationDialogState) : AppAction
    data class Canvas(val intent: TreeCanvasIntent) : AppAction
    data object SavePerson : AppAction
    data object CancelPerson : AppAction
    data object CreateRelative : AppAction
    data object SaveRelation : AppAction
    data object CancelRelation : AppAction
}

data class EmptyProjectAction(
    val label: String,
    val action: AppAction,
)

val emptyProjectActions: List<EmptyProjectAction> = listOf(
    EmptyProjectAction(Strings.NEW_PROJECT, AppAction.NewProject),
    EmptyProjectAction(Strings.OPEN_PROJECT, AppAction.OpenProject),
)

@Composable
fun App(
    state: AppState,
    onAction: (AppAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Box(
                Modifier
                    .width(spacing.sidebarWidth)
                    .fillMaxSize()
                    .background(colors.sidebar)
                    .padding(spacing.medium),
            ) {
                if (state.isProjectOpen) {
                    ProjectSidebar(state, onAction)
                }
            }
            VerticalDivider()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(colors.canvas),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    if (!state.isProjectOpen) {
                        Text(Strings.EMPTY_PROJECT)
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
                            Button(onClick = { onAction(emptyProjectActions[0].action) }) {
                                Text(emptyProjectActions[0].label)
                            }
                            OutlinedButton(onClick = { onAction(emptyProjectActions[1].action) }) {
                                Text(emptyProjectActions[1].label)
                            }
                        }
                    } else if (state.personCount == 0) {
                        Text(Strings.EMPTY_TREE)
                        Button(onClick = { onAction(AppAction.AddPerson) }) {
                            Text(Strings.ADD_PERSON)
                        }
                    }
                }
                if (state.isProjectOpen && state.personCount > 0) {
                    TreeCanvas(
                        state = state.canvas,
                        onIntent = { onAction(AppAction.Canvas(it)) },
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(spacing.statusBarHeight)
                .background(colors.statusBar)
                .padding(horizontal = spacing.small),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(state.status)
        }
    }
    state.personForm?.let { form ->
        PersonFormDialog(
            state = form,
            onChange = { onAction(AppAction.UpdatePersonForm(it)) },
            onSave = { onAction(AppAction.SavePerson) },
            onCancel = { onAction(AppAction.CancelPerson) },
        )
    }
    state.relationDialog?.let { dialog ->
        RelationDialog(
            state = dialog,
            onChange = { onAction(AppAction.UpdateRelationDialog(it)) },
            onCreatePerson = { onAction(AppAction.CreateRelative) },
            onSave = { onAction(AppAction.SaveRelation) },
            onCancel = { onAction(AppAction.CancelRelation) },
        )
    }
}

@Composable
private fun ProjectSidebar(state: AppState, onAction: (AppAction) -> Unit) {
    val spacing = TerevoTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text("${Strings.PROJECT}: ${state.projectName}")
        Text("${Strings.PEOPLE}: ${state.personCount}")
        Button(onClick = { onAction(AppAction.AddPerson) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_PERSON)
        }
        val person = state.selectedPerson ?: return@Column
        HorizontalDivider()
        Text(person.name.display)
        Text(person.lifeSpan.displayText())
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            OutlinedButton(onClick = { onAction(AppAction.EditPerson) }) { Text(Strings.EDIT_PERSON) }
            OutlinedButton(onClick = { onAction(AppAction.DeletePerson) }) { Text(Strings.DELETE_PERSON) }
        }
        Button(onClick = { onAction(AppAction.AddParent) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_PARENT)
        }
        Button(onClick = { onAction(AppAction.AddChild) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_CHILD)
        }
        Button(onClick = { onAction(AppAction.AddSpouse) }, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.ADD_SPOUSE)
        }
        if (state.relatedPeople.isNotEmpty()) HorizontalDivider()
        state.relatedPeople.forEach { related ->
            OutlinedButton(
                onClick = { onAction(AppAction.SelectPerson(related.person.id)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${related.role}: ${related.person.name.display}")
            }
        }
    }
}
