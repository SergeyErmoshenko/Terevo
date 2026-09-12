package me.terevo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.terevo.ui.theme.TerevoTheme

@Composable
fun TerevoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = TerevoTheme.spacing
    val colors = TerevoTheme.colors
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = spacing.extraSmall),
        shape = RoundedCornerShape(spacing.cornerRadius),
        border = BorderStroke(1.dp, colors.outline),
        content = content,
    )
}
