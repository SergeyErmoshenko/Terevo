package me.terevo.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class TerevoColors(
    val canvas: Color,
    val sidebar: Color,
    val statusBar: Color,
    val selection: Color,
    val male: Color,
    val female: Color,
    val unknownGender: Color,
)

@Immutable
data class TerevoSpacing(
    val extraSmall: Dp,
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val sidebarWidth: Dp,
    val statusBarHeight: Dp,
)

object TerevoTypography {
    val material: Typography = Typography()
}

internal val LightColors = TerevoColors(
    canvas = Color(0xFFF6F7F9),
    sidebar = Color(0xFFFFFFFF),
    statusBar = Color(0xFFE9ECF1),
    selection = Color(0xFF3F51B5),
    male = Color(0xFF4F86C6),
    female = Color(0xFFC65A8B),
    unknownGender = Color(0xFF8A8F98),
)

private val DarkColors = TerevoColors(
    canvas = Color(0xFF16181D),
    sidebar = Color(0xFF202329),
    statusBar = Color(0xFF292D35),
    selection = Color(0xFF9FA8DA),
    male = Color(0xFF78A9DD),
    female = Color(0xFFE58CB2),
    unknownGender = Color(0xFFB2B7C0),
)

private val Spacing = TerevoSpacing(
    extraSmall = 4.dp,
    small = 8.dp,
    medium = 16.dp,
    large = 24.dp,
    sidebarWidth = 280.dp,
    statusBarHeight = 28.dp,
)

val LocalTerevoColors = staticCompositionLocalOf { LightColors }
val LocalTerevoSpacing = staticCompositionLocalOf { Spacing }

object TerevoTheme {
    val colors: TerevoColors
        @Composable get() = LocalTerevoColors.current

    val spacing: TerevoSpacing
        @Composable get() = LocalTerevoSpacing.current
}

@Composable
fun TerevoTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val materialColors: ColorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    androidx.compose.runtime.CompositionLocalProvider(
        LocalTerevoColors provides colors,
        LocalTerevoSpacing provides Spacing,
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = TerevoTypography.material,
            content = content,
        )
    }
}
