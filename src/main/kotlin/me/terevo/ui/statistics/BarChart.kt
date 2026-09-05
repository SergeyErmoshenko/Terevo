package me.terevo.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.terevo.ui.Strings
import me.terevo.ui.theme.TerevoTheme

@Composable
fun BarChart(entries: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val colors = TerevoTheme.colors
    if (entries.isEmpty()) {
        Text(Strings.NO_DATA, color = colors.textSecondary, modifier = modifier)
        return
    }
    val textMeasurer = rememberTextMeasurer()
    val maxCount = entries.maxOf { it.second }.coerceAtLeast(1)
    val rowHeight = ROW_HEIGHT_DP.dp

    Canvas(modifier.fillMaxWidth().height(rowHeight * entries.size)) {
        val labelWidth = size.width * LABEL_WIDTH_FRACTION
        val barAreaWidth = size.width - labelWidth - COUNT_WIDTH_PX
        val rowHeightPx = size.height / entries.size

        entries.forEachIndexed { index, (label, count) ->
            val top = index * rowHeightPx
            val barHeight = rowHeightPx * BAR_HEIGHT_FRACTION
            val barTop = top + (rowHeightPx - barHeight) / 2f
            val barWidth = (count.toFloat() / maxCount) * barAreaWidth

            val labelLayout =
                textMeasurer.measure(label, TextStyle(color = colors.textPrimary, fontSize = LABEL_SIZE.sp))
            drawText(
                labelLayout,
                topLeft = Offset(0f, top + (rowHeightPx - labelLayout.size.height) / 2f),
            )
            drawRect(
                color = colors.selection,
                topLeft = Offset(labelWidth, barTop),
                size = Size(barWidth.coerceAtLeast(1f), barHeight),
            )
            val countLayout =
                textMeasurer.measure(
                    count.toString(),
                    TextStyle(color = colors.textSecondary, fontSize = LABEL_SIZE.sp)
                )
            drawText(
                countLayout,
                topLeft = Offset(
                    labelWidth + barWidth + BAR_LABEL_GAP,
                    top + (rowHeightPx - countLayout.size.height) / 2f
                ),
            )
        }
    }
}

private const val ROW_HEIGHT_DP: Int = 28
private const val LABEL_WIDTH_FRACTION: Float = 0.3f
private const val COUNT_WIDTH_PX: Float = 48f
private const val BAR_HEIGHT_FRACTION: Float = 0.6f
private const val BAR_LABEL_GAP: Float = 8f
private const val LABEL_SIZE: Int = 13
