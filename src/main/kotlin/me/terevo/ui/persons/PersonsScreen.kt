package me.terevo.ui.persons

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import me.terevo.domain.model.Gender
import me.terevo.domain.model.PersonId
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.ThumbnailCache

enum class PersonSortColumn {
    NAME,
    BIRTH_DATE,
    RESIDENCE,
    AGE,
    OCCUPATION,
    COMMENT,
}

private data class PersonSortState(val column: PersonSortColumn = PersonSortColumn.NAME, val ascending: Boolean = true)

private fun <T : Comparable<T>> comparatorFor(selector: (PersonRow) -> T?): Comparator<PersonRow> =
    Comparator { a, b -> compareValues(selector(a), selector(b)) }

private fun List<PersonRow>.sortedByColumn(sortState: PersonSortState): List<PersonRow> {
    val comparator = when (sortState.column) {
        PersonSortColumn.NAME -> comparatorFor { it.fullName }
        PersonSortColumn.BIRTH_DATE -> comparatorFor { it.birthDateSortKey }
        PersonSortColumn.RESIDENCE -> comparatorFor { it.residence }
        PersonSortColumn.AGE -> comparatorFor { it.ageSortKey }
        PersonSortColumn.OCCUPATION -> comparatorFor { it.occupation }
        PersonSortColumn.COMMENT -> comparatorFor { it.comment }
    }
    return if (sortState.ascending) sortedWith(comparator) else sortedWith(comparator.reversed())
}

@Composable
fun PersonsScreen(
    rows: List<PersonRow>,
    onSelect: (PersonId) -> Unit,
    onEdit: (PersonId) -> Unit,
    onAddPerson: () -> Unit,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    var query by remember { mutableStateOf("") }
    var sortState by remember { mutableStateOf(PersonSortState()) }
    val filtered = remember(rows, query) {
        if (query.isBlank()) {
            rows
        } else {
            rows.filter { row ->
                query.trim().lowercase().let { needle ->
                    row.fullName.lowercase().contains(needle) ||
                            row.occupation.lowercase().contains(needle) ||
                            row.residence.lowercase().contains(needle) ||
                            row.comment.lowercase().contains(needle)
                }
            }
        }
    }
    val sorted = remember(filtered, sortState) { filtered.sortedByColumn(sortState) }
    Column(modifier = Modifier.fillMaxSize().padding(spacing.medium)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(Strings.SEARCH) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onAddPerson) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Text(Strings.ADD_PERSON, modifier = Modifier.padding(start = spacing.small))
            }
        }
        Box(modifier = Modifier.padding(top = spacing.small)) {
            PersonRowHeader(
                sortState = sortState,
                onSort = { column ->
                    sortState = if (sortState.column == column) {
                        sortState.copy(ascending = !sortState.ascending)
                    } else {
                        PersonSortState(column, ascending = true)
                    }
                },
            )
        }
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(sorted, key = { it.id.value }) { row ->
                PersonRowItem(row = row, onSelect = onSelect, onEdit = onEdit)
                HorizontalDivider(color = colors.outline)
            }
        }
    }
}

@Composable
private fun RowScope.SortableHeaderCell(
    label: String,
    weight: Float,
    column: PersonSortColumn,
    sortState: PersonSortState,
    onSort: (PersonSortColumn) -> Unit,
) {
    val colors = TerevoTheme.colors
    Row(
        modifier = Modifier.weight(weight).clickable { onSort(column) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge,
            color = colors.textSecondary,
        )
        if (sortState.column == column) {
            Icon(
                imageVector = if (sortState.ascending) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp).padding(start = 2.dp),
            )
        }
    }
}

@Composable
private fun PersonRowHeader(sortState: PersonSortState, onSort: (PersonSortColumn) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("", modifier = Modifier.size(40.dp))
        SortableHeaderCell(Strings.SURNAME, 2f, PersonSortColumn.NAME, sortState, onSort)
        SortableHeaderCell(Strings.BIRTH_DATE, 1.2f, PersonSortColumn.BIRTH_DATE, sortState, onSort)
        SortableHeaderCell(Strings.RESIDENCE, 1.5f, PersonSortColumn.RESIDENCE, sortState, onSort)
        SortableHeaderCell("Возраст", 0.8f, PersonSortColumn.AGE, sortState, onSort)
        SortableHeaderCell(Strings.OCCUPATION, 1.5f, PersonSortColumn.OCCUPATION, sortState, onSort)
        SortableHeaderCell(Strings.NOTES, 1.5f, PersonSortColumn.COMMENT, sortState, onSort)
        Text("", modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun PersonRowItem(row: PersonRow, onSelect: (PersonId) -> Unit, onEdit: (PersonId) -> Unit) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    val genderColor = when (row.gender) {
        Gender.MALE -> colors.male
        Gender.FEMALE -> colors.female
        Gender.UNKNOWN -> colors.unknownGender
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(row.id) }
            .padding(vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PersonThumbnail(path = row.thumbnailPath, tint = genderColor)
        Text(row.fullName, modifier = Modifier.weight(2f).padding(start = spacing.small))
        Text(row.birthDate, modifier = Modifier.weight(1.2f))
        Text(row.residence, modifier = Modifier.weight(1.5f))
        Text(row.age, modifier = Modifier.weight(0.8f))
        Text(row.occupation, modifier = Modifier.weight(1.5f))
        Text(row.comment, modifier = Modifier.weight(1.5f))
        IconButton(onClick = { onEdit(row.id) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Edit, contentDescription = Strings.EDIT_PERSON)
        }
    }
}

@Composable
private fun PersonThumbnail(path: String?, tint: androidx.compose.ui.graphics.Color) {
    val bitmap = path?.let { ThumbnailCache.get(it) }
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(tint),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.surface)
        }
    }
}
