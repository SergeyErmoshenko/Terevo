package me.terevo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.terevo.ui.theme.TerevoTheme

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TerevoTheme.colors
    val spacing = TerevoTheme.spacing
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(spacing.cornerRadius),
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline),
    ) {
        Row {
            options.forEach { option ->
                val isSelected = option == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .clickable(onClick = { onSelect(option) })
                        .background(if (isSelected) colors.selection else Color.Transparent)
                        .padding(vertical = spacing.small, horizontal = spacing.extraSmall / 2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.cardSurface else colors.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
