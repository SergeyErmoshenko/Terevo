package me.terevo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class TerevoColors(
    val canvas: Color,
    val sidebar: Color,
    val statusBar: Color,
    val selection: Color,
    val accent: Color,
    val male: Color,
    val female: Color,
    val unknownGender: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val cardSurface: Color,
    val error: Color,
)

@Immutable
data class TerevoSpacing(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val sidebarWidth: Dp,
    val statusBarHeight: Dp,
    val cornerRadius: Dp,
)

object TerevoTypography {
    val material: Typography = Typography().let { base ->
        base.copy(
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, lineHeight = 28.sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
            bodyLarge = base.bodyLarge.copy(lineHeight = 22.sp),
            bodyMedium = base.bodyMedium.copy(lineHeight = 20.sp),
            labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
        )
    }
}

internal val LightColors = TerevoColors(
    canvas = Color(0xFFF6F5FA),
    sidebar = Color(0xFFFAF9FE),
    statusBar = Color(0xFFEDEBF5),
    selection = Color(0xFF5B3FD1),
    accent = Color(0xFF0F766E),
    male = Color(0xFF3F6FB0),
    female = Color(0xFFB0477E),
    unknownGender = Color(0xFF6E7180),
    surfaceVariant = Color(0xFFEFEDF7),
    outline = Color(0xFFDDD9EC),
    textPrimary = Color(0xFF1B1726),
    textSecondary = Color(0xFF5C5770),
    cardSurface = Color(0xFFFFFFFF),
    error = Color(0xFFB3261E),
)

internal val DarkColors = TerevoColors(
    canvas = Color(0xFF17151F),
    sidebar = Color(0xFF1E1B29),
    statusBar = Color(0xFF120F18),
    selection = Color(0xFFB4A7F5),
    accent = Color(0xFF5EEAD4),
    male = Color(0xFF7EA6E8),
    female = Color(0xFFE895BE),
    unknownGender = Color(0xFF9C97AD),
    surfaceVariant = Color(0xFF262233),
    outline = Color(0xFF3A3450),
    textPrimary = Color(0xFFF1EFF7),
    textSecondary = Color(0xFFABA6BD),
    cardSurface = Color(0xFF241F30),
    error = Color(0xFFFFB4AB),
)

private val Spacing = TerevoSpacing(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 16.dp,
    large = 24.dp,
    sidebarWidth = 380.dp,
    statusBarHeight = 28.dp,
    cornerRadius = 8.dp,
)

val LocalTerevoColors = staticCompositionLocalOf { LightColors }
val LocalTerevoSpacing = staticCompositionLocalOf { Spacing }

object TerevoTheme {
    val colors: TerevoColors
        @Composable get() = LocalTerevoColors.current

    val spacing: TerevoSpacing
        @Composable get() = LocalTerevoSpacing.current
}

private fun materialColorScheme(colors: TerevoColors, dark: Boolean): ColorScheme {
    val onColor = if (dark) Color(0xFF17151F) else Color(0xFFFFFFFF)
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
    return scheme.copy(
        primary = colors.selection,
        onPrimary = onColor,
        secondary = colors.accent,
        onSecondary = onColor,
        background = colors.canvas,
        onBackground = colors.textPrimary,
        surface = colors.sidebar,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        error = colors.error,
        onError = onColor,
        outline = colors.outline,
    )
}

private const val THEME_TRANSITION_MS: Int = 220

@Composable
private fun TerevoColors.animated(): TerevoColors {
    val tween = tween<Color>(THEME_TRANSITION_MS)
    return TerevoColors(
        canvas = animateColorAsState(canvas, tween).value,
        sidebar = animateColorAsState(sidebar, tween).value,
        statusBar = animateColorAsState(statusBar, tween).value,
        selection = animateColorAsState(selection, tween).value,
        accent = animateColorAsState(accent, tween).value,
        male = animateColorAsState(male, tween).value,
        female = animateColorAsState(female, tween).value,
        unknownGender = animateColorAsState(unknownGender, tween).value,
        surfaceVariant = animateColorAsState(surfaceVariant, tween).value,
        outline = animateColorAsState(outline, tween).value,
        textPrimary = animateColorAsState(textPrimary, tween).value,
        textSecondary = animateColorAsState(textSecondary, tween).value,
        cardSurface = animateColorAsState(cardSurface, tween).value,
        error = animateColorAsState(error, tween).value,
    )
}

@Composable
fun TerevoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = (if (darkTheme) DarkColors else LightColors).animated()
    CompositionLocalProvider(
        LocalTerevoColors provides colors,
        LocalTerevoSpacing provides Spacing,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme(colors, darkTheme),
            typography = TerevoTypography.material,
            content = content,
        )
    }
}
