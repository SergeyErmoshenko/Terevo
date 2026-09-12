package me.terevo.ui.person

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.terevo.domain.model.Gender
import me.terevo.domain.model.Marriage
import me.terevo.domain.model.Media
import me.terevo.domain.model.MediaId
import me.terevo.domain.model.Person
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme
import me.terevo.ui.tree.ThumbnailCache

data class SpouseInfo(val person: Person, val marriage: Marriage)

@Composable
fun PersonViewDialog(
    person: Person,
    photoPath: String?,
    parents: List<Person>,
    children: List<Person>,
    spouses: List<SpouseInfo>,
    media: List<Media>,
    mediaThumbnails: Map<MediaId, String?>,
    onEdit: () -> Unit,
    onClose: () -> Unit,
    onOpenMedia: (MediaId) -> Unit = {},
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    val genderColor = when (person.gender) {
        Gender.MALE -> colors.male
        Gender.FEMALE -> colors.female
        Gender.UNKNOWN -> colors.unknownGender
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 480.dp, max = 720.dp).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(spacing.cornerRadius),
            tonalElevation = spacing.small,
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(spacing.large)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    photoPath?.let(ThumbnailCache::get)?.let { bitmap ->
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(spacing.cornerRadius)),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
                        Text(
                            person.name.display,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small),
                        ) {
                            GenderBadge(person.gender.genderLabel(), genderColor)
                            Text(
                                person.lifeSpan.displayText(),
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (person.name.maidenName.isNotEmpty()) {
                            Text(
                                "${Strings.MAIDEN_NAME}: ${person.name.maidenName}",
                                color = colors.textSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(spacing.medium))
                HorizontalDivider(color = colors.outline)
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    val facts = listOfNotNull(
                        person.birthPlace?.let { Icons.Filled.Cake to "${Strings.BIRTH_PLACE}: ${it.title}" },
                        person.deathPlace?.let { Icons.Filled.Place to "${Strings.DEATH_PLACE}: ${it.title}" },
                        person.residence?.let { Icons.Filled.Home to "${Strings.RESIDENCE}: ${it.title}" },
                        person.occupation.takeIf { it.isNotBlank() }
                            ?.let { Icons.Filled.Work to "${Strings.OCCUPATION}: $it" },
                        person.notes.takeIf { it.isNotBlank() }?.let { Icons.Filled.Description to it },
                    )
                    if (facts.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
                            facts.forEach { (icon, text) -> InfoRow(icon, text) }
                        }
                    }
                    if (parents.isNotEmpty()) {
                        Section(Strings.PARENTS, Icons.Filled.Group) {
                            parents.forEach { PersonNameRow(it.name.display) }
                        }
                    }
                    if (children.isNotEmpty()) {
                        Section(Strings.CHILDREN, Icons.Filled.Group) {
                            children.forEach { PersonNameRow(it.name.display) }
                        }
                    }
                    if (spouses.isNotEmpty()) {
                        Section(Strings.SPOUSES, Icons.Filled.Group) {
                            spouses.forEach { info ->
                                Surface(
                                    color = colors.surfaceVariant,
                                    shape = RoundedCornerShape(spacing.small),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(spacing.small),
                                        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                                    ) {
                                        Text(info.person.name.display, fontWeight = FontWeight.Medium)
                                        val details = listOfNotNull(
                                            info.marriage.status.label(),
                                            info.marriage.since.displayText()?.let { "${Strings.MARRIAGE_DATE}: $it" },
                                            info.marriage.place?.title?.let { "${Strings.MARRIAGE_PLACE}: $it" },
                                        ).joinToString(" · ")
                                        if (details.isNotEmpty()) {
                                            Text(
                                                details,
                                                color = colors.textSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (person.customFields.isNotEmpty()) {
                        Section(Strings.CUSTOM_FIELDS, Icons.Filled.Info) {
                            person.customFields.forEach { (key, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(key, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                                    Text(value, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (media.isNotEmpty()) {
                        Section(Strings.MEDIA, Icons.AutoMirrored.Filled.InsertDriveFile) {
                            media.forEach { item ->
                                Surface(
                                    color = colors.surfaceVariant,
                                    shape = RoundedCornerShape(spacing.small),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(spacing.small),
                                        horizontalArrangement = Arrangement.spacedBy(spacing.small),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val bitmap = mediaThumbnails[item.id]?.let(ThumbnailCache::get)
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(40.dp)
                                                    .clip(RoundedCornerShape(spacing.extraSmall)),
                                            )
                                        } else {
                                            Icon(
                                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                                contentDescription = null,
                                                tint = colors.textSecondary,
                                                modifier = Modifier.size(28.dp),
                                            )
                                        }
                                        Text(item.fileName, modifier = Modifier.weight(1f))
                                        OutlinedButton(onClick = { onOpenMedia(item.id) }) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                            Text(Strings.OPEN_MEDIA, modifier = Modifier.padding(start = spacing.small))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(spacing.small))
                HorizontalDivider(color = colors.outline)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onClose) { Text(Strings.CLOSE) }
                    Spacer(Modifier.width(spacing.small))
                    Button(onClick = onEdit) { Text(Strings.EDIT_PERSON) }
                }
            }
        }
    }
}

@Composable
private fun GenderBadge(label: String, color: Color) {
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(50)) {
        Text(
            label,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, text: String) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(18.dp))
        Text(text)
    }
}

@Composable
private fun PersonNameRow(name: String) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(colors.textSecondary))
        Text(name)
    }
}

@Composable
private fun Section(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
        ) {
            Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = colors.textSecondary,
                fontWeight = FontWeight.Bold,
            )
        }
        content()
    }
}

private fun Gender.genderLabel(): String = when (this) {
    Gender.MALE -> Strings.GENDER_MALE
    Gender.FEMALE -> Strings.GENDER_FEMALE
    Gender.UNKNOWN -> Strings.GENDER_UNKNOWN
}
