package me.terevo.ui.events

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.ui.Strings
import me.terevo.ui.person.displayText
import me.terevo.ui.theme.TerevoTheme

@Composable
fun EventsScreen(rows: List<EventRow>) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            EventsHeaderCell(Strings.TAB_EVENTS, 1.2f)
            EventsHeaderCell(Strings.SURNAME, 2f)
            EventsHeaderCell(Strings.BIRTH_DATE, 1.2f)
            EventsHeaderCell(Strings.SEARCH_PLACE, 1.5f)
            EventsHeaderCell("Осталось дней", 1f)
            EventsHeaderCell("Прошло лет", 1f)
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(rows) { row ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.small),
                ) {
                    Text(row.type.label, modifier = Modifier.weight(1.2f))
                    Text(row.participants, modifier = Modifier.weight(2f))
                    Text(row.date.displayText() ?: "—", modifier = Modifier.weight(1.2f))
                    Text(row.place, modifier = Modifier.weight(1.5f))
                    Text(row.daysUntilAnniversary?.toString() ?: "—", modifier = Modifier.weight(1f))
                    Text(row.yearsPassed?.toString() ?: "—", modifier = Modifier.weight(1f))
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

private val EventType.label: String
    get() = when (this) {
        EventType.BIRTH -> Strings.BIRTH_DATE
        EventType.WEDDING -> Strings.MARRIAGE_DATE
        EventType.DEATH -> Strings.DEATH_DATE
    }
