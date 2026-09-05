package me.terevo.ui.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import me.terevo.domain.model.PersonId
import me.terevo.layout.Point
import me.terevo.ui.Strings
import me.terevo.ui.person.RelationMode
import me.terevo.ui.theme.TerevoTheme
import kotlin.math.roundToInt

data class DragRelationMenuState(
    val source: PersonId,
    val target: PersonId,
    val position: Point,
    val validity: Map<RelationMode, Boolean>,
)

@Composable
fun DragRelationMenu(
    state: DragRelationMenuState,
    onChoose: (RelationMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(state.position.x.roundToInt(), state.position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(spacing.cornerRadius))
                .background(colors.cardSurface)
                .padding(spacing.extraSmall),
        ) {
            DragRelationMenuItem(Strings.ADD_PARENT, state.validity[RelationMode.PARENT] == true) {
                onChoose(RelationMode.PARENT)
            }
            DragRelationMenuItem(Strings.ADD_CHILD, state.validity[RelationMode.CHILD] == true) {
                onChoose(RelationMode.CHILD)
            }
            DragRelationMenuItem(Strings.ADD_SPOUSE, state.validity[RelationMode.SPOUSE] == true) {
                onChoose(RelationMode.SPOUSE)
            }
        }
    }
}

@Composable
private fun DragRelationMenuItem(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Text(
        text = label,
        color = if (enabled) colors.textPrimary else colors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(spacing.small),
    )
}
