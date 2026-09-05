package me.terevo.ui.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import me.terevo.statistics.Statistics
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme

@Composable
fun StatisticsScreen(statistics: Statistics, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.BarChart, contentDescription = null, tint = colors.selection)
            Text(
                Strings.STATISTICS,
                modifier = Modifier.padding(start = spacing.small),
                style = MaterialTheme.typography.titleLarge
            )
        }
        OutlinedButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            Text(Strings.STATISTICS_BACK_TO_TREE, modifier = Modifier.padding(start = spacing.small))
        }
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            StatTile(Strings.STATISTICS_TOTAL_PERSONS, statistics.totalPersons.toString(), Modifier.weight(1f))
            StatTile(Strings.STATISTICS_LIVING, statistics.livingCount.toString(), Modifier.weight(1f))
            StatTile(Strings.STATISTICS_DECEASED, statistics.deceasedCount.toString(), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.medium)) {
            StatTile(
                Strings.STATISTICS_AVERAGE_LIFESPAN,
                statistics.averageLifespanYears
                    ?.let { "%.1f ${Strings.STATISTICS_YEARS}".format(it) }
                    ?: Strings.STATISTICS_NO_LIFESPAN_DATA,
                Modifier.weight(1f),
            )
            StatTile(
                Strings.STATISTICS_MISSING_BIRTH_DATE,
                statistics.missingBirthDateCount.toString(),
                Modifier.weight(1f)
            )
            StatTile(Strings.STATISTICS_MISSING_PARENTS, statistics.missingParentsCount.toString(), Modifier.weight(1f))
        }
        HorizontalDivider()
        Text(Strings.STATISTICS_GENERATIONS, style = MaterialTheme.typography.titleMedium)
        BarChart(
            statistics.generationDistribution.toSortedMap()
                .map { (generation, count) -> generation.toString() to count },
        )
        HorizontalDivider()
        Text(Strings.STATISTICS_TOP_SURNAMES, style = MaterialTheme.typography.titleMedium)
        BarChart(statistics.topSurnames.map { it.name to it.count })
        HorizontalDivider()
        Text(Strings.STATISTICS_TOP_PLACES, style = MaterialTheme.typography.titleMedium)
        BarChart(statistics.topPlaces.map { it.name to it.count })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Column(
        modifier = modifier
            .background(colors.surfaceVariant, RoundedCornerShape(spacing.cornerRadius))
            .padding(spacing.medium),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )
        Text(label, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}
