package me.terevo.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import compose.icons.TablerIcons
import compose.icons.tablericons.Edit
import compose.icons.tablericons.Plus
import compose.icons.tablericons.Trash
import me.terevo.domain.model.EventId
import me.terevo.ui.Strings
import me.terevo.ui.person.displayText
import me.terevo.ui.theme.TerevoTheme

@Composable
fun EventsScreen(
    rows: List<EventRow>,
    onAddEvent: () -> Unit,
    onEditEvent: (EventId) -> Unit,
    onDeleteEvent: (EventId) -> Unit,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Button(onClick = onAddEvent) {
                Icon(TablerIcons.Plus, contentDescription = null)
                Text(Strings.ADD_EVENT, modifier = Modifier.padding(start = spacing.small))
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = spacing.small)) {
            EventsHeaderCell(Strings.TAB_EVENTS, 1.2f)
            EventsHeaderCell(Strings.SURNAME, 2f)
            EventsHeaderCell(Strings.BIRTH_DATE, 1.2f)
            EventsHeaderCell(Strings.SEARCH_PLACE, 1.5f)
            EventsHeaderCell("Осталось дней", 1f)
            EventsHeaderCell("Прошло лет", 1f)
            EventsHeaderCell("", 1f)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(row.type, modifier = Modifier.weight(1.2f))
                    Text(row.participants, modifier = Modifier.weight(2f))
                    Text(row.date.displayText() ?: "—", modifier = Modifier.weight(1.2f))
                    Text(row.place, modifier = Modifier.weight(1.5f))
                    Text(row.daysUntilAnniversary?.toString() ?: "—", modifier = Modifier.weight(1f))
                    Text(row.yearsPassed?.toString() ?: "—", modifier = Modifier.weight(1f))
                    Row(modifier = Modifier.weight(1f)) {
                        val id = row.id
                        if (id != null) {
                            IconButton(onClick = { onEditEvent(id) }) {
                                Icon(TablerIcons.Edit, contentDescription = Strings.EDIT_EVENT)
                            }
                            IconButton(onClick = { onDeleteEvent(id) }) {
                                Icon(TablerIcons.Trash, contentDescription = Strings.DELETE_EVENT, tint = colors.error)
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outline)
            }
        }
    }
}

@Composable
private fun RowScope.EventsHeaderCell(label: String, weight: Float) {
    val colors = TerevoTheme.colors
    Text(
        label,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelLarge,
        color = colors.textSecondary,
    )
}
