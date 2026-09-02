package me.terevo.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.terevo.statistics.Statistics
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme

@Composable
fun StatisticsScreen(statistics: Statistics, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val spacing = TerevoTheme.spacing
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(Strings.STATISTICS)
        Button(onClick = onClose) { Text(Strings.STATISTICS_BACK_TO_TREE) }
        HorizontalDivider()
        Text("${Strings.STATISTICS_TOTAL_PERSONS}: ${statistics.totalPersons}")
        Text("${Strings.STATISTICS_LIVING}: ${statistics.livingCount}")
        Text("${Strings.STATISTICS_DECEASED}: ${statistics.deceasedCount}")
        Text(
            "${Strings.STATISTICS_AVERAGE_LIFESPAN}: " +
                    (
                            statistics.averageLifespanYears
                                ?.let { "%.1f ${Strings.STATISTICS_YEARS}".format(it) }
                                ?: Strings.STATISTICS_NO_LIFESPAN_DATA
                            ),
        )
        Text("${Strings.STATISTICS_MISSING_BIRTH_DATE}: ${statistics.missingBirthDateCount}")
        Text("${Strings.STATISTICS_MISSING_PARENTS}: ${statistics.missingParentsCount}")
        HorizontalDivider()
        Text(Strings.STATISTICS_GENERATIONS)
        BarChart(
            statistics.generationDistribution.toSortedMap()
                .map { (generation, count) -> generation.toString() to count },
        )
        HorizontalDivider()
        Text(Strings.STATISTICS_TOP_SURNAMES)
        BarChart(statistics.topSurnames.map { it.name to it.count })
        HorizontalDivider()
        Text(Strings.STATISTICS_TOP_PLACES)
        BarChart(statistics.topPlaces.map { it.name to it.count })
    }
}
