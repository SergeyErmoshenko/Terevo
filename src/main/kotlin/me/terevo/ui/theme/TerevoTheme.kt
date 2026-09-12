package me.terevo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

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

private fun interFont(weight: FontWeight, axisValue: Int) = Font(
    resource = "font/Inter-Variable.ttf",
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(axisValue)),
)

val InterFontFamily: FontFamily = FontFamily(
    interFont(FontWeight.Normal, 400),
    interFont(FontWeight.Medium, 500),
    interFont(FontWeight.SemiBold, 600),
    interFont(FontWeight.Bold, 700),
)

object TerevoTypography {
    val material: Typography = Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
            displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
            displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
            headlineLarge = base.headlineLarge.copy(fontFamily = InterFontFamily),
            headlineMedium = base.headlineMedium.copy(fontFamily = InterFontFamily),
            headlineSmall = base.headlineSmall.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.sp,
            ),
            titleLarge = base.titleLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
            ),
            titleMedium = base.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.1.sp,
            ),
            titleSmall = base.titleSmall.copy(fontFamily = InterFontFamily),
            bodyLarge = base.bodyLarge.copy(fontFamily = InterFontFamily, lineHeight = 22.sp),
            bodyMedium = base.bodyMedium.copy(fontFamily = InterFontFamily, lineHeight = 20.sp),
            bodySmall = base.bodySmall.copy(fontFamily = InterFontFamily),
            labelLarge = base.labelLarge.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.2.sp,
            ),
            labelMedium = base.labelMedium.copy(fontFamily = InterFontFamily),
            labelSmall = base.labelSmall.copy(fontFamily = InterFontFamily),
        )
    }
}

private val TerevoShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

private val SeedColor = Color(0xFF5865F2)

private data class FixedSemanticColors(
    val male: Color,
    val female: Color,
    val unknownGender: Color,
    val error: Color,
)

private val LightFixedColors = FixedSemanticColors(
    male = Color(0xFF3F6FB0),
    female = Color(0xFFB0477E),
    unknownGender = Color(0xFF6E7180),
    error = Color(0xFFB3261E),
)

private val DarkFixedColors = FixedSemanticColors(
    male = Color(0xFF7EA6E8),
    female = Color(0xFFE895BE),
    unknownGender = Color(0xFF9C97AD),
    error = Color(0xFFFFB4AB),
)

private fun terevoColorsFor(scheme: ColorScheme, dark: Boolean): TerevoColors {
    val fixed = if (dark) DarkFixedColors else LightFixedColors
    return TerevoColors(
        canvas = scheme.background,
        sidebar = scheme.surfaceContainer,
        statusBar = scheme.surfaceContainerHighest,
        selection = scheme.primary,
        accent = scheme.tertiary,
        male = fixed.male,
        female = fixed.female,
        unknownGender = fixed.unknownGender,
        surfaceVariant = scheme.surfaceVariant,
        outline = scheme.outline,
        textPrimary = scheme.onBackground,
        textSecondary = scheme.onSurfaceVariant,
        cardSurface = scheme.surface,
        error = fixed.error,
    )
}

private val LightMaterialScheme: ColorScheme =
    dynamicColorScheme(seedColor = SeedColor, isDark = false, style = PaletteStyle.TonalSpot)

private val DarkMaterialScheme: ColorScheme =
    dynamicColorScheme(seedColor = SeedColor, isDark = true, style = PaletteStyle.TonalSpot)

internal val LightColors: TerevoColors = terevoColorsFor(LightMaterialScheme, dark = false)
internal val DarkColors: TerevoColors = terevoColorsFor(DarkMaterialScheme, dark = true)

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

private fun materialColorScheme(base: ColorScheme, colors: TerevoColors, dark: Boolean): ColorScheme {
    val onError = if (dark) Color(0xFF690005) else Color(0xFFFFFFFF)
    return base.copy(
        background = colors.canvas,
        onBackground = colors.textPrimary,
        surface = colors.cardSurface,
        onSurface = colors.textPrimary,
        surfaceVariant = colors.surfaceVariant,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.outline,
        error = colors.error,
        onError = onError,
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
    val baseScheme = if (darkTheme) DarkMaterialScheme else LightMaterialScheme
    val colors = (if (darkTheme) DarkColors else LightColors).animated()
    CompositionLocalProvider(
        LocalTerevoColors provides colors,
        LocalTerevoSpacing provides Spacing,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme(baseScheme, colors, darkTheme),
            typography = TerevoTypography.material,
            shapes = TerevoShapes,
            content = content,
        )
    }
}
